package net.minestom.demo.commands;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.heightmap.Heightmap;

public class HeightmapCommand extends Command {

    public HeightmapCommand() {
        super("heightmap");

        setCondition(Conditions::playerOnly);

        setDefaultExecutor((sender, _) -> {
            final Player player = (Player) sender;
            final Pos playerPos = player.getPosition();
            final Instance instance = player.getInstance();

            final int sectionX = playerPos.blockX() & 15;
            final int sectionZ = playerPos.blockZ() & 15;
            final int blockX = playerPos.blockX();
            final int blockZ = playerPos.blockZ();

            final Chunk chunk = player.getChunk();
            if(chunk == null) {
                player.sendMessage("Your chunk is null.");
                return;
            }

//            final Heightmap motionBlockingHeightmap = chunk.motionBlockingHeightmap();
//            final Heightmap worldSurfaceHeightmap = chunk.worldSurfaceHeightmap();
            final Heightmap motionBlockingNoLeavesHeightmap = chunk.motionBlockingNoLeavesHeightmap();

//            final int motionBlockingY = motionBlockingHeightmap.getHeight(sectionX, sectionZ);
//            player.sendMessage("Motion Blocking Height: " + motionBlockingY);
//            instance.setBlock(blockX, motionBlockingY, blockZ, Block.GOLD_BLOCK);
//
//            final int worldSurfaceY = worldSurfaceHeightmap.getHeight(sectionX, sectionZ);
//            player.sendMessage("World Surface Height: " + worldSurfaceY);
//            instance.setBlock(blockX, worldSurfaceY, blockZ, Block.DIAMOND_BLOCK);

            final int motionBlockingNoLeavesY = motionBlockingNoLeavesHeightmap.getHeight(sectionX, sectionZ);
            player.sendMessage("Motion Blocking No Leaves Height: " + motionBlockingNoLeavesY);
            instance.setBlock(blockX, motionBlockingNoLeavesY, blockZ, Block.EMERALD_BLOCK);
        });
    }
}