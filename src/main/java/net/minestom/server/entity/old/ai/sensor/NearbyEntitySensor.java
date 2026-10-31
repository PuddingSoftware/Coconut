package net.minestom.server.entity.old.ai.sensor;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.old.ai.MobBrain;
import net.minestom.server.entity.old.ai.memory.Memories;
import net.minestom.server.instance.EntityTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * One cached entity query serves following, food, grouping, looking, and hostile acquisition.
 */
public final class NearbyEntitySensor implements Sensor {
    private record Candidate(Entity entity, boolean food, double affinity, double attraction, boolean hostile) {
    }

    @Override
    public int interval(MobBrain brain) {
        return brain.settings().scheduling().sensorInterval();
    }

    @Override
    public void sense(MobBrain brain) {
        var mob = brain.mob();
        var instance = mob.getInstance();
        if (instance == null) return;
        var seen = new ArrayList<Memories.SeenEntity>();
        var hostiles = new ArrayList<LivingEntity>();
        int limit = Math.max(1, brain.policy().maxPerceivedEntities());
        var candidates = new PriorityQueue<>(Comparator.comparingDouble(
                (Candidate candidate) -> mob.getDistanceSquared(candidate.entity())).reversed());
        UUID explicitFollow = brain.memory().get(Memories.FOLLOW_TARGET);
        instance.getEntityTracker().nearbyEntities(mob.getPosition(), brain.policy().sensingRange(),
                EntityTracker.Target.ENTITIES, other -> {
                    if (other == mob || other.isRemoved() || other.getInstance() != instance
                            || other instanceof LivingEntity living && living.isDead()) return;
                    boolean food = other instanceof ItemEntity item && brain.policy().isFood(mob, item);
                    double affinity = other instanceof EntityMob neighbor ? brain.policy().groupAffinity(mob, neighbor) : 0;
                    double attraction = brain.policy().attraction(mob, other);
                    boolean hostile = other instanceof LivingEntity living && brain.policy().isHostile(mob, living);
                    if (!food && affinity <= 0 && attraction <= 0 && !hostile && !(other instanceof Player)
                            && !other.getUuid().equals(explicitFollow)) return;
                    Candidate candidate = new Candidate(other, food, affinity, attraction, hostile);
                    if (candidates.size() < limit) candidates.add(candidate);
                    else if (mob.getDistanceSquared(other) < mob.getDistanceSquared(candidates.element().entity())) {
                        candidates.remove();
                        candidates.add(candidate);
                    }
                });
        for (Candidate candidate : candidates) {
            var other = candidate.entity();
            if (!mob.hasLineOfSight(other)) continue;
            seen.add(new Memories.SeenEntity(other.getUuid(), other.getPosition().asVec(),
                    other instanceof Player, candidate.food(), candidate.affinity(), candidate.attraction(), brain.tick()));
            if (candidate.hostile() && other instanceof LivingEntity living) hostiles.add(living);
        }
        seen.sort(Comparator.comparingDouble(entity -> mob.getPosition().distanceSquared(entity.position())));
        brain.memory().remember(Memories.VISIBLE, new Memories.Perception(seen),
                brain.settings().scheduling().observationLifetime());
        int lifetime = brain.settings().scheduling().memoryLifetime();
        var remembered = new LinkedHashMap<UUID, Memories.SeenEntity>();
        var previous = brain.memory().get(Memories.RECENT_ENTITIES);
        if (previous != null) for (var entity : previous.entities()) {
            if (brain.tick() - entity.observedAt() < lifetime && brain.resolve(entity.id()) != null)
                remembered.put(entity.id(), entity);
        }
        for (var entity : seen) remembered.put(entity.id(), entity);
        // Bound retained perceptions in dense crowds, keeping current/nearest observations first.
        var recent = new ArrayList<>(remembered.values());
        recent.sort(Comparator.comparingLong(Memories.SeenEntity::observedAt).reversed()
                .thenComparingDouble(entity -> mob.getPosition().distanceSquared(entity.position())));
        brain.memory().remember(Memories.RECENT_ENTITIES,
                new Memories.Perception(recent.subList(0, Math.min(limit * 2, recent.size()))), lifetime);
        seen.stream().filter(Memories.SeenEntity::food).findFirst().ifPresent(food -> {
            var oldFood = brain.memory().get(Memories.FOOD);
            if (oldFood == null || !oldFood.id().equals(food.id()) || !oldFood.position().sameBlock(food.position()))
                brain.scheduler().retryFailures();
            brain.memory().remember(Memories.FOOD, food, lifetime);
        });
        // Explicit follow targets are retained until invalidated; automatic attraction is reconsidered by the goal.
        UUID combat = brain.memory().get(Memories.COMBAT_TARGET);
        if (combat == null || brain.remembered(combat) == null) {
            brain.memory().forget(Memories.COMBAT_TARGET);
            hostiles.stream().min(Comparator.comparingDouble(mob::getDistanceSquared)).ifPresent(target ->
                    brain.memory().set(Memories.COMBAT_TARGET, target.getUuid()));
        }
    }
}
