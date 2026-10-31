package net.minestom.server.entity.old.ai.navigation;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.old.ai.Control;
import net.minestom.server.entity.old.ai.MobBrain;
import net.minestom.server.entity.old.ai.goal.Goal;
import net.minestom.server.instance.Instance;
import net.minestom.server.pathfinding.Pathfinder;
import net.minestom.server.pathfinding.context.MobContext;
import net.minestom.server.pathfinding.data.Path;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Goals request destinations; this service owns admission, asynchronous results, and waypoint following.
 */
public final class NavigationController {
    public enum Status {IDLE, QUEUED, PENDING, FOLLOWING, ARRIVED, FAILED}

    public record Failure(Vec destination, long expiresAt, String reason) {
    }

    public record Snapshot(Status status, @Nullable String owner, @Nullable Vec destination,
                           int waypoint, int waypointCount, int repaths, List<Failure> failures) {
    }

    private record Result(@Nullable Path path, @Nullable Throwable error) {
    }

    private final MobBrain brain;
    private final Pathfinder pathfinder;
    private final PathRequestBudget budget;
    private final Map<Vec, Failure> failures = new LinkedHashMap<>();
    private @Nullable Goal owner;
    private @Nullable Instance requestInstance;
    private @Nullable Vec destination;
    private @Nullable Vec requestedDestination;
    private @Nullable CompletableFuture<Result> pending;
    private AtomicBoolean cancellation = new AtomicBoolean();
    private List<Point> points = List.of();
    private Status status = Status.IDLE;
    private int index;
    private int range;
    private int repaths;
    private long requestedAt;
    private long progressAt;
    private Vec progressPosition = Vec.ZERO;

    public NavigationController(MobBrain brain, Pathfinder pathfinder, PathRequestBudget budget) {
        this.brain = brain;
        this.pathfinder = pathfinder;
        this.budget = budget;
    }

    public Status status() {
        return status;
    }

    public boolean available(Point point) {
        Failure failure = failures.get(blockKey(point));
        return failure == null || failure.expiresAt <= brain.tick();
    }

    public void request(Goal goal, Point target, int completionRange) {
        brain.scheduler().require(goal, Control.MOVE);
        brain.scheduler().require(goal, Control.JUMP);
        if (completionRange < 0) throw new IllegalArgumentException("Negative completion range");
        Vec next = target.asVec();
        if (owner != goal) {
            clear();
            owner = goal;
            destination = next;
            range = completionRange;
            repaths = 0;
            status = Status.QUEUED;
        } else {
            destination = next;
            range = completionRange;
            // A pending request is retained until it finishes or times out, even if the target moves.
            boolean leftArrivalRange = status == Status.ARRIVED && brain.mob().getPosition().distance(next)
                    > Math.max(range, brain.settings().navigation().waypointRange());
            if (leftArrivalRange || (status == Status.ARRIVED || status == Status.FAILED)
                    && requestedDestination != null
                    && requestedDestination.distanceSquared(next) >= movementThresholdSquared()) {
                repaths = 0;
                queue();
            }
        }
    }

