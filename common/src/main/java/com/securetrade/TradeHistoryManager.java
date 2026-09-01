package com.securetrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.securetrade.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TradeHistoryManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object EXECUTOR_LOCK = new Object();
    private static ExecutorService executor = createExecutor();

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

    public static void recordTrade(ServerPlayer p1, ServerPlayer p2, net.minecraft.world.SimpleContainer inv1, net.minecraft.world.SimpleContainer inv2, long p1XP, long p2XP) {
        try {
            MinecraftServer server = p1.getServer();
            if (server == null) return;

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

            Path historyFile = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-history.json");
            int limit = Math.max(100, Services.PLATFORM.getMaxHistoryEntries() * 10);
            submit(() -> appendEntry(historyFile, entry, limit));
        } catch (Exception e) {
            LOGGER.error("Failed to snapshot trade history entry", e);
        }
    }

    private static List<ItemInfo> getItemsList(net.minecraft.world.SimpleContainer container) {
        List<ItemInfo> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String id = Registry.ITEM.getKey(stack.getItem()).toString();
                int count = stack.getCount();
                String displayName = stack.getHoverName().getString();
                ItemStack stackCopy = stack.copy();
                stackCopy.setCount(1);
                String stackData = stackCopy.save(new CompoundTag()).toString();
                list.add(new ItemInfo(id, count, displayName, stackData));
            }
        }
        return list;
    }

    private static List<TradeEntry> readHistory(Path path) throws Exception {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            List<TradeEntry> list = GSON.fromJson(reader, new TypeToken<ArrayList<TradeEntry>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        }
    }

    private static void appendEntry(Path path, TradeEntry entry, int limit) {
        List<TradeEntry> history;
        try {
            history = readHistory(path);
        } catch (Exception e) {
            LOGGER.error("Failed to read trade history; preserving the damaged file", e);
            backupCorruptHistory(path);
            history = new ArrayList<>();
        }

        history.add(0, entry);
        while (history.size() > limit) {
            history.remove(history.size() - 1);
        }
        saveHistoryAtomically(path, history);
    }

    private static void saveHistoryAtomically(Path path, List<TradeEntry> history) {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(history, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save trade history", e);
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception cleanupError) {
                LOGGER.debug("Failed to remove temporary trade history file", cleanupError);
            }
        }
    }

    private static void backupCorruptHistory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Failed to preserve damaged trade history at {}", backup, e);
        }
    }

    public static void showHistory(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path historyFile = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-history.json");
        int maxEntries = Services.PLATFORM.getMaxHistoryEntries();
        submit(() -> {
            try {
                List<TradeEntry> history = readHistory(historyFile);
                server.execute(() -> sendHistory(player, history, maxEntries));
            } catch (Exception e) {
                LOGGER.error("Failed to load trade history", e);
                server.execute(() -> player.sendSystemMessage(
                        Component.translatable("securetrade.history.error", e.getMessage()).withStyle(ChatFormatting.RED)
                ));
            }
        });
    }

    private static void sendHistory(ServerPlayer player, List<TradeEntry> history, int maxEntries) {
        try {
            MinecraftServer server = player.getServer();
            if (server == null) return;

            String playerUuid = player.getUUID().toString();
            List<TradeEntry> playerHistory = new ArrayList<>();
            for (TradeEntry entry : history) {
                if (playerUuid.equals(entry.senderUuid) || playerUuid.equals(entry.targetUuid)) {
                    playerHistory.add(entry);
                }
            }

            int toShow = Math.min(maxEntries, playerHistory.size());

            if (toShow == 0) {
                player.sendSystemMessage(Component.translatable("securetrade.history.empty").withStyle(ChatFormatting.GRAY));
                return;
            }

            player.sendSystemMessage(Component.translatable("securetrade.history.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (int i = 0; i < toShow; i++) {
                TradeEntry entry = playerHistory.get(i);
                
                String otherName = playerUuid.equals(entry.senderUuid) ? entry.targetName : entry.senderName;
                
                Component otherNameComponent = Component.literal(otherName).withStyle(ChatFormatting.AQUA);
                player.sendSystemMessage(Component.translatable("securetrade.history.entry", i + 1, otherNameComponent).withStyle(ChatFormatting.GRAY));

                List<ItemInfo> gaveItems = playerUuid.equals(entry.senderUuid) ? entry.senderItems : entry.targetItems;
                List<ItemInfo> receivedItems = playerUuid.equals(entry.senderUuid) ? entry.targetItems : entry.senderItems;
                long gaveXP = playerUuid.equals(entry.senderUuid) ? entry.senderXP : entry.targetXP;
                long receivedXP = playerUuid.equals(entry.senderUuid) ? entry.targetXP : entry.senderXP;

                Component gaveComponent = formatItemsAndXP(gaveItems, gaveXP);
                player.sendSystemMessage(Component.translatable("securetrade.history.gave", gaveComponent).withStyle(ChatFormatting.RED));

                Component receivedComponent = formatItemsAndXP(receivedItems, receivedXP);
                player.sendSystemMessage(Component.translatable("securetrade.history.received", receivedComponent).withStyle(ChatFormatting.GREEN));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to render trade history", e);
            player.sendSystemMessage(Component.translatable("securetrade.history.error", e.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SecureTrade-History");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void submit(Runnable task) {
        synchronized (EXECUTOR_LOCK) {
            if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                executor = createExecutor();
            }
            executor.execute(task);
        }
    }

    public static void shutdown() {
        ExecutorService toShutdown;
        synchronized (EXECUTOR_LOCK) {
            toShutdown = executor;
            executor = null;
        }
        if (toShutdown == null) {
            return;
        }

        toShutdown.shutdown();
        try {
            if (!toShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
                toShutdown.shutdownNow();
            }
        } catch (InterruptedException e) {
            toShutdown.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static Component formatItemsAndXP(List<ItemInfo> items, long xp) {
        boolean hasItems = items != null && !items.isEmpty();
        if (!hasItems && xp <= 0) {
            return Component.translatable("securetrade.history.nothing").withStyle(ChatFormatting.GRAY);
        }

        MutableComponent result = Component.empty();
        boolean hasContent = false;

        if (hasItems) {
            for (ItemInfo item : aggregateItems(items)) {
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
            result.append(Component.translatable("securetrade.history.xp_amount", xp).withStyle(ChatFormatting.AQUA));
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
        if (item.stackData != null && !item.stackData.isBlank()) {
            return item.stackData;
        }
        return item.id + "\u0000" + (item.displayName == null ? "" : item.displayName);
    }

    private static Component formatItem(ItemInfo item) {
        ItemStack hoverStack = resolveItemStack(item);
        MutableComponent itemName = (hoverStack.isEmpty() ? resolveItemName(item) : hoverStack.getHoverName().copy())
                .withStyle(ChatFormatting.YELLOW);
        MutableComponent result = Component.empty()
                .append(Component.literal(item.count + "x ").withStyle(ChatFormatting.GRAY))
                .append(itemName);
        if (!hoverStack.isEmpty()) {
            return result.withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(hoverStack))
            ));
        }

        Component hoverText = Component.literal(item.id + "\n" + item.count + "x").withStyle(ChatFormatting.GRAY);
        return result.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }

    private static MutableComponent resolveItemName(ItemInfo item) {
        if (item == null || item.id == null || item.id.isBlank()) {
            return Component.translatable("securetrade.history.unknown_item");
        }

        try {
            ResourceLocation id = new ResourceLocation(item.id);
            Item resolvedItem = Registry.ITEM.get(id);
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

    private static ItemStack resolveItemStack(ItemInfo item) {
        if (item == null || item.id == null || item.id.isBlank()) {
            return ItemStack.EMPTY;
        }

        if (item.stackData != null && !item.stackData.isBlank()) {
            try {
                return ItemStack.of(TagParser.parseTag(item.stackData));
            } catch (Exception ignored) {
            }
        }

        try {
            ResourceLocation id = new ResourceLocation(item.id);
            Item resolvedItem = Registry.ITEM.get(id);
            if (resolvedItem != Items.AIR || "minecraft:air".equals(item.id)) {
                return new ItemStack(resolvedItem);
            }
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }

}
