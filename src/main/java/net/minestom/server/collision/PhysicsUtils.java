package net.minestom.server.collision;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

public final class PhysicsUtils {

    private PhysicsUtils() {
    }

    /**
     * Simulate the entity's movement physics
     * <p>
     * This is done by first attempting to move the entity forward with the
     * current velocity passed in. Then adjusting the velocity by applying
     * air resistance and friction.
     *
     * @param position              the current entity position
     * @param velocity              the current entity velocity in blocks/tick
     * @param boundingBox           the current entity bounding box
     * @param worldBorder           the world border colliding with the entity, applied only when {@code entityHasPhysics} is true
     * @param blockGetter           the block getter to test block collisions against
     * @param aerodynamics          the current entity aerodynamics
     * @param noGravity             whether the entity has gravity
     * @param hasPhysics            whether the entity has physics
     * @param onGround              whether the entity is on the ground
     * @param flying                whether the entity is flying
     * @param previousPhysicsResult the physics result from the previous simulation or null
     * @return a {@link PhysicsResult} containing the resulting physics state of this simulation
     */
    public static PhysicsResult simulateMovement(Pos position,
                                                 Vec velocity,
                                                 BoundingBox boundingBox,
                                                 WorldBorder worldBorder,
                                                 Block.Getter blockGetter,
                                                 Aerodynamics aerodynamics,
                                                 boolean noGravity,
                                                 boolean hasPhysics,
                                                 boolean onGround,
                                                 boolean flying,
                                                 double stepHeight,
                                                 @Nullable PhysicsResult previousPhysicsResult) {
        PhysicsResult physicsResult;
        if (hasPhysics) {
            physicsResult = CollisionUtils.handlePhysics(blockGetter, boundingBox, position, velocity, previousPhysicsResult, false);

            // check to see if we need to check for a step
            if (onGround && stepHeight > 0.0D && (physicsResult.collisionX() || physicsResult.collisionZ())) {
                final PhysicsResult stepUpPhysicsResult = tryStepUp(position, velocity, boundingBox, blockGetter, stepHeight);

                if (stepUpPhysicsResult != null) {
                    double normalDistance = position.distanceSquared(physicsResult.newPosition());
                    double stepUpDistance = position.distanceSquared(stepUpPhysicsResult.newPosition());

                    if (stepUpDistance > normalDistance + Vec.EPSILON) {
                        physicsResult = stepUpPhysicsResult;
                    }
                }
            }
        } else {
            physicsResult = CollisionUtils.blocklessCollision(position, velocity);
        }

        final Pos newPosition = physicsResult.newPosition();
        Vec newVelocity = physicsResult.newVelocity();

        final Pos positionWithinBorder = CollisionUtils.applyWorldBorder(worldBorder, position, newPosition);
        newVelocity = updateVelocity(positionWithinBorder, newVelocity, blockGetter, aerodynamics, !positionWithinBorder.samePoint(position), flying, onGround, noGravity);

        final boolean cached = physicsResult.cached()
                && newVelocity.samePoint(physicsResult.newVelocity())
                && positionWithinBorder.samePoint(newPosition);

        return new PhysicsResult(
                positionWithinBorder,
                newVelocity,
                physicsResult.isOnGround(),
                physicsResult.collisionX(),
                physicsResult.collisionY(),
                physicsResult.collisionZ(),
                physicsResult.originalDelta(),
                physicsResult.collisionPoints(),
                physicsResult.collisionShapes(),
                physicsResult.collisionShapePositions(),
                physicsResult.hasCollision(),
                physicsResult.res(),
                cached
        );
    }

