package com.securetrade.escrow;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.securetrade.TradeHistoryManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EscrowItems {
    private EscrowItems() {
    }

    static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /**
     * Vanilla upgrades the player file as a whole, but never looks inside our own keys or the
     * journal, so escrowed items written by an older Minecraft version are upgraded here.
     */
    static CompoundTag upgrade(DataFixer fixer, CompoundTag item, int dataVersion) {
        int current = currentDataVersion();
        if (dataVersion <= 0 || dataVersion >= current) {
            return item;
        }
        return (CompoundTag) fixer.update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, item), dataVersion, current).getValue();
    }

    static List<EscrowStack> fromContainer(Container container) {
        List<EscrowStack> items = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                items.add(new EscrowStack(slot, stack.copy()));
            }
        }
        return items;
    }

    static List<EscrowRecord.StoredItem> encode(List<EscrowStack> items) {
        List<EscrowRecord.StoredItem> stored = new ArrayList<>();
        for (EscrowStack item : items) {
            stored.add(new EscrowRecord.StoredItem(item.slot(), item.stack().save(new CompoundTag()).toString()));
        }
        return stored;
    }

    /**
     * @return the decoded items, or null if any of them can no longer be read
     */
    static List<EscrowStack> decode(DataFixer fixer, List<EscrowRecord.StoredItem> stored, int dataVersion) {
        List<EscrowStack> items = new ArrayList<>();
        for (EscrowRecord.StoredItem item : stored) {
            try {
                ItemStack stack = ItemStack.of(upgrade(fixer, TagParser.parseTag(item.data), dataVersion));
                if (stack.isEmpty()) {
                    return null;
                }
                items.add(new EscrowStack(item.slot, stack));
            } catch (Exception e) {
                return null;
            }
        }
        return items;
    }

    static boolean sameItems(List<EscrowStack> first, List<EscrowStack> second) {
        Map<Integer, ItemStack> bySlot = new HashMap<>();
        for (EscrowStack item : first) {
            if (!item.stack().isEmpty()) {
                bySlot.put(item.slot(), item.stack());
            }
        }
        int matched = 0;
        for (EscrowStack item : second) {
            if (item.stack().isEmpty()) {
                continue;
            }
            ItemStack other = bySlot.get(item.slot());
            if (other == null || !ItemStack.matches(other, item.stack())) {
                return false;
            }
            matched++;
        }
        return matched == bySlot.size();
    }

    static List<TradeHistoryManager.ItemInfo> describe(List<EscrowStack> items) {
        List<TradeHistoryManager.ItemInfo> infos = new ArrayList<>();
        for (EscrowStack item : items) {
            ItemStack stack = item.stack();
            String stackData = stack.copyWithCount(1).save(new CompoundTag()).toString();
            infos.add(new TradeHistoryManager.ItemInfo(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.getCount(),
                    stack.getHoverName().getString(),
                    stackData
            ));
        }
        return infos;
    }
}
