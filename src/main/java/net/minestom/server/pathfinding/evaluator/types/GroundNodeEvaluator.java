package net.minestom.server.pathfinding.evaluator.types;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.Shape;
import net.minestom.server.collision.SweepResult;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.pathfinding.context.MobContext;
import net.minestom.server.pathfinding.context.EvaluationContext;
import net.minestom.server.pathfinding.data.Node;
import net.minestom.server.pathfinding.evaluator.result.NodeEvaluationResult;
import net.minestom.server.pathfinding.options.PathfinderOptions;
import net.minestom.server.pathfinding.evaluator.NodeEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// a ground node evaluator that is designed with performance in mind
public final class GroundNodeEvaluator implements NodeEvaluator {

    // TODO: support water movement (just floating for now)
    // TODO: support larger hitboxes
    // TODO: fix the issue where the first iteration results in updated nodes?
    // TODO: use mutable positions to avoid object churn

    @Override
    public @Nullable Node getValidStart(@NotNull MobContext mobContext,
                                        @NotNull Point target) {
        final Point startPoint = mobContext.startPoint();

        // check if the mob has clearance to spawn at the starting point
        // this will typically fail if the mob is stuck in a block
        if (!canFit(mobContext, startPoint)) {
            return null;
        }

        // set the ground block
        final Node startNode = new Node(startPoint, startPoint, target, 0);
        //startNode.setGroundBlock(mobContext.instance().getBlock(startPoint.sub(0.0D, Vec.EPSILON, 0.0D), Block.Getter.Condition.TYPE));

        return startNode;
    }

    @Override
    public @NotNull NodeEvaluationResult isValidMove(@NotNull Node oldNode,
                                                     @NotNull Node newNode,
                                                     @NotNull MobContext mobContext,
                                                     @NotNull PathfinderOptions options) {
        // center the points since our pathfinding works on going from the center of one block to another
        final Point oldPoint = new Pos(oldNode.point().centerBlockX(), oldNode.point().y(), oldNode.point().centerBlockZ());
        final Point newPoint = new Pos(newNode.point().centerBlockX(), newNode.point().y(), newNode.point().centerBlockZ());
        final Point belowNewPoint = newPoint.sub(0, 1, 0);

        // handle getting blocks in loaded and unloaded chunks
        // this prevents NPEs when getting blocks in an unloaded chunk
        Block oldBlock;
        Block newBlock;
        Block belowNewBlock;
        try {
            oldBlock = mobContext.instance().getBlock(oldPoint, Block.Getter.Condition.TYPE);
            newBlock = mobContext.instance().getBlock(newPoint, Block.Getter.Condition.TYPE);
            belowNewBlock = mobContext.instance().getBlock(belowNewPoint, Block.Getter.Condition.TYPE);
        } catch (NullPointerException _) {
            if (options.isAutoLoadChunks()) {
                // load the chunks and get the blocks again
                mobContext.instance().loadChunk(oldPoint).join();
                mobContext.instance().loadChunk(newPoint).join();

                oldBlock = mobContext.instance().getBlock(oldPoint, Block.Getter.Condition.TYPE);
                newBlock = mobContext.instance().getBlock(newPoint, Block.Getter.Condition.TYPE);
                belowNewBlock = mobContext.instance().getBlock(belowNewPoint, Block.Getter.Condition.TYPE);
            } else {
                // we can't pathfind in unloaded chunks
                return new NodeEvaluationResult(NodeEvaluationResult.Status.INVALID_MOVE);
            }
        }

        final Shape oldBlockShape = oldBlock.collisionShape();
        final Shape newBlockShape = newBlock.collisionShape();
        final Shape belowNewBlockShape = belowNewBlock.collisionShape();

        final boolean largeBoundingBox = mobContext.boundingBox().width() > 1.0D || mobContext.boundingBox().depth() > 1.0D;

        final EvaluationContext evaluationContext = new EvaluationContext(
                mobContext,
                oldNode,
                newNode,
                oldPoint,
                newPoint,
                belowNewPoint,
                oldBlock,
                newBlock,
                belowNewBlock,
                oldBlockShape,
                newBlockShape,
                belowNewBlockShape,
                largeBoundingBox
        );

        return checkMove(evaluationContext);
    }

