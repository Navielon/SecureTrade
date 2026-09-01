package com.securetrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.securetrade.platform.Services;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.FolderName;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
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

    public static void recordTrade(ServerPlayerEntity p1, ServerPlayerEntity p2, net.minecraft.inventory.Inventory inv1, net.minecraft.inventory.Inventory inv2, long p1XP, long p2XP) {
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

            Path historyFile = server.getWorldPath(FolderName.ROOT).resolve("securetrade-history.json");
            int limit = Math.max(100, Services.PLATFORM.getMaxHistoryEntries() * 10);
            submit(() -> appendEntry(historyFile, entry, limit));
        } catch (Exception e) {
            TradeLogger.log("Failed to record trade history: " + e.getMessage());
        }
    }

    private static void appendEntry(Path historyFile, TradeEntry entry, int limit) {
        List<TradeEntry> history = loadHistory(historyFile);
        history.add(0, entry);
        while (history.size() > limit) {
            history.remove(history.size() - 1);
        }
        saveHistory(historyFile, history);
    }

    private static List<ItemInfo> getItemsList(net.minecraft.inventory.Inventory container) {
        List<ItemInfo> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String id = Registry.ITEM.getKey(stack.getItem()).toString();
                int count = stack.getCount();
                String displayName = stack.getHoverName().getString();
                ItemStack stackCopy = stack.copy();
                stackCopy.setCount(1);
                String stackData = stackCopy.save(new CompoundNBT()).toString();
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
            backupCorruptHistory(path);
            return new ArrayList<>();
        }
    }

    private static void saveHistory(Path path, List<TradeEntry> history) {
        Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(history, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            TradeLogger.log("Failed to save trade history: " + e.getMessage());
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception ignored) {
            }
        }
    }

    public static void showHistory(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Path historyFile = server.getWorldPath(FolderName.ROOT).resolve("securetrade-history.json");
        String playerUuid = player.getUUID().toString();
        int maxEntries = Services.PLATFORM.getMaxHistoryEntries();
        submit(() -> {
            List<TradeEntry> history = loadHistory(historyFile);
            List<TradeEntry> playerHistory = new ArrayList<>();
            for (TradeEntry entry : history) {
                if (playerUuid.equals(entry.senderUuid) || playerUuid.equals(entry.targetUuid)) {
                    playerHistory.add(entry);
                }
            }
            server.execute(() -> renderHistory(player, playerUuid, playerHistory, maxEntries));
        });
    }

    private static void renderHistory(ServerPlayerEntity player, String playerUuid, List<TradeEntry> playerHistory, int maxEntries) {
        try {
            int toShow = Math.min(maxEntries, playerHistory.size());

            if (toShow == 0) {
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.empty").withStyle(TextFormatting.GRAY));
                return;
            }

            TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.title").withStyle(TextFormatting.GOLD, TextFormatting.BOLD));

            for (int i = 0; i < toShow; i++) {
                TradeEntry entry = playerHistory.get(i);
                
                String otherName = playerUuid.equals(entry.senderUuid) ? entry.targetName : entry.senderName;
                
                ITextComponent otherNameITextComponent = TradeMessages.text(otherName).withStyle(TextFormatting.AQUA);
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.entry", i + 1, otherNameITextComponent).withStyle(TextFormatting.GRAY));

                List<ItemInfo> gaveItems = playerUuid.equals(entry.senderUuid) ? entry.senderItems : entry.targetItems;
                List<ItemInfo> receivedItems = playerUuid.equals(entry.senderUuid) ? entry.targetItems : entry.senderItems;
                long gaveXP = playerUuid.equals(entry.senderUuid) ? entry.senderXP : entry.targetXP;
                long receivedXP = playerUuid.equals(entry.senderUuid) ? entry.targetXP : entry.senderXP;

                ITextComponent gaveITextComponent = formatItemsAndXP(gaveItems, gaveXP);
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.gave", gaveITextComponent).withStyle(TextFormatting.RED));

                ITextComponent receivedITextComponent = formatItemsAndXP(receivedItems, receivedXP);
                TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.received", receivedITextComponent).withStyle(TextFormatting.GREEN));
            }
        } catch (Exception e) {
            TradeMessages.sendRaw(player, TradeMessages.trans("securetrade.history.error", e.getMessage()).withStyle(TextFormatting.RED));
        }
    }

    private static void submit(Runnable task) {
        synchronized (EXECUTOR_LOCK) {
            executor.submit(task);
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SecureTrade-History");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void backupCorruptHistory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".corrupt-" + System.currentTimeMillis());
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception backupError) {
            TradeLogger.log("Failed to back up corrupt trade history: " + backupError.getMessage());
        }
    }

    public static void shutdown() {
        ExecutorService toShutdown;
        synchronized (EXECUTOR_LOCK) {
            toShutdown = executor;
            executor = createExecutor();
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

    private static ITextComponent formatItemsAndXP(List<ItemInfo> items, long xp) {
        boolean hasItems = items != null && !items.isEmpty();
        if (!hasItems && xp <= 0) {
            return TradeMessages.trans("securetrade.history.nothing").withStyle(TextFormatting.GRAY);
        }

        IFormattableTextComponent result = TradeMessages.empty();
        boolean hasContent = false;

        if (hasItems) {
            for (ItemInfo item : aggregateItems(items)) {
                if (hasContent) {
                    result.append(TradeMessages.text(", ").withStyle(TextFormatting.GRAY));
                }
                result.append(formatItem(item));
                hasContent = true;
            }
        }

        if (xp > 0) {
            if (hasContent) {
                result.append(TradeMessages.text(", ").withStyle(TextFormatting.GRAY));
            }
            result.append(TradeMessages.trans("securetrade.history.xp_amount", xp).withStyle(TextFormatting.AQUA));
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

    private static ITextComponent formatItem(ItemInfo item) {
        ItemStack hoverStack = resolveItemStack(item);
        IFormattableTextComponent itemName = (hoverStack.isEmpty() ? resolveItemName(item) : hoverStack.getHoverName().copy())
                .withStyle(TextFormatting.YELLOW);
        IFormattableTextComponent result = TradeMessages.empty()
                .append(TradeMessages.text(item.count + "x ").withStyle(TextFormatting.GRAY))
                .append(itemName);
        if (!hoverStack.isEmpty()) {
            return result.withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemHover(hoverStack))
            ));
        }

        ITextComponent hoverText = TradeMessages.text(item.id + "\n" + item.count + "x").withStyle(TextFormatting.GRAY);
        return result.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }

    private static IFormattableTextComponent resolveItemName(ItemInfo item) {
        if (item == null || item.id == null || item.id.trim().isEmpty()) {
            return TradeMessages.trans("securetrade.history.unknown_item");
        }

        try {
            ResourceLocation id = new ResourceLocation(item.id);
            Item resolvedItem = Registry.ITEM.get(id);
            if (resolvedItem != Items.AIR || "minecraft:air".equals(item.id)) {
                return TradeMessages.trans(resolvedItem.getDescriptionId());
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
                return ItemStack.of(JsonToNBT.parseTag(item.stackData));
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




