package net.minestom.server.entity.old.ai;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.old.ai.controller.MobControllers;
import net.minestom.server.entity.old.ai.debug.AiSnapshot;
import net.minestom.server.entity.old.ai.goal.ApproachFoodGoal;
import net.minestom.server.entity.old.ai.goal.CombatGoal;
import net.minestom.server.entity.old.ai.goal.EscapeDangerGoal;
import net.minestom.server.entity.old.ai.goal.FollowTargetGoal;
import net.minestom.server.entity.old.ai.goal.IdleGoal;
import net.minestom.server.entity.old.ai.goal.LookAtPlayerGoal;
import net.minestom.server.entity.old.ai.goal.ReactToDamageGoal;
import net.minestom.server.entity.old.ai.goal.RestGoal;
import net.minestom.server.entity.old.ai.goal.SocializeGoal;
import net.minestom.server.entity.old.ai.goal.WanderGoal;
import net.minestom.server.entity.old.ai.memory.Blackboard;
import net.minestom.server.entity.old.ai.memory.Memories;
import net.minestom.server.entity.old.ai.memory.MemoryKey;
import net.minestom.server.entity.old.ai.navigation.NavigationController;
import net.minestom.server.entity.old.ai.navigation.PathRequestBudget;
import net.minestom.server.entity.old.ai.sensor.EnvironmentSensor;
import net.minestom.server.entity.old.ai.sensor.NearbyEntitySensor;
import net.minestom.server.entity.old.ai.sensor.Sensor;
import net.minestom.server.entity.old.ai.sensor.Terrain;
import net.minestom.server.entity.old.ai.sensor.TerrainSensor;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.instance.Instance;
import net.minestom.server.pathfinding.Pathfinder;
import net.minestom.server.pathfinding.options.PathfinderOptions;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Sensor → memory → scheduler → active goals → controllers pipeline for a ground mob.
 * All mutation, event hooks, and debug capture must run on the mob's owning tick thread.
 * Asynchronous pathfinding never mutates this brain or its entity.
 */
public final class MobBrain {
    private static final class ScheduledSensor {
        final Sensor sensor;
        long nextTick;

        ScheduledSensor(Sensor sensor, long nextTick) {
            this.sensor = sensor;
            this.nextTick = nextTick;
        }
    }

    private final EntityMob mob;
    private final AiSettings settings;
    private final MobAiPolicy policy;
    private final Blackboard memory = new Blackboard();
    private final GoalScheduler scheduler;
    private final MobControllers controllers;
    private final Terrain terrain = new Terrain();
    private final List<ScheduledSensor> sensors = new ArrayList<>();
    private final Random random;
    private final int phase;
    private @Nullable Instance instance;
    private SimulationLevel simulationLevel = SimulationLevel.FULL;
    private boolean automaticSimulation;
    private boolean urgent;
    private long tick;
    private long nextSimulationCheck;
    private int stepTicks = 1;
    private Vec previousPosition;

    public MobBrain(EntityMob mob, AiSettings settings, MobAiPolicy policy) {
        this(mob, settings, policy, new Pathfinder(new PathfinderOptions.Builder().async(true)
                .maxIterations(4096).maxLength(64).autoLoadChunks(false).build()), PathRequestBudget.shared());
    }

