package com.securetrade;

import net.minecraft.inventory.SimpleInventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public final class TradeInventoryCapacity {
    private TradeInventoryCapacity() {
    }

    public static boolean canFit(PlayerInventory inventory, SimpleInventory incoming) {
        List<ItemStack> currentSlots = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            currentSlots.add(inventory.getStack(slot).copy());
        }
        return canFit(currentSlots, incoming);
    }

    static boolean canFit(List<ItemStack> currentSlots, SimpleInventory incoming) {
        List<StackUnit<ItemStack>> inventoryUnits = new ArrayList<>(currentSlots.size());
        for (ItemStack stack : currentSlots) {
            inventoryUnits.add(toUnit(stack));
        }

        List<StackUnit<ItemStack>> incomingUnits = new ArrayList<>(incoming.size());
        for (int slot = 0; slot < incoming.size(); slot++) {
            incomingUnits.add(toUnit(incoming.getStack(slot)));
        }
        return canFit(inventoryUnits, incomingUnits,
                (left, right) -> ItemStack.areItemsEqual(left, right) && ItemStack.areTagsEqual(left, right));
    }

    static <T> boolean canFit(
            List<StackUnit<T>> currentSlots,
            List<StackUnit<T>> incoming,
            BiPredicate<T, T> matches
    ) {
        List<StackUnit<T>> simulated = new ArrayList<>(currentSlots.size());
        for (StackUnit<T> slot : currentSlots) {
            simulated.add(slot.copy());
        }

        for (StackUnit<T> offered : incoming) {
            int remaining = offered.count;
            if (remaining <= 0 || offered.key == null) {
                continue;
            }

            for (StackUnit<T> existing : simulated) {
                if (remaining <= 0) {
                    break;
                }
                if (existing.key != null && matches.test(existing.key, offered.key)) {
                    int moved = Math.min(existing.maxCount - existing.count, remaining);
                    if (moved > 0) {
                        existing.count += moved;
                        remaining -= moved;
                    }
                }
            }

            for (StackUnit<T> emptySlot : simulated) {
                if (remaining <= 0) {
                    break;
                }
                if (emptySlot.key == null || emptySlot.count <= 0) {
                    int moved = Math.min(offered.maxCount, remaining);
                    emptySlot.key = offered.key;
                    emptySlot.count = moved;
                    emptySlot.maxCount = offered.maxCount;
                    remaining -= moved;
                }
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static StackUnit<ItemStack> toUnit(ItemStack stack) {
        if (stack.isEmpty()) {
            return new StackUnit<>(null, 0, 0);
        }
        ItemStack key = stack.copy();
        key.setCount(1);
        return new StackUnit<>(key, stack.getCount(), stack.getMaxCount());
    }

    static final class StackUnit<T> {
        private T key;
        private int count;
        private int maxCount;

        StackUnit(T key, int count, int maxCount) {
            this.key = key;
            this.count = count;
            this.maxCount = maxCount;
        }

        private StackUnit<T> copy() {
            return new StackUnit<>(key, count, maxCount);
        }
    }
}
