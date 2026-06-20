package com.securetrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.securetrade.platform.Services;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Formatting;
import net.minecraft.util.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TradeHistoryManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class TradeEntry {
        public long timestamp;
        public String senderName;
        public String senderUuid;
        public String targetName;
        public String targetUuid;
        public List<ItemInfo> senderItems;
        public List<ItemInfo> targetItems;
        public long senderXP;
        public long targetXP;
    }

    public static class ItemInfo {
        public String id;
        public int count;
        public String displayName;
        public String stackData;

        public ItemInfo(String id, int count, String displayName, String stackData) {
            this.id = id;
            this.count = count;
            this.displayName = displayName;
            this.stackData = stackData;
        }
    }

    public static void recordTrade(ServerPlayerEntity p1, ServerPlayerEntity p2, net.minecraft.inventory.SimpleInventory inv1, net.minecraft.inventory.SimpleInventory inv2, long p1XP, long p2XP) {
        try {
            MinecraftServer server = p1.getServer();
            if (server == null) return;
            
            Path historyFile = server.getSavePath(WorldSavePath.ROOT).resolve("securetrade-history.json");
            List<TradeEntry> history = loadHistory(historyFile);

            TradeEntry entry = new TradeEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.senderName = p1.getEntityName();
            entry.senderUuid = p1.getUuid().toString();
            entry.targetName = p2.getEntityName();
            entry.targetUuid = p2.getUuid().toString();
            entry.senderItems = getItemsList(inv1);
            entry.targetItems = getItemsList(inv2);
            entry.senderXP = p1XP;
            entry.targetXP = p2XP;

            history.add(0, entry); // Add to the beginning of the list

            int limit = Math.max(100, Services.PLATFORM.getMaxHistoryEntries() * 10);
            while (history.size() > limit) {
                history.remove(history.size() - 1);
            }

            saveHistory(historyFile, history);
        } catch (Exception e) {
            TradeLogger.log("Failed to record trade history: " + e.getMessage());
        }
    }

    private static List<ItemInfo> getItemsList(net.minecraft.inventory.SimpleInventory container) {
        List<ItemInfo> list = new ArrayList<>();
        for (int i = 0; i < container.size(); i++) {
            ItemStack stack = container.getStack(i);
            if (!stack.isEmpty()) {
                String id = Registry.ITEM.getId(stack.getItem()).toString();
                int count = stack.getCount();
                String displayName = stack.getName().getString();
                ItemStack stackCopy = stack.copy();
                stackCopy.setCount(1);
                String stackData = stackCopy.writeNbt(new NbtCompound()).toString();
                list.add(new ItemInfo(id, count, displayName, stackData));
            }
        }
        return list;
    }

    private static List<TradeEntry> loadHistory(Path path) {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            List<TradeEntry> list = GSON.fromJson(reader, new TypeToken<ArrayList<TradeEntry>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            TradeLogger.log("Failed to load trade history: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void saveHistory(Path path, List<TradeEntry> history) {
        try {
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(history, writer);
            }
        } catch (Exception e) {
            TradeLogger.log("Failed to save trade history: " + e.getMessage());
        }
    }

    public static void showHistory(ServerPlayerEntity player) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) return;

            Path historyFile = server.getSavePath(WorldSavePath.ROOT).resolve("securetrade-history.json");
            List<TradeEntry> history = loadHistory(historyFile);

            String playerUuid = player.getUuid().toString();
            List<TradeEntry> playerHistory = new ArrayList<>();
            for (TradeEntry entry : history) {
                if (playerUuid.equals(entry.senderUuid) || playerUuid.equals(entry.targetUuid)) {
                    playerHistory.add(entry);
                }
            }

            int maxEntries = Services.PLATFORM.getMaxHistoryEntries();
            int toShow = Math.min(maxEntries, playerHistory.size());

            if (toShow == 0) {
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.empty").formatted(Formatting.GRAY));
                return;
            }

            TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.title").formatted(Formatting.GOLD, Formatting.BOLD));

            for (int i = 0; i < toShow; i++) {
                TradeEntry entry = playerHistory.get(i);
                
                // Determine other player name
                String otherName = playerUuid.equals(entry.senderUuid) ? entry.targetName : entry.senderName;
                
                // Format entry title, e.g. "1. Trade with Player2:"
                Text otherNameComponent = TradeMessages.text(otherName).formatted(Formatting.AQUA);
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.entry", i + 1, otherNameComponent).formatted(Formatting.GRAY));

                // Determine what was given and received by THIS player
                List<ItemInfo> gaveItems = playerUuid.equals(entry.senderUuid) ? entry.senderItems : entry.targetItems;
                List<ItemInfo> receivedItems = playerUuid.equals(entry.senderUuid) ? entry.targetItems : entry.senderItems;
                long gaveXP = playerUuid.equals(entry.senderUuid) ? entry.senderXP : entry.targetXP;
                long receivedXP = playerUuid.equals(entry.senderUuid) ? entry.targetXP : entry.senderXP;

                Text gaveComponent = formatItemsAndXP(gaveItems, gaveXP);
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.gave", gaveComponent).formatted(Formatting.RED));

                Text receivedComponent = formatItemsAndXP(receivedItems, receivedXP);
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.received", receivedComponent).formatted(Formatting.GREEN));
            }
        } catch (Exception e) {
            TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.error", e.getMessage()).formatted(Formatting.RED));
        }
    }

    private static Text formatItemsAndXP(List<ItemInfo> items, long xp) {
        boolean hasItems = items != null && !items.isEmpty();
        if (!hasItems && xp <= 0) {
            return TradeMessages.trans("securetrade.history.nothing").formatted(Formatting.GRAY);
        }

        MutableText result = TradeMessages.empty();
        boolean hasContent = false;

        if (hasItems) {
            for (ItemInfo item : aggregateItems(items)) {
                if (hasContent) {
                    result.append(TradeMessages.text(", ").formatted(Formatting.GRAY));
                }
                result.append(formatItem(item));
                hasContent = true;
            }
        }

        if (xp > 0) {
            if (hasContent) {
                result.append(TradeMessages.text(", ").formatted(Formatting.GRAY));
            }
            result.append(TradeMessages.trans("securetrade.history.xp_amount", xp).formatted(Formatting.AQUA));
        }

        return result;
    }

    private static List<ItemInfo> aggregateItems(List<ItemInfo> items) {
        Map<String, ItemInfo> aggregated = new LinkedHashMap<>();
        for (ItemInfo item : items) {
            if (item == null || item.id == null) {
                continue;
            }

            String key = getAggregationKey(item);
            ItemInfo existing = aggregated.get(key);
            if (existing == null) {
                aggregated.put(key, new ItemInfo(item.id, item.count, item.displayName, item.stackData));
            } else {
                existing.count = (int) Math.min(Integer.MAX_VALUE, (long) existing.count + item.count);
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    private static String getAggregationKey(ItemInfo item) {
        if (item.stackData != null && !item.stackData.trim().isEmpty()) {
            return item.stackData;
        }
        return item.id + "\u0000" + (item.displayName == null ? "" : item.displayName);
    }

    private static Text formatItem(ItemInfo item) {
        ItemStack hoverStack = resolveItemStack(item);
        MutableText itemName = (hoverStack.isEmpty() ? resolveItemName(item) : hoverStack.getName().copy())
                .formatted(Formatting.YELLOW);
        MutableText result = TradeMessages.empty()
                .append(TradeMessages.text(item.count + "x ").formatted(Formatting.GRAY))
                .append(itemName);
        if (!hoverStack.isEmpty()) {
            return result.styled(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(hoverStack))
            ));
        }

        Text hoverText = TradeMessages.text(item.id + "\n" + item.count + "x").formatted(Formatting.GRAY);
        return result.styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }

    private static MutableText resolveItemName(ItemInfo item) {
        if (item == null || item.id == null || item.id.trim().isEmpty()) {
            return TradeMessages.trans("securetrade.history.unknown_item");
        }

        try {
            Identifier id = new Identifier(item.id);
            Item resolvedItem = Registry.ITEM.get(id);
            if (resolvedItem != null && (resolvedItem != Items.AIR || "minecraft:air".equals(item.id))) {
                return TradeMessages.trans(resolvedItem.getTranslationKey());
            }
        } catch (Exception ignored) {
            // Fall back to the stored legacy display name below.
        }

        if (item.displayName != null && !item.displayName.trim().isEmpty()) {
            return TradeMessages.text(item.displayName);
        }

        return TradeMessages.text(item.id);
    }

    private static ItemStack resolveItemStack(ItemInfo item) {
        if (item == null || item.id == null || item.id.trim().isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (item.stackData != null && !item.stackData.trim().isEmpty()) {
            try {
                return ItemStack.fromNbt(StringNbtReader.parse(item.stackData));
            } catch (Exception ignored) {
            }
        }

        try {
            Identifier id = new Identifier(item.id);
            Item resolvedItem = Registry.ITEM.get(id);
            if (resolvedItem != null && (resolvedItem != Items.AIR || "minecraft:air".equals(item.id))) {
                return new ItemStack(resolvedItem);
            }
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }

}