    /**
     * Share the same budget across brains to bound aggregate pathfinding work.
     */
    public MobBrain(EntityMob mob, AiSettings settings, MobAiPolicy policy,
                    Pathfinder pathfinder, PathRequestBudget budget) {
        this.mob = Objects.requireNonNull(mob);
        this.settings = Objects.requireNonNull(settings);
        this.policy = Objects.requireNonNull(policy);
        if (!Double.isFinite(policy.sensingRange()) || policy.sensingRange() <= 0
                || !Double.isFinite(policy.explorationRange()) || policy.explorationRange() <= 0)
            throw new IllegalArgumentException("AI sensing and exploration ranges must be positive and finite");
        this.phase = mob.getUuid().hashCode() & Integer.MAX_VALUE;
        this.random = new Random(mob.getUuid().getMostSignificantBits() ^ mob.getUuid().getLeastSignificantBits());
        this.automaticSimulation = settings.simulation().automatic();
        this.tick = mob.getAliveTicks();
        this.instance = mob.getInstance();
        this.previousPosition = mob.getPosition().asVec();
        this.scheduler = new GoalScheduler(this);
        this.controllers = new MobControllers(this, Objects.requireNonNull(pathfinder), Objects.requireNonNull(budget));
        memory.advance(tick);
        memory.set(Memories.NEEDS, new Memories.Needs(0.2, 0.1, 0.3, 0.2, 0));
        addSensor(new EnvironmentSensor());
        addSensor(new TerrainSensor());
        addSensor(new NearbyEntitySensor());
        scheduler.add(new EscapeDangerGoal());
        scheduler.add(new ReactToDamageGoal());
        scheduler.add(new CombatGoal());
        scheduler.add(new WanderGoal());
        scheduler.add(new IdleGoal());
        scheduler.add(new FollowTargetGoal());
        scheduler.add(new ApproachFoodGoal());
        scheduler.add(new SocializeGoal());
        scheduler.add(new RestGoal());
        scheduler.add(new LookAtPlayerGoal());
    }

    public EntityMob mob() {
        return mob;
    }

    public AiSettings settings() {
        return settings;
    }

    public MobAiPolicy policy() {
        return policy;
    }

    public Blackboard memory() {
        return memory;
    }

    public GoalScheduler scheduler() {
        return scheduler;
    }

    public MobControllers controllers() {
        return controllers;
    }

    public NavigationController navigation() {
        return controllers.navigation();
    }

    public Terrain terrain() {
        return terrain;
    }

    public Random random() {
        return random;
    }

    public long tick() {
        return tick;
    }

    public int stepTicks() {
        return stepTicks;
    }

    public SimulationLevel simulationLevel() {
        return simulationLevel;
    }

    public Memories.Needs needs() {
        return Objects.requireNonNull(memory.get(Memories.NEEDS));
    }

    public void addSensor(Sensor sensor) {
        int interval = sensor.interval(this);
        if (interval < 1) throw new IllegalArgumentException("Sensor interval must be positive");
        sensors.add(new ScheduledSensor(sensor, tick + Math.floorMod(phase + sensors.size(), interval)));
    }

    public void tickBrain() {
        tick = mob.getAliveTicks();
        if (mob.getInstance() != instance) {
            reset(TerminationReason.INSTANCE_CHANGED);
            if (instance != null) {
                memory.invalidate(MemoryKey.Kind.REMEMBERED);
                memory.forget(Memories.FOLLOW_TARGET);
                memory.forget(Memories.COMBAT_TARGET);
            }
            navigation().conditionsChanged();
            instance = mob.getInstance();
            nextSimulationCheck = tick;
        }
        if (mob.isDead() || mob.isRemoved() || instance == null || !ChunkUtils.isLoaded(mob.getChunk())) {
            reset(TerminationReason.REMOVED);
            return;
        }
        if (automaticSimulation && tick >= nextSimulationCheck) {
            double[] nearest = {Double.POSITIVE_INFINITY};
            instance.getEntityTracker().nearbyEntities(mob.getPosition(), settings.simulation().essentialsExit(),
                    EntityTracker.Target.PLAYERS, player -> {
                        if (!player.isRemoved() && player.getInstance() == instance)
                            nearest[0] = Math.min(nearest[0], mob.getDistanceSquared(player));
                    });
            transitionSimulation(settings.simulation().level(simulationLevel, Math.sqrt(nearest[0])));
            int interval = settings.simulation().checkInterval();
            nextSimulationCheck = tick + interval - Math.floorMod(tick + phase, interval);
        }
        // Activation-ring checks are the only AI work performed while inactive. Entity physics/events still run.
        if (simulationLevel == SimulationLevel.INACTIVE) return;
        stepTicks = simulationLevel == SimulationLevel.FULL ? 1 : settings.scheduling().essentialsInterval();
        if (!urgent && Math.floorMod(tick + phase, stepTicks) != 0) return;
        urgent = false;
        memory.advance(tick);
        validateTargets();
        for (ScheduledSensor scheduled : sensors) {
            if (simulationLevel != SimulationLevel.FULL && !scheduled.sensor.essential()) continue;
            if (tick < scheduled.nextTick) continue;
            scheduled.sensor.sense(this);
            scheduled.nextTick = tick + (long) scheduled.sensor.interval(this) * stepTicks;
        }
        if (simulationLevel == SimulationLevel.FULL) updateNeeds();
        controllers.beginTick();
        scheduler.tick(Math.floorMod(tick + phase, settings.scheduling().selectionInterval()) == 0);
        controllers.tick();
    }

