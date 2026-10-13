package net.minestom.server.inventory.click;

import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.inventory.InventoryClickEvent;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.TransactionType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.Equippable;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class InventoryClickProcessor {

    public InventoryClickResult leftClick(ItemStack clicked, ItemStack cursor) {
        if (cursor.isSimilar(clicked)) {
            // Try to stack items
            final int totalAmount = cursor.amount() + clicked.amount();
            final int maxSize = cursor.maxStackSize();
            if (!MathUtils.isBetween(totalAmount, 0, clicked.maxStackSize())) {
                // Size is too big, stack as much as possible into clicked
                cursor = cursor.withAmount(totalAmount - maxSize);
                clicked = clicked.withAmount(maxSize);
            } else {
                // Merge cursor item clicked
                cursor = ItemStack.AIR;
                clicked = clicked.withAmount(totalAmount);
            }
        } else {
            // Items are not compatible, swap them
            ItemStack tempItem = clicked;
            clicked = cursor;
            cursor = tempItem;
        }

        return new InventoryClickResult(clicked, cursor);
    }

    public InventoryClickResult rightClick(ItemStack clicked, ItemStack cursor) {
        final InventoryClickResult clickResult = new InventoryClickResult(clicked, cursor);

        if (clicked.isSimilar(cursor)) {
            // Items can be stacked
            final int amount = clicked.amount() + 1;
            if (!MathUtils.isBetween(amount, 0, clicked.maxStackSize())) {
                // Size too large, stop here
                return clickResult;
            } else {
                // Add 1 to clicked
                clickResult.setCursor(cursor.withAmount(operand -> operand - 1));
                clickResult.setClicked(clicked.withAmount(amount));
            }
        } else {
            // Items cannot be stacked
            if (cursor.isAir()) {
                // Take half of clicked
                final int amount = (int) Math.ceil((double) clicked.amount() / 2D);
                clickResult.setCursor(clicked.withAmount(amount));
                clickResult.setClicked(clicked.withAmount(operand -> operand / 2));
            } else {
                if (clicked.isAir()) {
                    // Put 1 to clicked
                    clickResult.setCursor(cursor.withAmount(operand -> operand - 1));
                    clickResult.setClicked(cursor.withAmount(1));
                } else {
                    // Swap items
                    clickResult.setCursor(clicked);
                    clickResult.setClicked(cursor);
                }
            }
        }

        return clickResult;
    }

    public InventoryClickResult changeHeld(ItemStack clicked, ItemStack cursor) {
        // swap the cursor item and the held item
        return new InventoryClickResult(/* clicked= */ cursor, /* cursor= */ clicked);
    }

    public InventoryClickResult shiftClick(AbstractInventory inventory,
                                           AbstractInventory targetInventory,
                                           TransactionType.SlotInformation slotInformation,
                                           Player player,
                                           int slot,
                                           ItemStack clicked,
                                           ItemStack cursor) {
        final InventoryClickResult clickResult = new InventoryClickResult(clicked, cursor);
        if (clicked.isAir())
            return clickResult.cancelled();

        final boolean craftingGridClick = slot >= 36 && slot <= 40;

        // Handle armor and off-hand equippables
        if (inventory instanceof PlayerInventory && targetInventory instanceof PlayerInventory) {
            Equippable equippableComponent = clicked.get(DataComponents.EQUIPPABLE);
            if (equippableComponent != null) {
                final EquipmentSlot equipmentSlot = equippableComponent.slot();
                RegistryTag<EntityType> allowed = equippableComponent.allowedEntities();
                if ((allowed == null || allowed.contains(EntityType.PLAYER.registryKey()))
                        && (equipmentSlot.isArmor() || equipmentSlot == EquipmentSlot.OFF_HAND)
                        && !craftingGridClick) {
                    // Shift-click equip
                    final ItemStack currentItem = player.getEquipment(equipmentSlot);
                    if (currentItem.isAir()) {
                        player.setEquipment(equipmentSlot, clicked);
                        return new InventoryClickResult(ItemStack.AIR, cursor);
                    }
                }
            }
        }

        clickResult.setCancel(true);
        final var pair = TransactionType.ADD.process(targetInventory, clicked, (index, _) -> {
            System.out.println(inventory);
            System.out.println(targetInventory);
            System.out.println(index);
            System.out.println(slot);
            if (inventory == targetInventory && index == slot) {
                // prevent item lose/duplication
                return false;
            }

            clickResult.setCancel(false);
            return true;
        }, slotInformation);

        final ItemStack itemResult = pair.left();
        final Map<Integer, ItemStack> itemChangesMap = pair.right();
        itemChangesMap.forEach((Integer s, ItemStack itemStack) -> {
            targetInventory.setItemStack(s, itemStack);
            callClickEvent(player, targetInventory, s, ClickType.SHIFT_CLICK, itemStack, cursor);
        });

        clickResult.setClicked(itemResult);
        return clickResult;
    }

    public @Nullable ItemStack dragging(Player player, AbstractInventory inventory,
                                        List<Integer> slots, int button, ItemStack cursor) {
        // Drag instruction
        if (button == 2) {
            // End left
            final int slotCount = slots.size();
            final int cursorAmount = cursor.amount();
            if (slotCount > cursorAmount) return null;

            // Should be size of each defined slot (if not full)
            final int slotSize = (int) ((float) cursorAmount / (float) slotCount);
            // Place all waiting drag action
            int finalCursorAmount = cursorAmount;
            for (int slot : slots) {
                final boolean isInWindow = slot < inventory.getSize();
                final var inv = isInWindow ? inventory : player.getInventory();
                final int s = isInWindow ? slot : slot - inventory.getSize();

                ItemStack slotItem = inv.getItemStack(s);
                final int amount = slotItem.amount();
                if (cursor.isSimilar(slotItem)) {
                    if (MathUtils.isBetween(amount + slotSize, 0, slotItem.maxStackSize())) {
                        // Append divided amount to slot
                        slotItem = slotItem.withAmount(a -> a + slotSize);
                        finalCursorAmount -= slotSize;
                    } else {
                        // Amount too big, fill as much as possible
                        final int maxSize = cursor.maxStackSize();
                        final int removedAmount = maxSize - amount;
                        slotItem = slotItem.withAmount(maxSize);
                        finalCursorAmount -= removedAmount;
                    }
                } else if (slotItem.isAir()) {
                    // Slot is empty, add divided amount
                    slotItem = cursor.withAmount(slotSize);
                    finalCursorAmount -= slotSize;
                }
                inv.setItemStack(s, slotItem);
                callClickEvent(player, inv, s, ClickType.LEFT_DRAGGING, slotItem, cursor);
            }
            // Update the cursor
            cursor = cursor.withAmount(finalCursorAmount);
        } else if (button == 6) {
            // End right
            int cursorAmount = cursor.amount();
            if (slots.size() > cursorAmount) return null;
            // Place all waiting drag action
            int finalCursorAmount = cursorAmount;
            for (int slot : slots) {
                final boolean isInWindow = slot < inventory.getSize();
                final var inv = isInWindow ? inventory : player.getInventory();
                final int s = isInWindow ? slot : slot - inventory.getSize();

                ItemStack slotItem = inv.getItemStack(s);
                if (cursor.isSimilar(slotItem)) {
                    // Compatible item in the slot, increment by 1
                    final int amount = slotItem.amount() + 1;
                    if (MathUtils.isBetween(amount, 0, slotItem.maxStackSize())) {
                        slotItem = slotItem.withAmount(amount);
                        finalCursorAmount -= 1;
                    }
                } else if (slotItem.isAir()) {
                    // No item at the slot, place one
                    slotItem = cursor.withAmount(1);
                    finalCursorAmount -= 1;
                }
                inv.setItemStack(s, slotItem);
                callClickEvent(player, inv, s, ClickType.RIGHT_DRAGGING, slotItem, cursor);
            }
            // Update the cursor
            cursor = cursor.withAmount(finalCursorAmount);
        }

        return cursor;
    }

    public InventoryClickResult doubleClick(AbstractInventory clickedInventory,
                                            AbstractInventory inventory,
                                            Player player,
                                            int slot,
                                            ItemStack clicked,
                                            ItemStack cursor) {
        InventoryClickResult clickResult = new InventoryClickResult(clicked, cursor);
        if (cursor.isAir())
            return clickResult.cancelled();

        final int maxSize = cursor.maxStackSize();
        final int cursorSpace = maxSize - cursor.amount();
        if (cursorSpace <= 0) {
            // the cursor item is already full, double clicking will do nothing
            return clickResult;
        }

        ItemStack remain = cursor.withAmount(cursorSpace);
        final var playerInventory = player.getInventory();

        if (inventory == playerInventory) {
            // CASE: the player double-clicked inside their inventory with no other open inventories
            // prioritize the inventory from top-left to bottom-right and then hotbar from left to right
            remain = takeItem(
                    clickedInventory,
                    playerInventory,
                    remain,
                    new TransactionType.SlotInformation(
                            List.of(IntIntPair.of(9, playerInventory.getInnerSize() - 1), IntIntPair.of(0, 8)),
                            1
                    ),
                    player,
                    slot,
                    cursor
            );
        } else {
            // CASE: the player double-clicked inside an inventory with another inventory open
            // prioritize the open inventory from top-left to bottom-right and then the player inventory from top-left to bottom-right and then the hotbar from left to right
            remain = takeItem(
                    clickedInventory,
                    inventory,
                    remain,
                    new TransactionType.SlotInformation(
                            0,
                            inventory.getInnerSize() - 1,
                            1
                    ),
                    player,
                    slot,
                    cursor
            );

            if (!remain.isAir()) {
                remain = takeItem(
                        clickedInventory,
                        playerInventory,
                        remain,
                        new TransactionType.SlotInformation(
                                List.of(IntIntPair.of(9, playerInventory.getInnerSize() - 1), IntIntPair.of(0, 8)),
                                1
                        ),
                        player,
                        slot,
                        cursor
                );
            }
        }

        // Update cursor based on the remaining
        if (remain.isAir()) {
            // Item has been filled
            clickResult.setCursor(cursor.withAmount(maxSize));
        } else {
            final int tookAmount = cursorSpace - remain.amount();
            clickResult.setCursor(cursor.withAmount(cursor.amount() + tookAmount));
        }

        return clickResult;
    }

    private ItemStack takeItem(AbstractInventory clickedInventory,
                               AbstractInventory inventoryToTakeFrom,
                               ItemStack remainingItem,
                               TransactionType.SlotInformation slotInformation,
                               Player player,
                               int slot,
                               ItemStack cursor) {
        ItemStack itemResult = remainingItem;

        for (int pass = 0; pass < 2; pass++) {
            final int finalPass = pass;
            var pair = TransactionType.TAKE.process(inventoryToTakeFrom, itemResult, (index, itemStack) -> {
                // prevent item loss/duplication
                if (index == slot && clickedInventory == inventoryToTakeFrom) {
                    return false;
                }

                // keep vanilla behavior, skip over full-stacsk the first pass and allow full stacks the second pass
                if (finalPass == 0) {
                    return itemStack.amount() != itemStack.maxStackSize();
                }

                return true;
            }, slotInformation);

            itemResult = pair.left();
            var itemChangesMap = pair.right();
            itemChangesMap.forEach((Integer changedSlot, ItemStack newItemStack) -> {
                inventoryToTakeFrom.setItemStack(changedSlot, newItemStack);
                callClickEvent(player, inventoryToTakeFrom, changedSlot, ClickType.DOUBLE_CLICK, newItemStack, cursor);
            });
        }

        return itemResult;
    }

    public InventoryClickResult drop(Player player,
                                     boolean all, int slot, ItemStack clicked, ItemStack cursor) {
        final InventoryClickResult clickResult = new InventoryClickResult(clicked, cursor);

        if (slot == -999) {
            // Click outside
            if (all) {
                // Left (drop all)
                final int amount = cursor.amount();
                final ItemStack dropItem = cursor.withAmount(amount);
                final boolean dropResult = player.dropItem(dropItem);
                clickResult.setCancel(!dropResult);
                if (dropResult) {
                    cursor = ItemStack.AIR;
                }
            } else {
                // Right (drop 1)
                final ItemStack dropItem = cursor.withAmount(1);
                final boolean dropResult = player.dropItem(dropItem);
                clickResult.setCancel(!dropResult);
                if (dropResult) {
                    final int amount = cursor.amount();
                    final int newAmount = amount - 1;
                    cursor = cursor.withAmount(newAmount);
                }
            }

        } else {
            if (all) {
                // Ctrl + Drop key Q (drop all)
                final int amount = clicked.amount();
                final ItemStack dropItem = clicked.withAmount(amount);
                final boolean dropResult = player.dropItem(dropItem);
                clickResult.setCancel(!dropResult);
                if (dropResult) {
                    clicked = ItemStack.AIR;
                }
            } else {
                // Drop key Q (drop 1)
                final ItemStack dropItem = clicked.withAmount(1);
                final boolean dropResult = player.dropItem(dropItem);
                clickResult.setCancel(!dropResult);
                if (dropResult) {
                    final int amount = clicked.amount();
                    final int newAmount = amount - 1;
                    clicked = clicked.withAmount(newAmount);
                }
            }
        }

        clickResult.setClicked(clicked);
        clickResult.setCursor(cursor);

        return clickResult;
    }

    private void callClickEvent(Player player,
                                AbstractInventory inventory,
                                int slot,
                                ClickType clickType,
                                ItemStack clicked,
                                ItemStack cursor) {
        EventDispatcher.call(new InventoryClickEvent(inventory, player, slot, clickType, clicked, cursor));
    }
}