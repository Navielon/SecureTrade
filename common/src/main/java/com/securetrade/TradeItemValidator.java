package com.securetrade;

import com.securetrade.platform.Services;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

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

        // 1.20.1: read container contents from NBT (BlockEntityTag.Items for shulkers etc.)
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
            if (blockEntityTag.contains("Items")) {
                ListTag items = blockEntityTag.getList("Items", 10); // 10 = TAG_COMPOUND
                for (int i = 0; i < items.size(); i++) {
                    ItemStack nestedStack = ItemStack.of(items.getCompound(i));
                    if (containsBlacklistedItem(nestedStack, blacklist, depth + 1)) {
                        return true;
                    }
                }
            }
            // Fallback: top-level Items list (some mods/containers)
            if (tag.contains("Items")) {
                ListTag items = tag.getList("Items", 10);
                for (int i = 0; i < items.size(); i++) {
                    ItemStack nestedStack = ItemStack.of(items.getCompound(i));
                    if (containsBlacklistedItem(nestedStack, blacklist, depth + 1)) {
                        return true;
                    }
                }
            }
        }

        if (containsForgeItemHandlerItems(stack, blacklist, depth + 1)) {
            return true;
        }

        if (containsSophisticatedBackpackItems(stack, blacklist, depth + 1)) {
            return true;
        }

        return false;
    }

    private static boolean containsForgeItemHandlerItems(ItemStack stack, List<String> blacklist, int depth) {
        try {
            Class<?> forgeCapabilitiesClass = Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities");
            Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Field itemHandlerField = forgeCapabilitiesClass.getField("ITEM_HANDLER");
            Object itemHandlerCapability = itemHandlerField.get(null);
            Method getCapability = stack.getClass().getMethod("getCapability", capabilityClass);
            Object lazyOptional = getCapability.invoke(stack, itemHandlerCapability);
            Method resolve = lazyOptional.getClass().getMethod("resolve");
            Object optional = resolve.invoke(lazyOptional);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                return false;
            }
            return containsHandlerItems(opt.get(), blacklist, depth);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean containsSophisticatedBackpackItems(ItemStack stack, List<String> blacklist, int depth) {
        try {
            Class<?> wrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            Method fromExistingData = wrapperClass.getMethod("fromExistingData", ItemStack.class);
            Object optionalWrapper = fromExistingData.invoke(null, stack);
            if (!(optionalWrapper instanceof Optional<?> optional) || optional.isEmpty()) {
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

    private static boolean containsHandlerItems(Object handler, List<String> blacklist, int depth) throws ReflectiveOperationException {
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