    @Nullable
    private static PhysicsResult tryStepUp(Pos position,
                                           Vec velocity,
                                           BoundingBox boundingBox,
                                           Block.Getter blockGetter,
                                           double stepHeight) {
        // make sure there is velocity
        if (velocity.x() == 0.0D && velocity.z() == 0.0D)
            return null;

        // stepping is 3 steps: upwards -> horizontal -> downwards
        final Vec upwardsVelocity = new Vec(0.0D, stepHeight + Vec.EPSILON, 0.0D);
        final PhysicsResult upwardsPhysicsResult = CollisionUtils.handlePhysics(
                blockGetter,
                boundingBox,
                position,
                upwardsVelocity,
                null,
                false
        );

        // check if the up movement results in a step or is blocked
        final double yDifference = upwardsPhysicsResult.newPosition().y() - position.y();
        if (yDifference <= Vec.EPSILON)
            return null;

        final Pos elevatedPosition = upwardsPhysicsResult.newPosition();
        final Vec horizontalVelocity = new Vec(velocity.x(), 0.0D, velocity.z());
        final PhysicsResult horizontalPhysicsResult = CollisionUtils.handlePhysics(
                blockGetter,
                boundingBox,
                elevatedPosition,
                horizontalVelocity,
                null,
                false
        );

        final Pos horizontalPosition = horizontalPhysicsResult.newPosition();
        final Vec downwardsVelocity = new Vec(0.0D, -yDifference, 0.0D);
        final PhysicsResult downwardsPhysicsResult = CollisionUtils.handlePhysics(
                blockGetter,
                boundingBox,
                horizontalPosition,
                downwardsVelocity,
                null,
                false
        );

        final Pos finalPosition = downwardsPhysicsResult.newPosition();
        final double finalStepHeight = finalPosition.y() - position.y();
        if (finalStepHeight < -Vec.EPSILON || finalStepHeight > stepHeight + Vec.EPSILON)
            return null;

        final Vec finalVelocity = new Vec(
                horizontalPhysicsResult.collisionX() ? 0.0D : velocity.x(),
                velocity.y(),
                horizontalPhysicsResult.collisionZ() ? 0.0D : velocity.z()
        );

        return new PhysicsResult(
                finalPosition,
                finalVelocity,
                downwardsPhysicsResult.collisionY(),
                horizontalPhysicsResult.collisionX(),
                false,
                horizontalPhysicsResult.collisionZ(),
                velocity,
                downwardsPhysicsResult.collisionPoints(),
                downwardsPhysicsResult.collisionShapes(),
                downwardsPhysicsResult.collisionShapePositions(),
                true,
                downwardsPhysicsResult.res(),
                false
        );
    }

    /**
     * Calculates an updated velocity for an entity
     * <p>
     * If the position has not changed then the x and z values will not be touched, and only gravity will be accounted for if the entity is not flying.
     * Otherwise, the velocity will be adjusted by applying air resistance, gravity, and friction (only if the entity is on the ground).
     *
     * @param position        the current entity position
     * @param currentVelocity the current entity velocity in blocks/tick
     * @param blockGetter     the block getter to test block collisions against
     * @param aerodynamics    the current entity aerodynamics
     * @param positionChanged whether the position changed for the entity
     * @param flying          whether the entity is flying
     * @param onGround        whether the entity is on the ground
     * @param noGravity       whether the entity has gravity
     * @return the updated velocity or {@link Vec#ZERO} if the entity is flying
     */
    public static Vec updateVelocity(Pos position,
                                     Vec currentVelocity,
                                     Block.Getter blockGetter,
                                     Aerodynamics aerodynamics,
                                     boolean positionChanged,
                                     boolean flying,
                                     boolean onGround,
                                     boolean noGravity) {
        if (!positionChanged) {
            if (flying)
                return Vec.ZERO;

            return new Vec(0, noGravity ? 0 : -aerodynamics.gravity() * aerodynamics.verticalAirResistance(), 0);
        }

        double drag = onGround
                ? blockGetter.getBlock(position.sub(0, 0.5000001, 0)).registry().friction() * aerodynamics.horizontalAirResistance() : aerodynamics.horizontalAirResistance();
        double gravity = flying ? 0 : aerodynamics.gravity();
        double gravityDrag = flying ? 0.6 : aerodynamics.verticalAirResistance();

        double x = currentVelocity.x() * drag;
        double y = noGravity ? currentVelocity.y() : (currentVelocity.y() - gravity) * gravityDrag;
        double z = currentVelocity.z() * drag;

        return new Vec(Math.abs(x) < Vec.EPSILON ? 0 : x, Math.abs(y) < Vec.EPSILON ? 0 : y, Math.abs(z) < Vec.EPSILON ? 0 : z);
    }
}