package net.minestom.demo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.dialog.*;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.dialog.Dialog;
import net.minestom.server.dialog.DialogAction;
import net.minestom.server.dialog.DialogActionButton;
import net.minestom.server.dialog.DialogAfterAction;
import net.minestom.server.dialog.DialogBody;
import net.minestom.server.dialog.DialogInput;
import net.minestom.server.dialog.DialogMetadata;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.event.inventory.InventoryClickEvent;
import net.minestom.server.event.inventory.InventoryItemChangeEvent;
import net.minestom.server.event.item.*;
import net.minestom.server.event.player.*;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.item.PickupItemEvent;
import net.minestom.server.event.item.PlayerBeginItemUseEvent;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerCustomClickEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerEditSignEvent;
import net.minestom.server.event.player.PlayerGameModeRequestEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerLeaveBedEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.predicate.BlockPredicate;
import net.minestom.server.instance.block.predicate.ComponentPredicateSet;
import net.minestom.server.instance.block.predicate.DataComponentPredicate;
import net.minestom.server.instance.block.predicate.DataComponentPredicates;
import net.minestom.server.instance.block.predicate.NbtPredicate;
import net.minestom.server.instance.block.predicate.PropertiesPredicate;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.Bee;
import net.minestom.server.item.component.BlockPredicates;
import net.minestom.server.item.component.Consumable;
import net.minestom.server.item.component.TypedCustomData;
import net.minestom.server.monitoring.TickMonitor;
import net.minestom.server.network.packet.server.common.CustomReportDetailsPacket;
import net.minestom.server.network.packet.server.common.ServerLinksPacket;
import net.minestom.server.network.packet.server.play.TrackedWaypointPacket;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.time.TimeUnit;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerInit {

    private static final Instance MAIN_INSTANCE;

    static {
        MAIN_INSTANCE = MinecraftServer.getInstanceManager().createInstanceContainer(new AnvilLoader("worlds/world"));
        MAIN_INSTANCE.setChunkSupplier(LightingChunk::new);

        MAIN_INSTANCE.setTime(12000L);
        MAIN_INSTANCE.setTime(0);
    }

    private static final EventNode<Event> DEMO_NODE = EventNode.all("demo")
            .addListener(EntityAttackEvent.class, event -> {
                final Entity source = event.getEntity();
                final Entity entity = event.getTarget();

                entity.takeKnockback(0.4f, Math.sin(source.getPosition().yaw() * 0.017453292), -Math.cos(source.getPosition().yaw() * 0.017453292));

                if (entity instanceof Player target) {
                    target.damage(Damage.fromEntity(source, 5));
                }

                if (source instanceof Player player) {
                    player.sendMessage("You attacked something!");
                }
            })
            .addListener(PickupItemEvent.class, event -> {
                final Entity entity = event.getLivingEntity();
                if (entity instanceof Player player) {
                    // Cancel event if player does not have enough inventory space
                    final ItemStack itemStack = event.getItemEntity().getItemStack();
                    event.setCancelled(!player.getInventory().addItemStack(itemStack));
                }
            })
            .addListener(ItemDropEvent.class, event -> {
                final Player player = event.getPlayer();
                ItemStack droppedItem = event.getItemStack();

                Pos playerPos = player.getPosition();
                ItemEntity itemEntity = new ItemEntity(droppedItem);
                itemEntity.setPickupDelay(Duration.of(500, TimeUnit.MILLISECOND));
                itemEntity.setInstance(player.getInstance(), playerPos.withY(y -> y + 1.5)).join();
                Vec velocity = playerPos.direction().mul(6);
                itemEntity.setVelocity(velocity);
            })
            .addListener(PlayerDisconnectEvent.class, event -> System.out.println("DISCONNECTION " + event.getPlayer().getUsername()))
            .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                final Player player = event.getPlayer();

                event.setSpawningInstance(MAIN_INSTANCE);

                final Pos citySpawnPoint = new Pos(-100.5, 124, -45.5, -91.0f, 0.6f);
                //final Pos waterSpawnPoint = new Pos(503, 63, -212);
                //final Pos mountainSpawnPoint = new Pos(595, 82, -121);
                player.setRespawnPoint(citySpawnPoint);
            })
            .addListener(PlayerSpawnEvent.class, event -> {
                final Player player = event.getPlayer();
                player.setGameMode(GameMode.CREATIVE);
                player.setPermissionLevel(4);
            })
            .addListener(PlayerGameModeRequestEvent.class, event -> {
                final Player player = event.getPlayer();
                if (player.getPermissionLevel() >= 2) {
                    player.setGameMode(event.getRequestedGameMode());
                }
            })
            .addListener(PlayerCustomClickEvent.class, event -> {
                String payload = "null";
                if (event.getPayload() != null) {
                    try {
                        payload = MinestomAdventure.tagStringIO().asString(event.getPayload());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println(event.getKey() + " -> " + payload);
            })
            .addListener(PlayerPacketOutEvent.class, event -> {
                //System.out.println("out " + event.getPacket().getClass().getSimpleName());
            })
            .addListener(PlayerPacketEvent.class, event -> {
                //System.out.println("in " + event.getPacket().getClass().getSimpleName());
            })
            .addListener(PlayerBlockBreakEvent.class, event -> {
                var instance = event.getInstance();
                var block = event.getBlock();
                var pos = event.getBlockPosition();
                if (block.getProperty("part") == null || block.getProperty("facing") == null) return;
                boolean isHead = "head".equals(block.getProperty("part"));
                var facing = BlockFace.valueOf(block.getProperty("facing").toUpperCase(Locale.ROOT));
                var other = (isHead ? pos.add(facing.getOppositeFace().toDirection().vec().asPos()) : pos.add(facing.toDirection().vec().asPos()));
                var otherBlock = instance.getBlock(other);
                if (otherBlock.id() == block.id()) {
                    instance.setBlock(other, Block.AIR);
                }
            })
            .addListener(PlayerBlockInteractEvent.class, event -> {
                var player = event.getPlayer();
                var instance = event.getInstance();
                var block = event.getBlock();
                if (event.getBlock().key().asMinimalString().endsWith("_bed")) {
                    var pos = event.getBlockPosition();
                    if (block.getProperty("part") == null || block.getProperty("facing") == null) return;
                    boolean isHead = "head".equals(block.getProperty("part"));
                    var facing = BlockFace.valueOf(block.getProperty("facing").toUpperCase(Locale.ROOT));
                    var other = (isHead ? pos.add(facing.getOppositeFace().toDirection().vec().asPos()) : pos.add(facing.toDirection().vec().asPos()));
                    var otherBlock = instance.getBlock(other);
                    if (otherBlock.id() == block.id()) {
                        player.setVelocity(Vec.ZERO);
                        player.swingMainHand();
                        player.enterBed((isHead ? pos : other));
                    }
                }
            })
            .addListener(PlayerLeaveBedEvent.class, event -> {
                var player = event.getPlayer();
                boolean snooze = ThreadLocalRandom.current().nextFloat() < 0.7f;
                if (snooze) {
                    event.setCancelled(true);
                    player.playSound(Sound.sound(SoundEvent.ENTITY_ALLAY_ITEM_THROWN, Sound.Source.PLAYER, 1f, 0.6f));
                    player.sendActionBar(Component.text("I'm too tired to stand up!"));
                } else {
                    player.sendActionBar(Component.empty());
                }
            })
            .addListener(PlayerUseItemOnBlockEvent.class, event -> {
                if (event.getHand() != PlayerHand.MAIN) return;

                var itemStack = event.getItemStack();
                var block = event.getInstance().getBlock(event.getPosition());

                if ("false".equals(block.getProperty("waterlogged")) && itemStack.material().equals(Material.WATER_BUCKET)) {
                    block = block.withProperty("waterlogged", "true");
                } else if ("true".equals(block.getProperty("waterlogged")) && itemStack.material().equals(Material.BUCKET)) {
                    block = block.withProperty("waterlogged", "false");
                } else return;

                event.getInstance().setBlock(event.getPosition(), block);

            })
            .addListener(PlayerBeginItemUseEvent.class, event -> {
                final Player player = event.getPlayer();
                final ItemStack itemStack = event.getItemStack();
                final boolean hasProjectile = !itemStack.get(DataComponents.CHARGED_PROJECTILES, List.of()).isEmpty();
                if (itemStack.material() == Material.CROSSBOW && hasProjectile) {
                    // "shoot" the arrow
                    player.setItemInHand(event.getHand(), itemStack.without(DataComponents.CHARGED_PROJECTILES));
                    event.getPlayer().sendMessage("pew pew!");
                    event.setItemUseDuration(0); // Do not start using the item
                }
            })
            .addListener(PlayerFinishItemUseEvent.class, event -> {
                if (event.getItemStack().material() == Material.APPLE) {
                    event.getPlayer().sendMessage("yummy yummy apple");
                }
            })
            .addListener(PlayerCancelItemUseEvent.class, event -> {
                final Player player = event.getPlayer();
                final ItemStack itemStack = event.getItemStack();
                if (itemStack.material() == Material.CROSSBOW && event.getUseDuration() > 25) {
                    player.setItemInHand(event.getHand(), itemStack.with(DataComponents.CHARGED_PROJECTILES, List.of(ItemStack.of(Material.ARROW))));
                }
            })
            .addListener(PlayerBlockInteractEvent.class, event -> {
                var block = event.getBlock();
                var rawOpenProp = block.getProperty("open");
                if (rawOpenProp != null) {
                    block = block.withProperty("open", String.valueOf(!Boolean.parseBoolean(rawOpenProp)));
                    event.getInstance().setBlock(event.getBlockPosition(), block);
                }

                if (block.id() == Block.CRAFTING_TABLE.id()) {
                    event.getPlayer().openInventory(new Inventory(InventoryType.CRAFTING, "Crafting"));
                }
            })
            .addListener(CreativeInventoryActionEvent.class, event -> {
                if (event.getClickedItem().material() == Material.APPLE) {
                    event.setClickedItem(ItemStack.of(Material.GOLDEN_APPLE, event.getClickedItem().amount()));
                } else if (event.getClickedItem().material() == Material.ENCHANTED_GOLDEN_APPLE) {
                    event.setCancelled(true);
                }
            })
            .addListener(PlayerBlockPlaceEvent.class, event -> {
                Block block = event.getBlock();
                BlockHandler handler = block.handler();
                if (handler != null) return;
                event.setBlock(event.getBlock().withHandler(MinecraftServer.getBlockManager().getHandler(block.key().asString())));
            })
            .addListener(PlayerEditSignEvent.class, event -> {
                event.getLines()
                        .stream()
                        .map(Component::text)
                        .forEach(comp -> event.getPlayer().sendMessage(comp));
            });

    {
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();

        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 40, Block.STONE);

            if (unit.absoluteStart().blockY() < 40 && unit.absoluteEnd().blockY() > 40) {
                unit.modifier().setBlock(unit.absoluteStart().blockX(), 40, unit.absoluteStart().blockZ(), Block.TORCH);
            }
        });
        instanceContainer.setChunkSupplier(LightingChunk::new);

        var defaultClock = instanceContainer.defaultClock();
        defaultClock.rate(4f);
    }

    private final AtomicReference<TickMonitor> LAST_TICK = new AtomicReference<>();

    public void init() {
        var eventHandler = MinecraftServer.getGlobalEventHandler();
        eventHandler.addChild(DEMO_NODE);

        eventHandler.addListener(ServerTickMonitorEvent.class, event -> LAST_TICK.set(event.getTickMonitor()));

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (LAST_TICK.get() == null || MinecraftServer.getConnectionManager().getOnlinePlayerCount() == 0)
                return;

            long ramUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            ramUsage /= 1e6; // bytes to MB

            TickMonitor tickMonitor = LAST_TICK.get();
            final Component header = Component.text("RAM USAGE: " + ramUsage + " MB")
                    .append(Component.newline())
                    .append(Component.text("TICK TIME: " + MathUtils.round(tickMonitor.getTickTime(), 2) + "ms"))
                    .append(Component.newline())
                    .append(Component.text("ACQ TIME: " + MathUtils.round(tickMonitor.getAcquisitionTime(), 2) + "ms"));
            Audiences.players().sendPlayerListHeader(header);
        }).repeat(10, TimeUnit.SERVER_TICK).schedule();
    }
}