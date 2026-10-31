package net.minestom.server.instance.heightmap;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;

public class MotionBlockingNoLeavesHeightmap extends Heightmap {

    public MotionBlockingNoLeavesHeightmap(Chunk attachedChunk) {
        super(attachedChunk);
    }

    @Override
    protected boolean checkBlock(Block block) {
        boolean isLeavesBlock = block.compare(Block.ACACIA_LEAVES)
                || block.compare(Block.AZALEA_LEAVES)
                || block.compare(Block.BIRCH_LEAVES)
                || block.compare(Block.CHERRY_LEAVES)
                || block.compare(Block.JUNGLE_LEAVES)
                || block.compare(Block.DARK_OAK_LEAVES)
                || block.compare(Block.FLOWERING_AZALEA_LEAVES)
                || block.compare(Block.MANGROVE_LEAVES)
                || block.compare(Block.OAK_LEAVES)
                || block.compare(Block.PALE_OAK_LEAVES)
                || block.compare(Block.SPRUCE_LEAVES);

        return (block.solid() && !block.compare(Block.COBWEB) && !block.compare(Block.BAMBOO_SAPLING) && !isLeavesBlock)
                || block.liquid()
                || "true".equals(block.getProperty("waterlogged"));
    }

    @Override
    public Type type() {
        return Type.MOTION_BLOCKING_NO_LEAVES;
    }
}