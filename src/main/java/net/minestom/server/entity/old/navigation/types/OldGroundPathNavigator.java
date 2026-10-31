package net.minestom.server.entity.old.navigation.types;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.old.navigation.PathNavigator;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.pathfinding.Pathfinder;
import net.minestom.server.utils.position.PositionUtils;
import org.jetbrains.annotations.NotNull;

// TODO: https://chatgpt.com/c/69dedcf6-c814-83ea-85e8-2cad0c2c2eed
public final class OldGroundPathNavigator extends PathNavigator {

    // TODO: add stuck detection

    // TODO: mobs should float on water in the ground path navigator (they should not be able to submerge themselves)

    private static final double MIN_WAYPOINT_RANGE = 0.1D;
//    private static final double MOVEMENT_EPSILON_SQUARED = 1.0E-8;
//    private static final double LOOK_EPSILON_SQUARED = 1.0E-8;
//    private static final double STEP_VERTICAL_SPEED = 0.8D;
//    private static final double JUMP_VERTICAL_SPEED = 10.0D;
//    private static final double JUMP_TRIGGER_DISTANCE_SQUARED = 1.5D * 1.5D;
//    private static final double VERTICAL_EPSILON = 1.0E-3;

    public OldGroundPathNavigator(@NotNull EntityMob entityMob,
                                  @NotNull Pathfinder pathfinder) {
        super(entityMob, pathfinder);
    }

    public OldGroundPathNavigator(@NotNull EntityMob entityMob) {
        super(entityMob);
    }

    @Override
    public void navigatePath() {
        // can't navigate an empty path
        if (points.isEmpty())
            return;

        final double movementSpeed = entityMob.getAttributeValue(Attribute.MOVEMENT_SPEED);
        if (movementSpeed <= 0) {
            return;
        }

        advancePathIndex(movementSpeed);

        // TODO: load point and nextPoint if the chunks are unloaded but autoLoadChunks is enabled
        final Point point = points.get(currentIndex);
        final Point nextPoint;
        if (currentIndex >= points.size() - 1) {
            nextPoint = point;
        } else {
            nextPoint = points.get(currentIndex + 1);
        }

        moveTo(point, nextPoint, movementSpeed/*, 0.0D*/);
    }

    private void advancePathIndex(double movementSpeed) {
        final double waypointRange = Math.max(movementSpeed, MIN_WAYPOINT_RANGE);
        final double waypointRangeSquared = waypointRange * waypointRange;

        while (currentIndex < points.size() - 1) {
            final Pos entityPosition = entityMob.getPosition();
            final Point currentPoint = points.get(currentIndex);
            final double targetX = getBlockCenterX(currentPoint);
            final double targetZ = getBlockCenterZ(currentPoint);

            final double dx = targetX - entityPosition.x();
            final double dz = targetZ - entityPosition.z();
            final double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > waypointRangeSquared) {
                break;
            }

            currentIndex++;
        }
    }

//    private void jumpTo(Point point,
//                        Point nextPoint,
//                        double movementSpeed) {
//        // move the entity towards the next point
//        moveTo(point, nextPoint, movementSpeed, 0.0D);
//
//        // jump when the entity is on the ground
//        // this prevents "infinite" jumps, where the entity could ascend forever and never reach the next point
//        if (entityMob.isOnGround()) {
//            final Vec velocity = entityMob.getVelocity();
//            // Minestom uses blocks per second instead of Vanilla's blocks per tick
//            // 0.42 is the initial jump velocity of an entity in Vanilla
//            // this will give us the correct starting velocity for a jump: 0.42 * 20 = 8.4
//            entityMob.setVelocity(velocity.withY(8.4D));
//        }
//    }

    private void moveTo(Point point,
                        Point nextPoint,
                        double movementSpeed
                        /*double verticalSpeed*/) {
        final Pos pos = entityMob.getPosition();
        final double dx = point.x() - pos.x();
//        final double dy = point.y() - pos.y();
        final double dz = point.z() - pos.z();

        final double dxLook = nextPoint.x() - pos.x();
        final double dyLook = nextPoint.y() - pos.y();
        final double dzLook = nextPoint.z() - pos.z();

        final double horizDistSq = dx * dx + dz * dz;
        final double horizDist = Math.sqrt(horizDistSq);

        if (horizDistSq < 2.5E-7) {
            entityMob.setVelocity(new Vec(0, entityMob.getVelocity().y(), 0));
            return;
        }

        movementSpeed = Math.min(movementSpeed, horizDist);

        final double radians = Math.atan2(dz, dx);
        final double velX = Math.cos(radians) * movementSpeed * 20;
        final double velZ = Math.sin(radians) * movementSpeed * 20;

        final float yaw = PositionUtils.getLookYaw(dxLook, dzLook);
        final float pitch = PositionUtils.getLookPitch(dxLook, dyLook, dzLook);

        Vec currentVel = entityMob.getVelocity();
        entityMob.setVelocity(new Vec(velX, currentVel.y(), velZ));
        entityMob.setView(yaw, pitch);

//        final Pos entityPosition = entityMob.getPosition();
//        final double targetX = getBlockCenterX(point);
//        final double targetZ = getBlockCenterZ(point);
//        final double nextTargetX = getBlockCenterX(nextPoint);
//        final double nextTargetZ = getBlockCenterZ(nextPoint);
//
//        // get the difference between the point we need to get to and the entities current position
//        final double dx = targetX - entityPosition.x();
//        final double dy = point.y() - entityPosition.y();
//        final double dz = targetZ - entityPosition.z();
//        final double horizontalDistanceSquared = dx * dx + dz * dz;
//        final boolean hasHorizontalMovement = horizontalDistanceSquared > MOVEMENT_EPSILON_SQUARED;
//        final boolean hasVerticalMovement = Math.abs(verticalSpeed) > Vec.EPSILON;
//        if (!hasHorizontalMovement && !hasVerticalMovement) {
//            return;
//        }
//
//        // get the direction that the entity should look at, which will be the difference between the next point and the entities current position
//        double dxLook = nextTargetX - entityPosition.x();
//        double dyLook = nextPoint.y() - entityPosition.y();
//        double dzLook = nextTargetZ - entityPosition.z();
//        final double lookDistanceSquared = dxLook * dxLook + dyLook * dyLook + dzLook * dzLook;
//        if (lookDistanceSquared <= LOOK_EPSILON_SQUARED) {
//            dxLook = dx;
//            dyLook = dy;
//            dzLook = dz;
//        }
//
//        final double speedX;
//        final double speedZ;
//        if (hasHorizontalMovement) {
//            final double horizontalDistance = Math.sqrt(horizontalDistanceSquared);
//            final double clampedSpeed = Math.min(movementSpeed, horizontalDistance);
//            final double speedScale = clampedSpeed / horizontalDistance;
//            speedX = dx * speedScale;
//            speedZ = dz * speedScale;
//        } else {
//            speedX = 0.0D;
//            speedZ = 0.0D;
//        }
//
//        final float yaw = PositionUtils.getLookYaw(dxLook, dzLook);
//        final float pitch = PositionUtils.getLookPitch(dxLook, dyLook, dzLook);
//
//        // actually move the entity with respect to physics
//        final PhysicsResult physicsResult = CollisionUtils.handlePhysics(entityMob, new Vec(speedX, verticalSpeed, speedZ));
//        entityMob.refreshPosition(physicsResult.newPosition().asPos().withView(yaw, pitch));
    }

    private static double getBlockCenterX(Point point) {
        return point.blockX() + 0.5D;
    }

    private static double getBlockCenterZ(Point point) {
        return point.blockZ() + 0.5D;
    }
}