    // checks to make sure that a move is valid
    @NotNull
    @SuppressWarnings("MethodCanBeStatic")
    private NodeEvaluationResult checkMove(@NotNull EvaluationContext evaluationContext) {
        // check if they can move to this position without any obstructions in the way
        if (hasBlockCollision(evaluationContext, evaluationContext.oldPoint(), evaluationContext.newPoint())) {
            // since their is a block collision at the new point, we'll check to see if they can step or jump to get in a position where they will fit
            return checkUpwardsMove(evaluationContext);
        }

        // TODO: support falling for larger bounding boxes

        return checkFallMoveForSmallBoundingBox(evaluationContext);
    }

    @NotNull
    @SuppressWarnings("MethodCanBeStatic")
    private NodeEvaluationResult checkFallMoveForSmallBoundingBox(@NotNull EvaluationContext evaluationContext) {
        // check if the blocks up to the max safe fall distance are air
        // this is our fast exit before needing expensive physics calls
        final int newPointX = evaluationContext.newPoint().blockX();
        final int newPointY = evaluationContext.newPoint().blockY();
        final int newPointZ = evaluationContext.newPoint().blockZ();

        double landingY = Double.MAX_VALUE;
        boolean requiresPreciseCheck = false;
        for (int y = newPointY; y >= evaluationContext.belowNewPoint().blockY() - evaluationContext.mobContext().safeFallDistance(); y--) {
            final Block block = evaluationContext.instance().getBlock(newPointX, y, newPointZ, Block.Getter.Condition.TYPE);
            if (block != null && !block.air()) {
                final Shape blockShape = block.collisionShape();
                if (blockShape.relativeStart().x() == 0.0D && blockShape.relativeStart().z() == 0.0D
                        && blockShape.relativeEnd().x() == 1.0D && blockShape.relativeEnd().z() == 1.0D) {
                    // we know that this shape is consistent with a block that an entity could not fall through (it takes up the entire 1x1 space)
                    // we don't need to perform a precise physics check since we know this
                    landingY = y + blockShape.relativeEnd().y();
                } else {
                    // setting the landingY is only to get over the first check outside of this loop
                    // technically we don't know if they can land at this Y value
                    landingY = y + blockShape.relativeEnd().y();
                    requiresPreciseCheck = true;
                }

                break;
            }
        }

        // check if there was any fall
        // if not, we don't have to update the node and they can just walk
        if (landingY == evaluationContext.newPoint().y()) {
            // store the block below the new block for future cost processing
//            evaluationContext.newNode().setGroundBlock(evaluationContext.belowNewBlock());
            return new NodeEvaluationResult(NodeEvaluationResult.Status.VALID_MOVE);
        }

        // check if the fall would exceed the max safe fall distance
        // we know it does if landingY doesn't get updated to an appropriate value
        if (landingY == Double.MAX_VALUE) {
            return new NodeEvaluationResult(NodeEvaluationResult.Status.INVALID_MOVE);
        }

        // check if we need to do additional checks during the fall
        // this will happen because of weirdly sized blocks like trapdoors, etc...
        Node fallNode;
        if (requiresPreciseCheck) {
            final Vec velocity = new Vec(0, -evaluationContext.mobContext().safeFallDistance(), 0);
            final PhysicsResult result = CollisionUtils.handlePhysics(
                    evaluationContext.instance(),
                    evaluationContext.boundingBox(),
                    evaluationContext.newPoint().asPos(),
                    velocity,
                    null,
                    true
            );

            if (!result.isOnGround())
                return new NodeEvaluationResult(NodeEvaluationResult.Status.INVALID_MOVE);

            fallNode = new Node(
                    new Pos(newPointX + 0.5D, result.newPosition().y(), newPointZ + 0.5D),
                    evaluationContext.newNode().start(),
                    evaluationContext.newNode().target(),
                    evaluationContext.newNode().depth() + 1
            );
        } else {
            fallNode = new Node(
                    new Pos(newPointX + 0.5D, landingY, newPointZ + 0.5D),
                    evaluationContext.newNode().start(),
                    evaluationContext.newNode().target(),
                    evaluationContext.newNode().depth() + 1
            );
        }

        // nudge down a tiny bit to get the appropriate ground block
//        fallNode.setGroundBlock(evaluationContext.instance().getBlock(fallNode.point().sub(0.0D, Vec.EPSILON, 0.0D), Block.Getter.Condition.TYPE));
        return new NodeEvaluationResult(NodeEvaluationResult.Status.VALID_MOVE, fallNode);
    }

