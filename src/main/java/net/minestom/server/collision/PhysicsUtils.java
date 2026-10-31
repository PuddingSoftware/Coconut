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
     * Simulates movement without stepping over obstacles.
     *
     * @see #simulateMovement(Pos, Vec, BoundingBox, WorldBorder, Block.Getter, Aerodynamics, boolean, boolean, boolean, boolean, double, PhysicsResult)
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
                                                 @Nullable PhysicsResult previousPhysicsResult) {
        return simulateMovement(position, velocity, boundingBox, worldBorder, blockGetter, aerodynamics,
                noGravity, hasPhysics, onGround, flying, 0, previousPhysicsResult);
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
     * @param worldBorder           the world border colliding with the entity, applied only when {@code hasPhysics} is true
     * @param blockGetter           the block getter to test block collisions against
     * @param aerodynamics          the current entity aerodynamics
     * @param noGravity             whether the entity has gravity
     * @param hasPhysics            whether the entity has physics
     * @param onGround              whether the entity is on the ground
     * @param flying                whether the entity is flying
     * @param stepHeight            the maximum height to step over obstacles, or zero to disable stepping
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
            physicsResult = CollisionUtils.handlePhysics(blockGetter, worldBorder, boundingBox,
                    position, velocity, previousPhysicsResult, false);

            // Border walls have no collision shape and cannot be stepped over.
            if (onGround && stepHeight > 0 && (physicsResult.collisionX() || physicsResult.collisionZ())
                    && (physicsResult.collisionShapes()[0] != null || physicsResult.collisionShapes()[2] != null)) {
                final PhysicsResult stepUpResult = tryStepUp(position, velocity, boundingBox, worldBorder,
                        blockGetter, stepHeight, physicsResult);
                if (stepUpResult != null) physicsResult = stepUpResult;
            }
        } else {
            physicsResult = CollisionUtils.blocklessCollision(position, velocity);
        }

        final Pos newPosition = physicsResult.newPosition();
        final Vec newVelocity = updateVelocity(newPosition, physicsResult.newVelocity(), blockGetter, aerodynamics,
                !newPosition.samePoint(position), flying, onGround, noGravity);
        if (newVelocity.samePoint(physicsResult.newVelocity())) return physicsResult;

        return new PhysicsResult(
                newPosition,
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
                physicsResult.collisionFraction()
        );
    }

    @Nullable
    private static PhysicsResult tryStepUp(Pos position,
                                           Vec velocity,
                                           BoundingBox boundingBox,
                                           WorldBorder worldBorder,
                                           Block.Getter blockGetter,
                                           double stepHeight,
                                           PhysicsResult normalResult) {
        // Keep the three sweeps separate: a diagonal sweep can hit the step before clearing its top.
        final Vec upwardsVelocity = new Vec(0.0D, stepHeight + Vec.EPSILON, 0.0D);
        final PhysicsResult upwardsPhysicsResult = CollisionUtils.handlePhysics(
                blockGetter,
                boundingBox,
                position,
                upwardsVelocity,
                null,
                false
        );

        // A low ceiling may still leave enough room for a smaller step.
        final double yDifference = upwardsPhysicsResult.newPosition().y() - position.y();
        if (yDifference <= Vec.EPSILON)
            return null;

        final Pos elevatedPosition = upwardsPhysicsResult.newPosition();
        final Vec horizontalVelocity = new Vec(velocity.x(), 0.0D, velocity.z());
        final PhysicsResult horizontalPhysicsResult = CollisionUtils.handlePhysics(
                blockGetter,
                worldBorder,
                boundingBox,
                elevatedPosition,
                horizontalVelocity,
                null,
                false
        );

        final Pos horizontalPosition = horizontalPhysicsResult.newPosition();
        // Height alone must not make a blocked step look better. Reject it before the landing sweep.
        if (horizontalDistanceSquared(position, horizontalPosition)
                <= horizontalDistanceSquared(position, normalResult.newPosition()) + Vec.EPSILON)
            return null;

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
                horizontalPhysicsResult.newVelocity().x(),
                downwardsPhysicsResult.collisionY() ? 0 : velocity.y(),
                horizontalPhysicsResult.newVelocity().z()
        );

        final boolean horizontalCollision = horizontalPhysicsResult.hasCollision();
        final var collisionPoints = horizontalCollision
                ? horizontalPhysicsResult.collisionPoints().clone() : downwardsPhysicsResult.collisionPoints();
        final var collisionShapes = horizontalCollision
                ? horizontalPhysicsResult.collisionShapes().clone() : downwardsPhysicsResult.collisionShapes();
        final var collisionShapePositions = horizontalCollision
                ? horizontalPhysicsResult.collisionShapePositions().clone() : downwardsPhysicsResult.collisionShapePositions();
        if (horizontalCollision) {
            collisionPoints[1] = downwardsPhysicsResult.collisionPoints()[1];
            collisionShapes[1] = downwardsPhysicsResult.collisionShapes()[1];
            collisionShapePositions[1] = downwardsPhysicsResult.collisionShapePositions()[1];
        }

        return new PhysicsResult(
                finalPosition,
                finalVelocity,
                downwardsPhysicsResult.collisionY(),
                horizontalPhysicsResult.collisionX(),
                downwardsPhysicsResult.collisionY(),
                horizontalPhysicsResult.collisionZ(),
                velocity,
                collisionPoints,
                collisionShapes,
                collisionShapePositions,
                horizontalPhysicsResult.hasCollision() || downwardsPhysicsResult.hasCollision(),
                downwardsPhysicsResult.collisionFraction()
        );
    }

    private static double horizontalDistanceSquared(Pos start, Pos end) {
        final double x = end.x() - start.x();
        final double z = end.z() - start.z();
        return x * x + z * z;
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
                ? blockGetter.getBlock(position.sub(0, 0.5000001, 0)).friction() * aerodynamics.horizontalAirResistance() : aerodynamics.horizontalAirResistance();
        double gravity = flying ? 0 : aerodynamics.gravity();
        double gravityDrag = flying ? 0.6 : aerodynamics.verticalAirResistance();

        double x = currentVelocity.x() * drag;
        double y = noGravity ? currentVelocity.y() : (currentVelocity.y() - gravity) * gravityDrag;
        double z = currentVelocity.z() * drag;

        return new Vec(Math.abs(x) < Vec.EPSILON ? 0 : x, Math.abs(y) < Vec.EPSILON ? 0 : y, Math.abs(z) < Vec.EPSILON ? 0 : z);
    }
}