    public void tick() {
        failures.values().removeIf(failure -> failure.expiresAt <= brain.tick());
        if (owner == null || destination == null) return;
        var mob = brain.mob();
        var instance = mob.getInstance();
        if (instance == null || (requestInstance != null && requestInstance != instance)) {
            clear();
            return;
        }
        if (status == Status.QUEUED) {
            double arrivalRange = Math.max(range, brain.settings().navigation().waypointRange());
            if (mob.getPosition().distanceSquared(destination) <= arrivalRange * arrivalRange) {
                cancelRequest();
                requestedDestination = destination;
                status = Status.ARRIVED;
                brain.controllers().stopMovement(owner);
                return;
            }
            if (!available(destination)) {
                fail("destination cooling down");
                return;
            }
            if (!instance.getWorldBorder().inBounds(destination)
                    || !ChunkUtils.isLoaded(instance.getChunkAt(destination))) {
                fail("destination outside loaded world");
                return;
            }
            requestInstance = instance;
            if (!budget.acquire(instance, mob.getUuid())) return;
            requestedDestination = destination;
            requestedAt = brain.tick();
            cancellation = new AtomicBoolean();
            var context = new MobContext(instance, mob.getBoundingBox(), mob.getPosition(),
                    mob.getAttributeValue(Attribute.STEP_HEIGHT), mob.getAttributeValue(Attribute.SAFE_FALL_DISTANCE));
            status = Status.PENDING;
            try {
                pending = pathfinder.findPath(mob.getPosition(), requestedDestination, context, range, cancellation)
                        .handle((path, error) -> {
                            budget.complete(instance);
                            return new Result(path, error);
                        });
            } catch (RuntimeException exception) {
                budget.complete(instance);
                fail("path request error: " + exception);
                throw exception;
            }
        }
        if (status == Status.PENDING && pending != null) {
            if (!pending.isDone()) {
                if (brain.tick() - requestedAt >= brain.settings().navigation().requestTimeout())
                    fail("path request timed out");
                return;
            }
            Result result = pending.join();
            pending = null;
            if (result.error != null) {
                fail("pathfinding error: " + result.error);
                return;
            }
            Path path = result.path;
            if (path == null || path.state() == Path.State.FAILED || path.points().isEmpty()) {
                fail("no path");
                return;
            }
            points = List.copyOf(path.points());
            index = 0;
            progressPosition = mob.getPosition().asVec();
            progressAt = brain.tick();
            status = Status.FOLLOWING;
        }
        if (status != Status.FOLLOWING) return;
        if (requestedDestination != null
                && requestedDestination.distanceSquared(destination) >= movementThresholdSquared()
                && brain.tick() - requestedAt >= brain.settings().navigation().repathInterval()) {
            queue();
            return;
        }
        double arrivalRange = Math.max(range, brain.settings().navigation().waypointRange());
        if (mob.getPosition().distanceSquared(destination) <= arrivalRange * arrivalRange) {
            status = Status.ARRIVED;
            brain.controllers().stopMovement(owner);
            return;
        }
        double waypointRange = Math.max(brain.settings().navigation().waypointRange(),
                mob.getAttributeValue(Attribute.MOVEMENT_SPEED));
        while (index < points.size() && mob.getPosition().distanceSquared(points.get(index))
                <= waypointRange * waypointRange) index++;
        if (index == points.size()) {
            if (requestedDestination != null && !requestedDestination.equals(destination)) {
                queue();
                return;
            }
            // Best-effort paths and a pathfinder's start-only result are not destination success.
            fail("path ended before reaching destination");
            return;
        }
        Point waypoint = points.get(index);
        if (!ChunkUtils.isLoaded(instance.getChunkAt(waypoint))) {
            fail("waypoint chunk unloaded");
            return;
        }
        // A higher waypoint can be above a step. Only invalidate occupied space, not the swept jump arc.
        if (!brain.inDanger() && brain.tick() % brain.settings().navigation().repathInterval() == 0
                && !brain.terrain().canOccupy(mob, waypoint)) {
            repath("terrain changed");
            return;
        }
        double progress = brain.settings().navigation().progressDistance();
        if (mob.getPosition().distanceSquared(progressPosition) >= progress * progress) {
            progressPosition = mob.getPosition().asVec();
            progressAt = brain.tick();
        } else if (brain.tick() - progressAt >= brain.settings().navigation().stuckTicks()) {
            repath("stuck");
            return;
        }
        brain.controllers().move(owner, waypoint, 1);
        if (waypoint.y() - mob.getPosition().y() > mob.getAttributeValue(Attribute.STEP_HEIGHT))
            brain.controllers().jump(owner);
    }

    private double movementThresholdSquared() {
        double distance = brain.settings().navigation().targetMovement();
        return distance * distance;
    }

    private Vec blockKey(Point point) {
        return new Vec(point.blockX(), point.blockY(), point.blockZ());
    }

    private void repath(String reason) {
        if (repaths++ >= brain.settings().navigation().maxRepaths()) fail(reason);
        else queue();
    }

    private void queue() {
        cancelRequest();
        points = List.of();
        index = 0;
        status = Status.QUEUED;
        if (owner != null) brain.controllers().stopMovement(owner);
    }

    private void fail(String reason) {
        cancelRequest();
        status = Status.FAILED;
        points = List.of();
        if (destination != null) {
            Vec key = blockKey(destination);
            failures.remove(key);
            failures.put(key, new Failure(destination,
                    brain.tick() + brain.settings().scheduling().failureCooldown(), reason));
            if (failures.size() > brain.settings().navigation().failureHistorySize())
                failures.remove(failures.keySet().iterator().next());
        }
        if (owner != null) brain.controllers().stopMovement(owner);
    }

    private void cancelRequest() {
        cancellation.set(true);
        // Do not cancel the wrapper future: the worker must release its in-flight permit when it actually exits.
        pending = null;
        if (requestInstance != null) budget.cancel(requestInstance, brain.mob().getUuid());
    }

    public void release(Goal goal) {
        if (owner == goal) clear();
    }

    public void clear() {
        cancelRequest();
        if (owner != null) brain.controllers().stopMovement(owner);
        owner = null;
        requestInstance = null;
        destination = null;
        requestedDestination = null;
        points = List.of();
        index = 0;
        status = Status.IDLE;
    }

    /**
     * Call when a relevant block, door, destination, or world condition changes.
     */
    public void conditionsChanged() {
        failures.clear();
        if (owner != null) {
            repaths = 0;
            queue();
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(status, owner == null ? null : owner.name(), destination, index, points.size(),
                repaths, List.copyOf(failures.values()));
    }
}
