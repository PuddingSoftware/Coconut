package net.minestom.demo.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;

public class InventoryCommand extends Command {

    public InventoryCommand() {
        super("inventory");

        setCondition(Conditions::playerOnly);

        setDefaultExecutor((sender, _) -> {
            sender.sendMessage(Component.text("Usage: /inventory <type>", NamedTextColor.RED));
        });

        addSyntax((sender, context) -> {
            final Player player = (Player) sender;
            final InventoryType inventoryType = context.get("type");
            player.openInventory(new Inventory(inventoryType, Component.text("Testing")));
        }, ArgumentType.Enum("type", InventoryType.class));
    }
}