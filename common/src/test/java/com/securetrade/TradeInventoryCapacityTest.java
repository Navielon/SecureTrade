package com.securetrade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeInventoryCapacityTest {
    @Test
    void acceptsIncomingStackWhenAnEmptySlotExists() {
        List<TradeInventoryCapacity.StackUnit<String>> inventory = filledInventory("dirt", 64);
        inventory.set(8, new TradeInventoryCapacity.StackUnit<>(null, 0, 0));
        List<TradeInventoryCapacity.StackUnit<String>> incoming = List.of(
                new TradeInventoryCapacity.StackUnit<>("diamond", 32, 64)
        );

        assertTrue(TradeInventoryCapacity.canFit(inventory, incoming, String::equals));
    }

    @Test
    void mergesIncomingItemsIntoCompatibleStacks() {
        List<TradeInventoryCapacity.StackUnit<String>> inventory = filledInventory("dirt", 64);
        inventory.set(4, new TradeInventoryCapacity.StackUnit<>("dirt", 63, 64));
        List<TradeInventoryCapacity.StackUnit<String>> incoming = List.of(
                new TradeInventoryCapacity.StackUnit<>("dirt", 1, 64)
        );

        assertTrue(TradeInventoryCapacity.canFit(inventory, incoming, String::equals));
    }

    @Test
    void rejectsOfferWhenNoCompatibleSpaceExists() {
        List<TradeInventoryCapacity.StackUnit<String>> inventory = filledInventory("dirt", 64);
        List<TradeInventoryCapacity.StackUnit<String>> incoming = List.of(
                new TradeInventoryCapacity.StackUnit<>("diamond", 1, 64)
        );

        assertFalse(TradeInventoryCapacity.canFit(inventory, incoming, String::equals));
    }

    private static List<TradeInventoryCapacity.StackUnit<String>> filledInventory(String key, int count) {
        List<TradeInventoryCapacity.StackUnit<String>> inventory = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            inventory.add(new TradeInventoryCapacity.StackUnit<>(key, count, 64));
        }
        return inventory;
    }
}
