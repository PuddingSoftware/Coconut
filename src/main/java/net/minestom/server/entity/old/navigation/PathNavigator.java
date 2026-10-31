package net.minestom.server.entity.old.navigation;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.old.navigation.data.NavigationDataSnapshot;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.pathfinding.Pathfinder;
import net.minestom.server.pathfinding.context.MobContext;
import net.minestom.server.pathfinding.data.Path;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class PathNavigator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathNavigator.class);

    protected final EntityMob entityMob;

    private final Pathfinder pathfinder;

    @Nullable
    protected Path currentPath;
    protected List<Point> points = List.of();
    protected int currentIndex = 0;

    // set up the default options for pathfinding completion (or best effort completion)
    private int completionRange = 1;
    private Runnable completionCallback = () -> {
    };
    private Runnable bestEffortCompletionCallback = () -> {
    };

    // allows users to cancel pathfinding whenever they want
    private final AtomicBoolean cancelPathfindingFlag;

    public PathNavigator(@NotNull EntityMob entityMob,
                         @NotNull Pathfinder pathfinder) {
        this.entityMob = entityMob;
        this.pathfinder = pathfinder;
        this.currentPath = null;
        this.cancelPathfindingFlag = new AtomicBoolean();
    }

    public PathNavigator(@NotNull EntityMob entityMob) {
        this(entityMob, Pathfinder.DEFAULT_PATHFINDER);
    }

    protected abstract void navigatePath();

    @ApiStatus.Internal
    public synchronized void tick() {
        if (entityMob.isDead())
            return;

        // don't perform any navigation if the path is null or if it failed to find a path
        if (currentPath == null || currentPath.state() == Path.State.FAILED)
            return;

        // check for completion, whether it be to the end point or the best effort point
        if (currentPath.state() == Path.State.FOUND) {
            // check if the mob is within the completion distance of the path's end point
            if (entityMob.getPosition().octileDistance(currentPath.end()) <= completionRange) {
                completionCallback.run();
                clearPath();
                return;
            }
        } else {
            // check if the mob is within the completion distance of the path's best effort point
            final Point bestEffortPoint = currentPath.points().getLast();
            if (entityMob.getPosition().octileDistance(bestEffortPoint) <= completionRange) {
                bestEffortCompletionCallback.run();
                clearPath();
                return;
            }
        }

        // make the mob actually move around the terrain
        navigatePath();
    }

    public CompletableFuture<Path> findPath(Point target,
                                            int completionRange,
                                            Runnable completionCallback,
                                            Runnable bestEffortCompletionCallback) {
        final Instance instance = entityMob.getInstance();
        final Point position = entityMob.getPosition();

        // can't pathfind outside the world border
        final WorldBorder worldBorder = instance.getWorldBorder();
        if (!worldBorder.inBounds(target)) {
            return CompletableFuture.completedFuture(new Path(Path.State.FAILED, Collections.emptyList(), position, target));
        }

        // check if we can pathfind in unloaded chunks
        final Chunk chunk = instance.getChunkAt(target);
        if (!pathfinder.getOptions().isAutoLoadChunks() && !ChunkUtils.isLoaded(chunk)) {
            return CompletableFuture.completedFuture(new Path(Path.State.FAILED, Collections.emptyList(), position, target));
        }

        cancelPathfindingFlag.set(false);
        final CompletableFuture<Path> futurePath = pathfinder.findPath(
                position,
                target,
                new MobContext(
                        instance,
                        entityMob.getBoundingBox(),
                        position,
                        entityMob.getAttributeValue(Attribute.STEP_HEIGHT),
                        entityMob.getAttributeValue(Attribute.SAFE_FALL_DISTANCE)
                ),
                completionRange,
                cancelPathfindingFlag
        );

        return futurePath.whenComplete((path, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("Failed to find path for entity {}", entityMob.getUuid(), throwable);
                return;
            }

            // don't include a node that is too close to the start
            // this prevents doubling back to nodes during navigation, which causes issue when the entity starts on a slab, staircase, etc...
            if (path.points().getFirst().sameBlock(position)) {
                path.points().removeFirst();
            }

            synchronized (this) {
                this.currentIndex = 0;
                this.points = path.points();
                this.completionRange = completionRange;
                this.completionCallback = completionCallback;
                this.bestEffortCompletionCallback = bestEffortCompletionCallback;
                this.currentPath = path;
            }
        });

//        return futurePath;
    }

    // sets the path from a navigation data snapshot
    public synchronized void setPath(@NotNull NavigationDataSnapshot dataSnapshot) {
        this.cancelPathfindingFlag.set(false);
        this.currentIndex = dataSnapshot.getIndex();
        this.points = dataSnapshot.getPoints();
        this.completionRange = dataSnapshot.getCompletionRange();
        this.completionCallback = dataSnapshot.getCompletionCallback();
        this.bestEffortCompletionCallback = dataSnapshot.getBestEffortCompletionCallback();
        this.currentPath = dataSnapshot.getPath();
    }

    /**
     * Clears the mob's current path which forces it to stop navigating the path.
     */
    public synchronized void clearPath() {
        this.cancelPathfindingFlag.set(true);
        this.currentPath = null;
        this.currentIndex = 0;
        this.points = List.of();
    }

    @NotNull
    public synchronized NavigationDataSnapshot getNavigationDataSnapshot() {
        return new NavigationDataSnapshot(
                currentPath,
                points,
                currentIndex,
                completionRange,
                completionCallback,
                bestEffortCompletionCallback
        );
    }

    public enum Type {

        GROUND
    }
}