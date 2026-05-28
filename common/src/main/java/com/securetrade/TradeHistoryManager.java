package com.securetrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.securetrade.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        public int senderXP;
        public int targetXP;
    }

    public static class ItemInfo {
        public String id;
        public int count;
        public String displayName;

        public ItemInfo(String id, int count, String displayName) {
            this.id = id;
            this.count = count;
            this.displayName = displayName;
        }
    }

    public static void recordTrade(ServerPlayer p1, ServerPlayer p2, net.minecraft.world.SimpleContainer inv1, net.minecraft.world.SimpleContainer inv2, int p1XP, int p2XP) {
        try {
            MinecraftServer server = p1.level().getServer();
            if (server == null) return;
            
            Path historyFile = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-history.json");
            List<TradeEntry> history = loadHistory(historyFile);

            TradeEntry entry = new TradeEntry();
            entry.timestamp = System.currentTimeMillis();
            entry.senderName = p1.getScoreboardName();
            entry.senderUuid = p1.getUUID().toString();
            entry.targetName = p2.getScoreboardName();
            entry.targetUuid = p2.getUUID().toString();
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

    private static List<ItemInfo> getItemsList(net.minecraft.world.SimpleContainer container) {
        List<ItemInfo> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                int count = stack.getCount();
                String displayName = stack.getHoverName().getString();
                list.add(new ItemInfo(id, count, displayName));
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

    public static void showHistory(ServerPlayer player) {
        try {
            MinecraftServer server = player.level().getServer();
            if (server == null) return;

            Path historyFile = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-history.json");
            List<TradeEntry> history = loadHistory(historyFile);

            String playerUuid = player.getUUID().toString();
            List<TradeEntry> playerHistory = new ArrayList<>();
            for (TradeEntry entry : history) {
                if (playerUuid.equals(entry.senderUuid) || playerUuid.equals(entry.targetUuid)) {
                    playerHistory.add(entry);
                }
            }

            int maxEntries = Services.PLATFORM.getMaxHistoryEntries();
            int toShow = Math.min(maxEntries, playerHistory.size());

            if (toShow == 0) {
                player.sendSystemMessage(Component.translatable("securetrade.history.empty").withStyle(ChatFormatting.GRAY));
                return;
            }

            player.sendSystemMessage(Component.translatable("securetrade.history.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (int i = 0; i < toShow; i++) {
                TradeEntry entry = playerHistory.get(i);
                
                // Determine other player name
                String otherName = playerUuid.equals(entry.senderUuid) ? entry.targetName : entry.senderName;
                
                // Format entry title, e.g. "1. Trade with Player2:"
                Component otherNameComponent = Component.literal(otherName).withStyle(ChatFormatting.AQUA);
                player.sendSystemMessage(Component.translatable("securetrade.history.entry", i + 1, otherNameComponent).withStyle(ChatFormatting.GRAY));

                // Determine what was given and received by THIS player
                List<ItemInfo> gaveItems = playerUuid.equals(entry.senderUuid) ? entry.senderItems : entry.targetItems;
                List<ItemInfo> receivedItems = playerUuid.equals(entry.senderUuid) ? entry.targetItems : entry.senderItems;
                int gaveXP = playerUuid.equals(entry.senderUuid) ? entry.senderXP : entry.targetXP;
                int receivedXP = playerUuid.equals(entry.senderUuid) ? entry.targetXP : entry.senderXP;

                Component gaveComponent = formatItemsAndXP(gaveItems, gaveXP);
                player.sendSystemMessage(Component.translatable("securetrade.history.gave", gaveComponent).withStyle(ChatFormatting.RED));

                Component receivedComponent = formatItemsAndXP(receivedItems, receivedXP);
                player.sendSystemMessage(Component.translatable("securetrade.history.received", receivedComponent).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Error reading trade history: " + e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private static Component formatItemsAndXP(List<ItemInfo> items, int xp) {
        boolean hasItems = items != null && !items.isEmpty();
        if (!hasItems && xp <= 0) {
            return Component.translatable("securetrade.history.nothing").withStyle(ChatFormatting.GRAY);
        }

        MutableComponent result = Component.empty();
        boolean hasContent = false;

        if (hasItems) {
            for (ItemInfo item : items) {
                if (hasContent) {
                    result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                result.append(formatItem(item));
                hasContent = true;
            }
        }

        if (xp > 0) {
            if (hasContent) {
                result.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            result.append(Component.literal(xp + " XP").withStyle(ChatFormatting.AQUA));
        }

        return result;
    }

    private static Component formatItem(ItemInfo item) {
        MutableComponent itemName = resolveItemName(item).withStyle(ChatFormatting.YELLOW);
        Component hoverText = Component.literal(item.id + "\n" + item.count + "x").withStyle(ChatFormatting.GRAY);

        return Component.empty()
                .append(Component.literal(item.count + "x ").withStyle(ChatFormatting.GRAY))
                .append(itemName)
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hoverText)));
    }

    private static MutableComponent resolveItemName(ItemInfo item) {
        if (item == null || item.id == null || item.id.isBlank()) {
            return Component.translatable("securetrade.history.unknown_item");
        }

        try {
            Identifier id = Identifier.parse(item.id);
            Item resolvedItem = BuiltInRegistries.ITEM.get(id).map(reference -> reference.value()).orElse(Items.AIR);
            if (resolvedItem != Items.AIR || "minecraft:air".equals(item.id)) {
                return Component.translatable(resolvedItem.getDescriptionId());
            }
        } catch (Exception ignored) {
            // Fall back to the stored legacy display name below.
        }

        if (item.displayName != null && !item.displayName.isBlank()) {
            return Component.literal(item.displayName);
        }

        return Component.literal(item.id);
    }

}