    public void setSimulationLevel(SimulationLevel level) {
        automaticSimulation = false;
        transitionSimulation(Objects.requireNonNull(level));
    }

    public void useAutomaticSimulation() {
        automaticSimulation = true;
        nextSimulationCheck = tick;
    }

    private void transitionSimulation(SimulationLevel next) {
        if (next == simulationLevel) return;
        reset(TerminationReason.SIMULATION_CHANGED);
        simulationLevel = next;
        memory.advance(mob.getAliveTicks());
        previousPosition = mob.getPosition().asVec();
        urgent = true;
    }

    public void reset(TerminationReason reason) {
        scheduler.stopAll(reason);
        controllers.clear();
        memory.invalidate(MemoryKey.Kind.OBSERVATION);
        scheduler.conditionsChanged();
        for (ScheduledSensor scheduled : sensors) {
            int interval = scheduled.sensor.interval(this);
            scheduled.nextTick = tick + (scheduled.sensor instanceof EnvironmentSensor ? 0 : phase % interval);
        }
    }

    /**
     * Called only after damage was accepted, including during inactive entity simulation.
     */
    public void onDamage(Damage damage) {
        tick = mob.getAliveTicks();
        memory.advance(tick);
        Entity attacker = damage.getAttacker();
        UUID attackerId = attacker != null && attacker.getInstance() == mob.getInstance() ? attacker.getUuid() : null;
        Vec source = attackerId != null ? attacker.getPosition().asVec()
                : damage.getSourcePosition() == null ? mob.getPosition().asVec() : damage.getSourcePosition().asVec();
        memory.remember(Memories.THREAT, new Memories.Threat(attackerId, source, tick),
                settings.scheduling().memoryLifetime());
        scheduler.conditionsChanged();
        urgent = true;
    }

    /**
     * Invalidates observations and failed destinations when relevant terrain or game rules change.
     */
    public void conditionsChanged() {
        memory.invalidate(MemoryKey.Kind.OBSERVATION);
        navigation().conditionsChanged();
        scheduler.conditionsChanged();
        for (ScheduledSensor scheduled : sensors) scheduled.nextTick = tick;
        urgent = true;
    }

    public @Nullable Entity resolve(@Nullable UUID id) {
        if (id == null || mob.getInstance() == null) return null;
        Entity entity = mob.getInstance().getEntityByUuid(id);
        return entity == null || entity.isRemoved() || entity.getInstance() != mob.getInstance()
                || entity instanceof LivingEntity living && living.isDead() ? null : entity;
    }

    public void invalidateEntity(UUID id) {
        if (id.equals(memory.get(Memories.FOLLOW_TARGET))) memory.forget(Memories.FOLLOW_TARGET);
        if (id.equals(memory.get(Memories.COMBAT_TARGET))) memory.forget(Memories.COMBAT_TARGET);
        var food = memory.get(Memories.FOOD);
        if (food != null && food.id().equals(id)) memory.forget(Memories.FOOD);
        var threat = memory.get(Memories.THREAT);
        if (threat != null && id.equals(threat.attacker())) memory.forget(Memories.THREAT);
        var perception = memory.get(Memories.VISIBLE);
        if (perception != null) memory.remember(Memories.VISIBLE,
                new Memories.Perception(perception.entities().stream().filter(entity -> !entity.id().equals(id)).toList()),
                settings.scheduling().observationLifetime());
        var recent = memory.get(Memories.RECENT_ENTITIES);
        if (recent != null) memory.remember(Memories.RECENT_ENTITIES,
                new Memories.Perception(recent.entities().stream().filter(entity -> !entity.id().equals(id)).toList()),
                settings.scheduling().memoryLifetime());
        scheduler.conditionsChanged();
    }

