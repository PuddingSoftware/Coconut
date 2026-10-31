package net.minestom.server.pathfinding.utils;

import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.pathfinding.data.Path;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PathUtils {

    private static final Map<UUID, Task> ACTIVE_TASKS = new HashMap<>();

//    private static final Map<Node.Type, RGBLike> NODE_COLORS = new EnumMap<>(Node.Type.class);
//
//    static {
//        NODE_COLORS.put(Node.Type.EMPTY, NamedTextColor.GREEN); // walk node
//        NODE_COLORS.put(Node.Type.STEP, NamedTextColor.GOLD);
//        NODE_COLORS.put(Node.Type.JUMP, NamedTextColor.RED);
//    }

    private PathUtils() {
    }

    public static void drawPath(@NotNull EntityMob mob,
                                @NotNull Path path) {
        ACTIVE_TASKS.compute(mob.getUuid(), (_, task) -> {
            if (task != null) {
                // there is already an active draw path task, cancel the previous one
                task.cancel();
            }

            return scheduleDrawPathTask(path);
        });
    }

    @NotNull
    private static Task scheduleDrawPathTask(@NotNull Path path) {
        return MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            for (Point point : path.points()) {
                final ParticlePacket particlePacket = new ParticlePacket(
                        Particle.DUST.withColor(NamedTextColor.GREEN),
                        point.centerBlockX(), point.centerBlockY(), point.centerBlockZ(),
                        0, 0, 0, 0,
                        1
                );

                MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> {
                    player.sendPacket(particlePacket);
                });
            }
        }, TaskSchedule.tick(2), TaskSchedule.tick(2));
    }
}