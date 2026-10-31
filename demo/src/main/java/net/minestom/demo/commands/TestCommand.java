package net.minestom.demo.commands;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;

public class TestCommand extends Command {

    public TestCommand() {
        super("testcmd");

        setDefaultExecutor((sender, _) -> {
            final Player player = (Player) sender;

            final Instance instance = player.getInstance();
            final Pos position = player.getPosition();

            final Entity entity = new Entity(EntityType.ZOMBIE);
            entity.hide();
            entity.setInstance(instance, position).join();

            final Entity spacerEntity = new Entity(EntityType.CHICKEN);
            spacerEntity.setInstance(instance, position).join();
            Entity currentLine = spacerEntity;
            for (int i = 0; i < 5; i++) {
                final Entity nametagEntity = new Entity(EntityType.PIG);
                currentLine.addPassenger(nametagEntity);
                currentLine = nametagEntity;
            }

            MinecraftServer.getSchedulerManager().buildTask(() -> {
                System.out.println("------------------");
                System.out.println("------------------");
                System.out.println("------------------");
                System.out.println("------------------");
                entity.addPassenger(spacerEntity);
            }).delay(TaskSchedule.seconds(5)).schedule();

            MinecraftServer.getSchedulerManager().buildTask(() -> {
                entity.show();
            }).delay(TaskSchedule.seconds(10)).schedule();
        });
    }
}