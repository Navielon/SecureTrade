package com.securetrade;

import com.securetrade.platform.Services;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

public final class TradeItemValidator {
    private static final int MAX_NESTED_DEPTH = 8;
    public static final TagKey<Item> UNTRADEABLE_TAG = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath("securetrade", "untradeable")
    );
    private static final Set<String> CONTAINER_ACCESSORS = Set.of(
            "items", "getItems", "stacks", "getStacks",
            "contents", "getContents", "inventory", "getInventory"
    );

    private TradeItemValidator() {
    }

    public static boolean containsBlacklistedItem(ItemStack stack) {
        List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null) blacklist = List.of();
        return containsBlacklistedItem(stack, blacklist, 0);
    }

    public static boolean containsBlacklistedItems(SimpleContainer container) {
        List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null) blacklist = List.of();

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (containsBlacklistedItem(container.getItem(i), blacklist, 0)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsBlacklistedItem(ItemStack stack, List<String> blacklist, int depth) {
        if (stack.isEmpty()) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.is(UNTRADEABLE_TAG) || blacklist.contains(itemId)) {
            return true;
        }

        if (depth >= MAX_NESTED_DEPTH) {
            return true;
        }

        ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
        if (containerContents != null) {
            for (ItemStackTemplate nestedTemplate : containerContents.nonEmptyItems()) {
                ItemStack nestedStack = nestedTemplate.create();
                if (containsBlacklistedItem(nestedStack, blacklist, depth + 1)) {
                    return true;
                }
            }
        }

        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            for (ItemStackTemplate nestedTemplate : bundleContents.items()) {
                ItemStack nestedStack = nestedTemplate.create();
                if (containsBlacklistedItem(nestedStack, blacklist, depth + 1)) {
                    return true;
                }
            }
        }

        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        for (TypedDataComponent<?> component : stack.getComponents()) {
            if (component.type() == DataComponents.CONTAINER || component.type() == DataComponents.BUNDLE_CONTENTS) {
                continue;
            }
            if (containsComponentItems(component.value(), blacklist, depth + 1, visited)) {
                return true;
            }
        }

        if (Services.PLATFORM.containsPlatformContainerItems(stack, blacklist, depth + 1)) {
            return true;
        }

        return false;
    }

    private static boolean containsComponentItems(
            Object value,
            List<String> blacklist,
            int depth,
            IdentityHashMap<Object, Boolean> visited
    ) {
        if (value == null || depth > MAX_NESTED_DEPTH || visited.put(value, Boolean.TRUE) != null) {
            return false;
        }
        if (value instanceof ItemStack itemStack) {
            return containsBlacklistedItem(itemStack, blacklist, depth);
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() && containsComponentItems(optional.get(), blacklist, depth + 1, visited);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (containsComponentItems(element, blacklist, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object element : map.values()) {
                if (containsComponentItems(element, blacklist, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                if (containsComponentItems(Array.get(value, index), blacklist, depth + 1, visited)) {
                    return true;
                }
            }
            return false;
        }

        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !CONTAINER_ACCESSORS.contains(method.getName())) {
                continue;
            }
            try {
                if (containsComponentItems(method.invoke(value), blacklist, depth + 1, visited)) {
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
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
