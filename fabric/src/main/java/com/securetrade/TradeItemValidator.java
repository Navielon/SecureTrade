package com.securetrade;

import com.securetrade.platform.Services;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.tag.Tag;
import net.minecraft.item.Item;
import net.fabricmc.fabric.api.tag.TagRegistry;
import net.minecraft.inventory.Inventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;

public final class TradeItemValidator {
    private static final int MAX_NESTED_DEPTH = 8;
    public static final Tag<Item> UNTRADEABLE_TAG =
            TagRegistry.item(new Identifier("securetrade", "untradeable"));

    private TradeItemValidator() {
    }

    public static boolean containsBlacklistedItem(ItemStack stack) {
        List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null) blacklist = java.util.Collections.emptyList();
        return containsBlacklistedItem(stack, blacklist, 0);
    }

    public static boolean containsBlacklistedItems(Inventory container) {
        List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null) blacklist = java.util.Collections.emptyList();

        for (int i = 0; i < container.size(); i++) {
            if (containsBlacklistedItem(container.getStack(i), blacklist, 0)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsBlacklistedItem(ItemStack stack, List<String> blacklist, int depth) {
        if (stack.isEmpty()) {
            return false;
        }

        String itemId = Registry.ITEM.getId(stack.getItem()).toString();
        if (UNTRADEABLE_TAG.contains(stack.getItem()) || blacklist.contains(itemId)) {
            return true;
        }

        if (depth >= MAX_NESTED_DEPTH) {
            return true;
        }

        NbtCompound tag = stack.getTag();
        if (tag != null && containsNestedNbtItems(tag, blacklist, depth + 1)) {
            return true;
        }

        if (Services.PLATFORM.containsPlatformContainerItems(stack, blacklist, depth + 1)) {
            return true;
        }

        if (containsSophisticatedBackpackItems(stack, blacklist, depth + 1)) {
            return true;
        }

        return false;
    }

    private static boolean containsNestedNbtItems(NbtElement tag, List<String> blacklist, int depth) {
        if (tag == null || depth > MAX_NESTED_DEPTH) {
            return false;
        }
        if (tag instanceof NbtCompound) {
            NbtCompound compound = (NbtCompound) tag;
            if (compound.contains("id", 8) && compound.contains("Count", 99)) {
                ItemStack nestedStack = ItemStack.fromNbt(compound);
                if (!nestedStack.isEmpty() && containsBlacklistedItem(nestedStack, blacklist, depth)) {
                    return true;
                }
            }
            for (String key : compound.getKeys()) {
                if (containsNestedNbtItems(compound.get(key), blacklist, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (tag instanceof NbtList) {
            NbtList list = (NbtList) tag;
            for (int index = 0; index < list.size(); index++) {
                if (containsNestedNbtItems(list.get(index), blacklist, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsSophisticatedBackpackItems(ItemStack stack, List<String> blacklist, int depth) {
        try {
            Class<?> wrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            Method fromExistingData = wrapperClass.getMethod("fromExistingData", ItemStack.class);
            Object optionalWrapper = fromExistingData.invoke(null, stack);
            if (!(optionalWrapper instanceof Optional)) {
                return false;
            }

            Optional<?> optional = (Optional<?>) optionalWrapper;
            if (!optional.isPresent()) {
                return false;
            }
            Object wrapper = optional.get();
            Method getInventoryHandler = wrapper.getClass().getMethod("getInventoryHandler");
            Object handler = getInventoryHandler.invoke(wrapper);
            return containsHandlerItems(handler, blacklist, depth);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean containsHandlerItems(Object handler, List<String> blacklist, int depth) throws ReflectiveOperationException {
        if (handler == null) {
            return false;
        }

        Method getSlots = handler.getClass().getMethod("getSlots");
        Method getStackInSlot = handler.getClass().getMethod("getStackInSlot", int.class);
        int slots = (int) getSlots.invoke(handler);
        for (int i = 0; i < slots; i++) {
            Object nestedStack = getStackInSlot.invoke(handler, i);
            if (nestedStack instanceof ItemStack) {
                ItemStack itemStack = (ItemStack) nestedStack;
                if (containsBlacklistedItem(itemStack, blacklist, depth)) {
                    return true;
                }
            }
        }
        return false;
    }
}
