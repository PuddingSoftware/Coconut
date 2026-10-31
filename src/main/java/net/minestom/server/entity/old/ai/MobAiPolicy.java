package net.minestom.server.entity.old.ai;

import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.item.PickupItemEvent;

/**
 * Species/game rules. Override these hooks for diets, factions, combat damage, and interactions.
 */
public class MobAiPolicy {
    public enum DamageResponse {FLEE, DEFEND, RETALIATE}

    public double sensingRange() {
        return 16;
    }

    public double explorationRange() {
        return 10;
    }

    public int terrainSamples() {
        return 16;
    }

    public int maxPerceivedEntities() {
        return 32;
    }

    public double followDistance() {
        return 3;
    }

    public double attackRange() {
        return 2;
    }

    public int attackInterval() {
        return 20;
    }

    public int restDuration() {
        return 100;
    }

    public int idleDuration() {
        return 40;
    }

    public DamageResponse damageResponse(EntityMob mob) {
        return DamageResponse.FLEE;
    }

    public boolean isHostile(EntityMob mob, LivingEntity other) {
        return false;
    }

    public double attraction(EntityMob mob, Entity other) {
        return 0;
    }

    public double groupAffinity(EntityMob mob, EntityMob other) {
        return mob.getEntityType().equals(other.getEntityType()) ? 1 : 0;
    }

    public boolean isFood(EntityMob mob, ItemEntity item) {
        return item.isPickable() && item.getItemStack().has(DataComponents.FOOD);
    }

    /**
     * Mirrors Minestom's attack convention: the game handles damage in EntityAttackEvent.
     */
    public void attack(EntityMob mob, LivingEntity target) {
        mob.swingMainHand();
        EventDispatcher.call(new EntityAttackEvent(mob, target));
    }

    /**
     * Consumes one item after the normal cancellable pickup event.
     */
    public boolean consumeFood(EntityMob mob, ItemEntity item) {
        PickupItemEvent event = new PickupItemEvent(mob, item);
        EventDispatcher.call(event);
        if (event.isCancelled() || item.isRemoved() || !isFood(mob, item)) return false;
        var stack = item.getItemStack();
        if (stack.amount() == 1) item.remove();
        else item.setItemStack(stack.withAmount(stack.amount() - 1));
        return true;
    }
}