    @NotNull
    @SuppressWarnings("MethodCanBeStatic")
    private NodeEvaluationResult checkUpwardsMove(@NotNull EvaluationContext evaluationContext) {
        final Shape oldBlockShape = evaluationContext.oldBlockShape();
        final Shape newBlockShape = evaluationContext.newBlockShape();

        final double oldRelativeGroundHeight = oldBlockShape.relativeEnd().y();
        final double newRelativeHeight = newBlockShape.relativeEnd().y();

        final Shape aboveNewBlockShape = evaluationContext.instance()
                .getBlock(evaluationContext.newPoint().add(0.0D, 1.0D, 0.0D), Block.Getter.Condition.TYPE)
                .collisionShape();

        // Possible heights to try standing/moving at:
        // 1. top of the new block collision shape
        // 2. one full block above the new point
        // 3. top of the collision shape above the new point
        final double[] candidateRelativeHeights = new double[]{
                newRelativeHeight,
                1.0D,
                1.0D + aboveNewBlockShape.relativeEnd().y()
        };

        double previousCandidate = Double.NaN;

        for (double candidateRelativeHeight : candidateRelativeHeights) {
            if (candidateRelativeHeight <= 0.0D) {
                continue;
            }

            // Avoid checking the same height twice.
            if (!Double.isNaN(previousCandidate)
                    && Math.abs(candidateRelativeHeight - previousCandidate) < 1.0E-6D) {
                continue;
            }

            previousCandidate = candidateRelativeHeight;

            final double totalUpwardsHeight = candidateRelativeHeight - oldRelativeGroundHeight;

            // Not actually moving upward.
            if (totalUpwardsHeight <= 0.0D) {
                continue;
            }

            // Max allowed upward movement.
            if (totalUpwardsHeight > 1.5D) {
                continue;
            }

            final Point newTestPoint = evaluationContext.newPoint()
                    .add(0.0D, candidateRelativeHeight, 0.0D);

            final Point modifiedOldTestPoint = evaluationContext.oldPoint()
                    .withY(newTestPoint.y());

            if (hasBlockCollision(evaluationContext, modifiedOldTestPoint, newTestPoint)) {
                continue;
            }

            final Node upwardsNode = new Node(
                    evaluationContext.newPoint().add(0.0D, totalUpwardsHeight, 0.0D),
                    evaluationContext.newNode().start(),
                    evaluationContext.newNode().target(),
                    evaluationContext.newNode().depth() + 1
            );

//            upwardsNode.setGroundBlock(
//                    evaluationContext.instance().getBlock(
//                            upwardsNode.point().sub(0.0D, Vec.EPSILON, 0.0D),
//                            Block.Getter.Condition.TYPE
//                    )
//            );

            return new NodeEvaluationResult(NodeEvaluationResult.Status.VALID_MOVE, upwardsNode);
        }

        return new NodeEvaluationResult(NodeEvaluationResult.Status.INVALID_MOVE);
    }

