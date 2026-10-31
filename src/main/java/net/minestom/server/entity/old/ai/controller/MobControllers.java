package net.minestom.server.entity.old.ai.controller;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.old.ai.Control;
import net.minestom.server.entity.old.ai.MobBrain;
import net.minestom.server.entity.old.ai.goal.Goal;
import net.minestom.server.entity.old.ai.memory.Memories;
import net.minestom.server.entity.old.ai.navigation.NavigationController;
import net.minestom.server.entity.old.ai.navigation.PathRequestBudget;
import net.minestom.server.pathfinding.Pathfinder;
import net.minestom.server.property.ServerProperties;
import net.minestom.server.utils.position.PositionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Tick-local movement, look, and jump requests; persistent navigation is a separate service.
 */
public final class MobControllers {
    private final MobBrain brain;
    private final NavigationController navigation;
    private @Nullable Goal movementOwner;
    private @Nullable Vec movementTarget;
    private @Nullable Vec lookTarget;
    private boolean jump;
    private double speed;
    private long nextAttack;

    public MobControllers(MobBrain brain, Pathfinder pathfinder, PathRequestBudget budget) {
        this.brain = brain;
        this.navigation = new NavigationController(brain, pathfinder, budget);
    }

    public NavigationController navigation() {
        return navigation;
    }

    public void beginTick() {
        movementTarget = null;
        lookTarget = null;
        jump = false;
    }

    public void move(Goal owner, Point target, double speedMultiplier) {
        brain.scheduler().require(owner, Control.MOVE);
        if (!Double.isFinite(speedMultiplier) || speedMultiplier < 0)
            throw new IllegalArgumentException("Invalid movement speed multiplier");
        movementOwner = owner;
        movementTarget = target.asVec();
        speed = speedMultiplier;
    }

    public void look(Goal owner, Point target) {
        brain.scheduler().require(owner, Control.LOOK);
        lookTarget = target.asVec();
    }

    public void jump(Goal owner) {
        brain.scheduler().require(owner, Control.JUMP);
        jump = true;
    }

    public void stopMovement(Goal owner) {
        if (movementOwner != owner) return;
        movementOwner = null;
        movementTarget = null;
        var velocity = brain.mob().getVelocity();
        brain.mob().setVelocity(new Vec(0, velocity.y(), 0));
    }

    public boolean attack(Goal owner, UUID targetId) {
        brain.scheduler().require(owner, Control.COMBAT);
        var target = brain.resolve(targetId);
        if (!(target instanceof LivingEntity living) || brain.tick() < nextAttack
                || brain.mob().getDistanceSquared(target) > Math.pow(brain.policy().attackRange(), 2)
                || !brain.mob().hasLineOfSight(target)) return false;
        nextAttack = brain.tick() + Math.max(1, brain.policy().attackInterval());
        brain.policy().attack(brain.mob(), living);
        return true;
    }

    public boolean interact(Goal owner, UUID foodId) {
        brain.scheduler().require(owner, Control.INTERACT);
        var target = brain.resolve(foodId);
        if (!(target instanceof ItemEntity item) || brain.mob().getDistanceSquared(item) > 2.25
                || !brain.mob().hasLineOfSight(item) || !brain.policy().isFood(brain.mob(), item)) return false;
        return brain.policy().consumeFood(brain.mob(), item);
    }

    public void tick() {
        navigation.tick();
        var mob = brain.mob();
        var position = mob.getPosition();
        if (movementTarget != null) {
            double dx = movementTarget.x() - position.x();
            double dz = movementTarget.z() - position.z();
            double distance = Math.hypot(dx, dz);
            double blocksPerTick = mob.getAttributeValue(Attribute.MOVEMENT_SPEED) * speed;
            double magnitude = Math.min(blocksPerTick, distance / brain.stepTicks())
                    * ServerProperties.SERVER_TICKS_PER_SECOND.get();
            Vec velocity = mob.getVelocity();
            mob.setVelocity(distance < Vec.EPSILON ? new Vec(0, velocity.y(), 0)
                    : new Vec(dx / distance * magnitude, velocity.y(), dz / distance * magnitude));
        } else if (movementOwner != null) {
            stopMovement(movementOwner);
        }
        if (jump) {
            var danger = brain.memory().get(Memories.DANGER);
            boolean swimming = Boolean.TRUE.equals(brain.memory().get(Memories.IN_WATER))
                    || danger != null && danger.lava();
            if (mob.isOnGround() || swimming) {
                double jumpSpeed = mob.getAttributeValue(Attribute.JUMP_STRENGTH)
                        * ServerProperties.SERVER_TICKS_PER_SECOND.get();
                mob.setVelocity(mob.getVelocity().withY(Math.max(mob.getVelocity().y(), swimming ? 3 : jumpSpeed)));
            }
        }
        if (lookTarget != null) {
            double dx = lookTarget.x() - position.x();
            double dy = lookTarget.y() - (position.y() + mob.getEyeHeight());
            double dz = lookTarget.z() - position.z();
            if (dx * dx + dy * dy + dz * dz > Vec.EPSILON)
                mob.setView(PositionUtils.getLookYaw(dx, dz), PositionUtils.getLookPitch(dx, dy, dz));
        }
    }

    public void release(Goal owner) {
        navigation.release(owner);
        stopMovement(owner);
        if (brain.scheduler().holds(owner, Control.LOOK)) lookTarget = null;
        if (brain.scheduler().holds(owner, Control.JUMP)) jump = false;
    }

    public void clear() {
        navigation.clear();
        if (movementOwner != null) stopMovement(movementOwner);
        beginTick();
    }
}
