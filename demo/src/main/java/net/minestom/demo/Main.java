package net.minestom.demo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.demo.commands.AroundCommand;
import net.minestom.demo.commands.AttributeCommand;
import net.minestom.demo.commands.AutoViewCommand;
import net.minestom.demo.commands.BelowNameCommand;
import net.minestom.demo.commands.BookCommand;
import net.minestom.demo.commands.ComeCommand;
import net.minestom.demo.commands.ConfigCommand;
import net.minestom.demo.commands.CookieCommand;
import net.minestom.demo.commands.DebugGridCommand;
import net.minestom.demo.commands.DimensionCommand;
import net.minestom.demo.commands.DisplayCommand;
import net.minestom.demo.commands.EchoCommand;
import net.minestom.demo.commands.EntitySelectorCommand;
import net.minestom.demo.commands.ExecuteCommand;
import net.minestom.demo.commands.FindCommand;
import net.minestom.demo.commands.GamemodeCommand;
import net.minestom.demo.commands.GiveCommand;
import net.minestom.demo.commands.HealthCommand;
import net.minestom.demo.commands.HeightmapCommand;
import net.minestom.demo.commands.HorseCommand;
import net.minestom.demo.commands.InventoryCommand;
import net.minestom.demo.commands.KillCommand;
import net.minestom.demo.commands.LegacyCommand;
import net.minestom.demo.commands.MinecartCommand;
import net.minestom.demo.commands.NotificationCommand;
import net.minestom.demo.commands.PlayersCommand;
import net.minestom.demo.commands.PotionCommand;
import net.minestom.demo.commands.PrimedTNTCommand;
import net.minestom.demo.commands.RedirectTestCommand;
import net.minestom.demo.commands.RelightCommand;
import net.minestom.demo.commands.RemoveCommand;
import net.minestom.demo.commands.SaveCommand;
import net.minestom.demo.commands.SetBlockCommand;
import net.minestom.demo.commands.SetEntityType;
import net.minestom.demo.commands.ShootCommand;
import net.minestom.demo.commands.ShutdownCommand;
import net.minestom.demo.commands.SidebarCommand;
import net.minestom.demo.commands.SleepCommand;
import net.minestom.demo.commands.SummonCommand;
import net.minestom.demo.commands.SummonRandomCommand;
import net.minestom.demo.commands.SweptIntersectionCommand;
import net.minestom.demo.commands.TeleportCommand;
import net.minestom.demo.commands.TestBiomeAmbientParticleCommand;
import net.minestom.demo.commands.TestCommand;
import net.minestom.demo.commands.TestCommand2;
import net.minestom.demo.commands.TestInstabreakCommand;
import net.minestom.demo.commands.TitleCommand;
import net.minestom.demo.commands.TransferCommand;
import net.minestom.demo.commands.WeatherCommand;
import net.minestom.demo.commands.WorldBorderCommand;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.instance.block.BlockHandler;

import java.util.function.Supplier;

public class Main {

    static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init(new Auth.Online());

        CommandManager commandManager = MinecraftServer.getCommandManager();
        commandManager.register(new AroundCommand());
        commandManager.register(new ComeCommand());
        commandManager.register(new TestCommand());
        commandManager.register(new EntitySelectorCommand());
        commandManager.register(new HealthCommand());
        commandManager.register(new LegacyCommand());
        commandManager.register(new DimensionCommand());
        commandManager.register(new ShutdownCommand());
        commandManager.register(new TeleportCommand());
        commandManager.register(new PlayersCommand());
        commandManager.register(new FindCommand());
        commandManager.register(new TitleCommand());
        commandManager.register(new BookCommand());
        commandManager.register(new ShootCommand());
        commandManager.register(new HorseCommand());
        commandManager.register(new EchoCommand());
        commandManager.register(new SummonCommand());
        commandManager.register(new SummonRandomCommand());
        commandManager.register(new RemoveCommand());
        commandManager.register(new GiveCommand());
        commandManager.register(new SetBlockCommand());
        commandManager.register(new AutoViewCommand());
        commandManager.register(new SaveCommand());
        commandManager.register(new GamemodeCommand());
        commandManager.register(new ExecuteCommand());
        commandManager.register(new RedirectTestCommand());
        commandManager.register(new DebugGridCommand());
        commandManager.register(new DisplayCommand());
        commandManager.register(new NotificationCommand());
        commandManager.register(new TestCommand2());
        commandManager.register(new ConfigCommand());
        commandManager.register(new SidebarCommand());
        commandManager.register(new SetEntityType());
        commandManager.register(new RelightCommand());
        commandManager.register(new KillCommand());
        commandManager.register(new WeatherCommand());
        commandManager.register(new PotionCommand());
        commandManager.register(new CookieCommand());
        commandManager.register(new WorldBorderCommand());
        commandManager.register(new TransferCommand());
        commandManager.register(new TestInstabreakCommand());
        commandManager.register(new AttributeCommand());
        commandManager.register(new PrimedTNTCommand());
        commandManager.register(new SleepCommand());
        commandManager.register(new MinecartCommand());
        commandManager.register(new BelowNameCommand());
        commandManager.register(new InventoryCommand());
        commandManager.register(new SweptIntersectionCommand());
        commandManager.register(new HeightmapCommand());
        commandManager.register(new TestBiomeAmbientParticleCommand());

        MinecraftServer.getBlockManager().registerHandler("minecraft:stone", new Supplier<BlockHandler>() {
            @Override
            public BlockHandler get() {
                return new BlockHandler() {

                    @Override
                    public boolean onInteract(Interaction interaction) {
                        System.out.println("Interacted with block!");
                        return true;
                    }

                    @Override
                    public Key getKey() {
                        return Key.key("minecraft", "stone");
                    }
                };
            }
        });

        commandManager.setUnknownCommandCallback((sender, _) -> sender.sendMessage(Component.text("Unknown command", NamedTextColor.RED)));

        new PlayerInit().init();

        minecraftServer.start("0.0.0.0", 25565);
    }
}