    @SuppressWarnings("MethodCanBeStatic")
    private boolean canFit(@NotNull MobContext mobContext,
                           @NotNull Point point) {
        return CollisionUtils.canFit(
                mobContext.boundingBox(),
                mobContext.instance(),
                point
        );
    }

    @SuppressWarnings("MethodCanBeStatic")
    private boolean hasBlockCollision(@NotNull EvaluationContext evaluationContext,
                                      @NotNull Point currentPoint,
                                      @NotNull Point newPoint) {
        final Instance instance = evaluationContext.instance();
        final BoundingBox boundingBox = evaluationContext.boundingBox();

        final SweepResult result = new SweepResult(1, 0, 0, 0, null, 0, 0, 0, 0, 0, 0);

        // get the starting and ending coordinates of the bounding box and then get the broad-phase bounding box
        // if no collisions occur in the broad-phase bounding box then we don't need to perform an expensive AABB swept intersection check
        final double startMinX = currentPoint.x() + boundingBox.minX();
        final double startMinY = currentPoint.y() + boundingBox.minY();
        final double startMinZ = currentPoint.z() + boundingBox.minZ();
        final double startMaxX = currentPoint.x() + boundingBox.maxX();
        final double startMaxY = currentPoint.y() + boundingBox.maxY();
        final double startMaxZ = currentPoint.z() + boundingBox.maxZ();

        final double endMinX = newPoint.x() + boundingBox.minX();
        final double endMinY = newPoint.y() + boundingBox.minY();
        final double endMinZ = newPoint.z() + boundingBox.minZ();
        final double endMaxX = newPoint.x() + boundingBox.maxX();
        final double endMaxY = newPoint.y() + boundingBox.maxY();
        final double endMaxZ = newPoint.z() + boundingBox.maxZ();

        final double broadPhaseMinX = Math.min(startMinX, endMinX);
        final double broadPhaseMinY = Math.min(startMinY, endMinY);
        final double broadPhaseMinZ = Math.min(startMinZ, endMinZ);
        final double broadPhaseMaxX = Math.max(startMaxX, endMaxX);
        final double broadPhaseMaxY = Math.max(startMaxY, endMaxY);
        final double broadPhaseMaxZ = Math.max(startMaxZ, endMaxZ);

        final int minBlockX = (int) Math.floor(broadPhaseMinX);
        final int minBlockY = (int) Math.floor(broadPhaseMinY);
        final int minBlockZ = (int) Math.floor(broadPhaseMinZ);
        final int maxBlockX = (int) Math.ceil(broadPhaseMaxX);
        final int maxBlockY = (int) Math.ceil(broadPhaseMaxY);
        final int maxBlockZ = (int) Math.ceil(broadPhaseMaxZ);

        for (int x = minBlockX; x <= maxBlockX; x++) {
            for (int y = minBlockY; y <= maxBlockY; y++) {
                for (int z = minBlockZ; z <= maxBlockZ; z++) {
                    final BlockVec blockPoint = new BlockVec(x, y, z);
                    final Block block = instance.getBlock(blockPoint, Block.Getter.Condition.TYPE);

                    // this block will not have any possibility of a collision
                    if (block == null || block.id() == Block.AIR.id()) continue;

                    // check for the possibility of a collision
                    final Shape blockCollisionShape = block.collisionShape();
                    if (blockCollisionShape.relativeStart() == Vec.ZERO
                            && blockCollisionShape.relativeEnd() == Vec.ZERO)
                        continue;

                    // check if the entity's bounding box collides with this block
                    final boolean hit = blockCollisionShape.intersectBoxSwept(
                            currentPoint,
                            newPoint.sub(currentPoint),
                            blockPoint,
                            boundingBox,
                            result
                    );

                    // if there is a collision, then just return true
                    if (hit) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}