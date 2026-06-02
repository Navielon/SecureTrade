package com.securetrade;

import com.securetrade.platform.Services;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class TradeItemValidator {
    private static final int MAX_NESTED_DEPTH = 8;

    private TradeItemValidator() {
    }

    public static boolean containsBlacklistedItem(ItemStack stack) {
        List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }
        return containsBlacklistedItem(stack, blacklist, 0);
    }

    public static boolean containsBlacklistedItems(SimpleContainer container) {
        List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (containsBlacklistedItem(container.getItem(i), blacklist, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBlacklistedItem(ItemStack stack, List<String> blacklist, int depth) {
        if (stack.isEmpty()) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (blacklist.contains(itemId)) {
            return true;
        }

        if (depth >= MAX_NESTED_DEPTH) {
            return true;
        }

        ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
        if (containerContents != null) {
            for (ItemStack nestedStack : containerContents.nonEmptyItems()) {
                if (containsBlacklistedItem(nestedStack, blacklist, depth + 1)) {
                    return true;
                }
            }
        }

        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            for (ItemStack nestedStack : bundleContents.items()) {
                if (containsBlacklistedItem(nestedStack, blacklist, depth + 1)) {
                    return true;
                }
            }
        }

        if (Services.PLATFORM.containsPlatformContainerItems(stack, blacklist, depth + 1)) {
            return true;
        }

        return false;
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
            if (nestedStack instanceof ItemStack itemStack && containsBlacklistedItem(itemStack, blacklist, depth)) {
                return true;
            }
        }
        return false;
    }
}
