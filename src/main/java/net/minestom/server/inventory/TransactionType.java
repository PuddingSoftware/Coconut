package net.minestom.server.inventory;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.MathUtils;

import java.util.List;
import java.util.Map;

/**
 * Represents a type of transaction that you can apply to an {@link AbstractInventory}.
 */
@FunctionalInterface
public interface TransactionType {

    /**
     * Adds an item to the inventory.
     * Can either take an air slot or be stacked.
     */
    TransactionType ADD = (inventory, itemStack, slotPredicate, slotInformation) -> {
        final Int2ObjectMap<ItemStack> itemChangesMap = new Int2ObjectOpenHashMap<>();

        final int step = slotInformation.step;
        outer:
        for (IntIntPair slotRange : slotInformation.slotRanges) {
            final int start = slotRange.firstInt();
            final int end = slotRange.secondInt();

            // check filled slots (not air)
            for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
                ItemStack inventoryItem = inventory.getItemStack(i);
                if (inventoryItem.isAir()) {
                    continue;
                }

                if (itemStack.isSimilar(inventoryItem)) {
                    final int itemAmount = inventoryItem.amount();
                    final int maxSize = inventoryItem.maxStackSize();
                    if (itemAmount >= maxSize) continue;
                    if (!slotPredicate.test(i, inventoryItem)) {
                        // skip this slot
                        continue;
                    }

                    final int itemStackAmount = itemStack.amount();
                    final int totalAmount = itemStackAmount + itemAmount;
                    if (!MathUtils.isBetween(totalAmount, 0, itemStack.maxStackSize())) {
                        // Slot cannot accept the whole item, reduce amount to 'itemStack'
                        itemChangesMap.put(i, inventoryItem.withAmount(maxSize));
                        itemStack = itemStack.withAmount(totalAmount - maxSize);
                    } else {
                        // Slot can accept the whole item
                        itemChangesMap.put(i, inventoryItem.withAmount(totalAmount));
                        itemStack = ItemStack.AIR;
                        break outer;
                    }
                }
            }
        }

        outer:
        for (IntIntPair slotRange : slotInformation.slotRanges) {
            final int start = slotRange.firstInt();
            final int end = slotRange.secondInt();

            // check air slots to fill
            for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
                ItemStack inventoryItem = inventory.getItemStack(i);
                if (!inventoryItem.isAir()) continue;
                if (!slotPredicate.test(i, inventoryItem)) {
                    // skip this slot
                    continue;
                }

                final int maxSize = itemStack.maxStackSize();
                final int currentSize = itemStack.amount();

                if (!MathUtils.isBetween(currentSize, 0, maxSize)) {
                    // Slot cannot accept the whole item, reduce amount to 'itemStack'
                    itemChangesMap.put(i, itemStack.withAmount(maxSize));
                    itemStack = itemStack.withAmount(currentSize - maxSize);
                } else {
                    // Slot can accept the whole item
                    itemChangesMap.put(i, itemStack.withAmount(currentSize));
                    itemStack = ItemStack.AIR;
                    break outer;
                }
            }
        }

        return Pair.of(itemStack, itemChangesMap);
    };

    /**
     * Takes an item from the inventory.
     * Can either transform items to air or reduce their amount.
     */
    TransactionType TAKE = (inventory, itemStack, slotPredicate, slotInformation) -> {
        final Int2ObjectMap<ItemStack> itemChangesMap = new Int2ObjectOpenHashMap<>();

        final int step = slotInformation.step;
        outer:
        for (IntIntPair slotRange : slotInformation.slotRanges) {
            final int start = slotRange.firstInt();
            final int end = slotRange.secondInt();

            for (int i = start; step > 0 ? i <= end : i >= end; i += step) {
                final ItemStack inventoryItem = inventory.getItemStack(i);
                if (inventoryItem.isAir()) continue;
                if (itemStack.isSimilar(inventoryItem)) {
                    if (!slotPredicate.test(i, inventoryItem)) {
                        // skip this slot
                        continue;
                    }

                    final int inventoryItemAmount = inventoryItem.amount();
                    final int itemStackAmount = itemStack.amount();
                    if (itemStackAmount < inventoryItemAmount) {
                        itemChangesMap.put(i, inventoryItem.withAmount(inventoryItemAmount - itemStackAmount));
                        itemStack = ItemStack.AIR;
                        break outer;
                    }

                    itemChangesMap.put(i, ItemStack.AIR);
                    itemStack = itemStack.withAmount(itemStackAmount - inventoryItemAmount);
                    if (itemStack.amount() == 0) {
                        itemStack = ItemStack.AIR;
                        break outer;
                    }
                }
            }
        }

        return Pair.of(itemStack, itemChangesMap);
    };

    Pair<ItemStack, Map<Integer, ItemStack>> process(AbstractInventory inventory,
                                                     ItemStack itemStack,
                                                     SlotPredicate slotPredicate,
                                                     SlotInformation slotInformation);

    default Pair<ItemStack, Map<Integer, ItemStack>> process(AbstractInventory inventory,
                                                             ItemStack itemStack,
                                                             SlotPredicate slotPredicate) {
        return process(inventory, itemStack, slotPredicate, new SlotInformation(0, inventory.getInnerSize() - 1, 1));
    }

    default Pair<ItemStack, Map<Integer, ItemStack>> process(AbstractInventory inventory,
                                                             ItemStack itemStack) {
        return process(inventory, itemStack, (_, _) -> true);
    }

    @FunctionalInterface
    interface SlotPredicate {

        boolean test(int slot, ItemStack itemStack);
    }

    record SlotInformation(List<IntIntPair> slotRanges, int step) {

        public SlotInformation(int start,
                               int end,
                               int step) {
            this(List.of(IntIntPair.of(start, end)), step);
        }

        public SlotInformation(List<IntIntPair> slotRanges,
                               int step) {
            this.slotRanges = List.copyOf(slotRanges);
            this.step = step;
        }

        @Override
        public List<IntIntPair> slotRanges() {
            return List.copyOf(slotRanges);
        }
    }
}