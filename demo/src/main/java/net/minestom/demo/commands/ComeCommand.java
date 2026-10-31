package net.minestom.demo.commands;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityMob;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Instance;
import net.minestom.server.pathfinding.data.Path;
import net.minestom.server.pathfinding.utils.PathUtils;

public class ComeCommand extends Command {

    public ComeCommand() {
        super("come");
        setCondition(Conditions::playerOnly);

        setDefaultExecutor((sender, _) -> {
            final Player player = (Player) sender;
            final Instance currentInstance = player.getInstance();

            int foundAmount = 0;
            int bestEffortAmount = 0;
            long startTime = System.currentTimeMillis();
            for (Entity entity : currentInstance.getEntities()) {
                if (!(entity instanceof EntityMob entityMob))
                    continue;

                entityMob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2);

                final Path path = entityMob.findPath(
                        //new Pos(368, 95, 7),
                        player.getPosition(),
                        1,
                        () -> {
                            player.sendMessage("I have finished my path!");
                        },
                        () -> {
                            player.sendMessage("I tried to finish my path...");
                        }
                ).join();

                if (path.state() == Path.State.FAILED) {
//                    System.out.println("Could not compute path.");
                    continue;
                }

//                System.out.println("Path Size: " + path.points().size());

                if (path.state() == Path.State.FOUND) {
                    foundAmount++;
                }

                if (path.state() == Path.State.BEST_EFFORT) {
                    bestEffortAmount++;
                }

                PathUtils.drawPath(entityMob, path);
            }
            long endTime = System.currentTimeMillis();

            System.out.println("Took " + (endTime - startTime) + "ms to find " + foundAmount + " paths and partly find " + bestEffortAmount + " paths.");
        });
    }
}