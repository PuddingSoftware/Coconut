package net.minestom.server.entity.old.ai.sensor;

import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Conservative ground-mob checks. Never loads chunks while sensing.
 */
public final class Terrain {
    public @Nullable Block block(EntityMob mob, Point point) {
        var instance = mob.getInstance();
        if (instance == null || !ChunkUtils.isLoaded(instance.getChunkAt(point))) return null;
        return instance.getBlock(point, Block.Getter.Condition.TYPE);
    }

    public boolean hazardous(Block block) {
        return block.compare(Block.LAVA) || block.compare(Block.FIRE) || block.compare(Block.SOUL_FIRE)
                || block.compare(Block.CACTUS) || block.compare(Block.MAGMA_BLOCK)
                || block.compare(Block.CAMPFIRE) || block.compare(Block.SOUL_CAMPFIRE)
                || block.compare(Block.SWEET_BERRY_BUSH) || block.compare(Block.POWDER_SNOW);
    }

    public boolean canOccupy(EntityMob mob, Point point) {
        var instance = mob.getInstance();
        if (instance == null || !instance.getWorldBorder().inBounds(point)) return false;
        var blocks = mob.getBoundingBox().getBlocks(point);
        while (blocks.hasNext()) {
            var position = blocks.next();
            Block block = block(mob, new Vec(position.x(), position.y(), position.z()));
            if (block == null || hazardous(block) || block.compare(Block.WATER)) return false;
        }
        return CollisionUtils.canFit(mob.getBoundingBox(), instance, point);
    }

    public boolean safe(EntityMob mob, Point point) {
        Block support = block(mob, point.sub(0, 0.05, 0));
        return support != null && support.blocksMotion() && !hazardous(support) && canOccupy(mob, point)
                && support.collisionShape().intersectBox(point.sub(point.blockX(), supportY(point), point.blockZ())
                .sub(0, 0.05, 0), mob.getBoundingBox());
    }

    private int supportY(Point point) {
        return (int) Math.floor(point.y() - 0.05);
    }

    public @Nullable Vec ground(EntityMob mob, double x, double y, double z) {
        // The pathfinder still proves accessibility; this only rejects obviously unusable destinations.
        for (int offset : new int[]{0, 1, -1, 2, -2, 3, -3}) {
            Vec candidate = new Vec(Math.floor(x) + 0.5, Math.floor(y) + offset, Math.floor(z) + 0.5);
            if (safe(mob, candidate)) return candidate;
            Block support = block(mob, candidate.sub(0, 1, 0));
            if (support != null && support.blocksMotion()) {
                Vec surface = candidate.withY(candidate.y() - 1 + support.collisionShape().relativeEnd().y());
                if (safe(mob, surface)) return surface;
            }
        }
        return null;
    }
}