    private void validateTargets() {
        UUID follow = memory.get(Memories.FOLLOW_TARGET);
        if (follow != null && resolve(follow) == null) memory.forget(Memories.FOLLOW_TARGET);
        UUID combat = memory.get(Memories.COMBAT_TARGET);
        if (combat != null && resolve(combat) == null) memory.forget(Memories.COMBAT_TARGET);
    }

    public List<Memories.SeenEntity> visible() {
        var visible = memory.get(Memories.VISIBLE);
        return visible == null ? List.of() : visible.entities();
    }

    public @Nullable Memories.SeenEntity observed(@Nullable UUID entity) {
        return entity == null ? null : visible().stream().filter(seen -> seen.id().equals(entity)).findFirst().orElse(null);
    }

    public @Nullable Memories.SeenEntity remembered(@Nullable UUID entity) {
        Memories.Perception recent = memory.get(Memories.RECENT_ENTITIES);
        return entity == null || recent == null ? null : recent.entities().stream()
                .filter(seen -> seen.id().equals(entity)
                        && tick - seen.observedAt() < settings.scheduling().memoryLifetime()).findFirst().orElse(null);
    }

    public List<Vec> safePositions() {
        var locations = memory.get(Memories.SAFE_POSITIONS);
        return locations == null ? List.of() : locations.positions().stream().filter(navigation()::available).toList();
    }

    public boolean inDanger() {
        var danger = memory.get(Memories.DANGER);
        return danger != null && danger.present();
    }

    public boolean safeHere() {
        return !inDanger() && terrain.safe(mob, mob.getPosition());
    }

    public boolean night() {
        long time = mob.getInstance() == null ? 0 : Math.floorMod(mob.getInstance().getTime(), 24000);
        return time >= 13000 && time < 23000;
    }

    private double unit(double value) {
        return Math.clamp(value, 0, 1);
    }

    private void updateNeeds() {
        boolean moving = mob.getPosition().distanceSquared(previousPosition) > 0.0025;
        previousPosition = mob.getPosition().asVec();
        var needs = needs();
        double activity = needs.recentActivity() + ((moving ? 1 : 0) - needs.recentActivity()) / 100;
        memory.set(Memories.NEEDS, new Memories.Needs(unit(needs.hunger() + 1.0 / 12000),
                unit(needs.fatigue() + (moving ? 1.0 / 2400 : -1.0 / 6000)),
                unit(needs.boredom() + (moving ? -1.0 / 200 : 1.0 / 400)),
                unit(needs.isolation() + 1.0 / 1200), activity));
    }

    public void feed(double amount) {
        var n = needs();
        memory.set(Memories.NEEDS, new Memories.Needs(unit(n.hunger() - amount), n.fatigue(), n.boredom(), n.isolation(), n.recentActivity()));
    }

    public void recover(double amount) {
        var n = needs();
        memory.set(Memories.NEEDS, new Memories.Needs(n.hunger(), unit(n.fatigue() - amount), n.boredom(), n.isolation(), n.recentActivity()));
    }

    public void socialize(double amount) {
        var n = needs();
        memory.set(Memories.NEEDS, new Memories.Needs(n.hunger(), n.fatigue(), n.boredom(), unit(n.isolation() - amount), n.recentActivity()));
    }

    public void explored(Vec position) {
        memory.set(Memories.LAST_EXPLORED, tick);
        var visited = memory.get(Memories.VISITED);
        var positions = new ArrayList<>(visited == null ? List.<Vec>of() : visited.positions());
        positions.add(position);
        if (positions.size() > 16) positions.removeFirst();
        memory.remember(Memories.VISITED, new Memories.Locations(positions), settings.scheduling().memoryLifetime());
    }

    public AiSnapshot snapshot() {
        tick = mob.getAliveTicks();
        memory.advance(tick);
        return new AiSnapshot(mob.getUuid(), tick, simulationLevel, scheduler.snapshot(), scheduler.controls(),
                memory.snapshot(), navigation().snapshot(), scheduler.transitions());
    }
}
