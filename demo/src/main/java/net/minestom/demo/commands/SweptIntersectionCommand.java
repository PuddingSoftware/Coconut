package net.minestom.demo.commands;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.collision.SweepResult;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.pathfinding.movement.MovementStrategies;
import net.minestom.server.timer.TaskSchedule;

public class SweptIntersectionCommand extends Command {

    public SweptIntersectionCommand() {
        super("sweptintersection");

        setCondition(Conditions::playerOnly);

        setDefaultExecutor((sender, _) -> {
            final Player player = (Player) sender;

            final Instance instance = player.getInstance();
            final Vec playerPosition = new Vec(player.getPosition().centerBlockX(), player.getPosition().y(), player.getPosition().centerBlockZ());
            final BoundingBox playerBoundingBox = player.getBoundingBox();

            for (Vec offset : MovementStrategies.BASIC_AND_DIAGONAL) {
                final Vec neighborPosition = playerPosition.add(offset);
                final SweepResult finalResult = new SweepResult(1, 0, 0, 0, null, 0, 0, 0, 0, 0, 0);

                final double startMinX = playerPosition.x() + playerBoundingBox.minX();
                final double startMinY = playerPosition.y() + playerBoundingBox.minY();
                final double startMinZ = playerPosition.z() + playerBoundingBox.minZ();
                final double startMaxX = playerPosition.x() + playerBoundingBox.maxX();
                final double startMaxY = playerPosition.y() + playerBoundingBox.maxY();
                final double startMaxZ = playerPosition.z() + playerBoundingBox.maxZ();

                final double endMinX = neighborPosition.x() + playerBoundingBox.minX();
                final double endMinY = neighborPosition.y() + playerBoundingBox.minY();
                final double endMinZ = neighborPosition.z() + playerBoundingBox.minZ();
                final double endMaxX = neighborPosition.x() + playerBoundingBox.maxX();
                final double endMaxY = neighborPosition.y() + playerBoundingBox.maxY();
                final double endMaxZ = neighborPosition.z() + playerBoundingBox.maxZ();

                // the union of the start and end bounding box
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

                boolean hitAnything = false;
                outer: for(int x = minBlockX; x <= maxBlockX; x++) {
                    for (int y = minBlockY; y <= maxBlockY; y++) {
                        for (int z = minBlockZ; z <= maxBlockZ; z++) {
                            final BlockVec blockPosition = new BlockVec(x, y, z);
                            final Block block = instance.getBlock(blockPosition, Block.Getter.Condition.TYPE);
                            final Shape blockShape = block.collisionShape();

                            // no block here = no possibility of a collision
                            if (blockShape.relativeStart() == Vec.ZERO && blockShape.relativeEnd() == Vec.ZERO)
                                continue;

                            final boolean hit = blockShape.intersectBoxSwept(
                                    playerPosition,
                                    neighborPosition.sub(playerPosition),
                                    blockPosition,
                                    playerBoundingBox,
                                    finalResult
                            );

                            if (hit) {
                                hitAnything = true;
                                break outer;
                            }
                        }
                    }
                }

                System.out.println(finalResult);
                System.out.println();

                spawnParticle(player, neighborPosition, hitAnything);
            }
        });
    }

    @SuppressWarnings("MethodCanBeStatic")
    private void spawnParticle(Player player,
                               Point point,
                               boolean hit) {
        final ParticlePacket particlePacket = new ParticlePacket(
                Particle.DUST.withColor(hit ? NamedTextColor.RED : NamedTextColor.WHITE),
                point.centerBlockX(),
                point.centerBlockY(),
                point.centerBlockZ(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1
        );

        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            player.sendPacket(particlePacket);
        }, TaskSchedule.tick(2), TaskSchedule.tick(2));
    }
}