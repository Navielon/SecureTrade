package com.securetrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradePreferencesManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, PlayerPreferences> PREFERENCES = new HashMap<>();
    private static Path loadedPath;

    private static final class PlayerPreferences {
        private boolean dnd;
        private Map<String, String> blockedPlayers = new LinkedHashMap<>();
    }

    private TradePreferencesManager() {
    }

    public static synchronized boolean toggleDnd(ServerPlayer player) {
        PlayerPreferences preferences = getPreferences(player);
        preferences.dnd = !preferences.dnd;
        save();
        return preferences.dnd;
    }

    public static synchronized boolean isDnd(ServerPlayer player) {
        return getPreferences(player).dnd;
    }

    public static synchronized boolean isBlocked(ServerPlayer owner, UUID otherPlayerId) {
        return getPreferences(owner).blockedPlayers.containsKey(otherPlayerId.toString());
    }

    public static synchronized void block(ServerPlayer owner, ServerPlayer blockedPlayer) {
        getPreferences(owner).blockedPlayers.put(
                blockedPlayer.getUUID().toString(),
                blockedPlayer.getScoreboardName()
        );
        save();
    }

    public static synchronized boolean unblock(ServerPlayer owner, UUID blockedPlayerId) {
        boolean removed = getPreferences(owner).blockedPlayers.remove(blockedPlayerId.toString()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static synchronized String unblockByName(ServerPlayer owner, String blockedPlayerName) {
        Iterator<Map.Entry<String, String>> entries = getPreferences(owner).blockedPlayers.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, String> entry = entries.next();
            if (entry.getValue().equalsIgnoreCase(blockedPlayerName)) {
                String storedName = entry.getValue();
                entries.remove();
                save();
                return storedName;
            }
        }
        return null;
    }

    public static synchronized List<String> getBlockedPlayerNames(ServerPlayer owner) {
        return new ArrayList<>(getPreferences(owner).blockedPlayers.values());
    }

    public static synchronized void clear() {
        PREFERENCES.clear();
        loadedPath = null;
    }

    private static PlayerPreferences getPreferences(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return new PlayerPreferences();
        }
        ensureLoaded(server);
        return PREFERENCES.computeIfAbsent(player.getUUID().toString(), ignored -> new PlayerPreferences());
    }

    private static void ensureLoaded(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("securetrade-player-settings.json");
        if (path.equals(loadedPath)) {
            return;
        }

        PREFERENCES.clear();
        loadedPath = path;
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            Map<String, PlayerPreferences> loaded = GSON.fromJson(
                    reader,
                    new TypeToken<Map<String, PlayerPreferences>>() { }.getType()
            );
            if (loaded != null) {
                for (Map.Entry<String, PlayerPreferences> entry : loaded.entrySet()) {
                    PlayerPreferences preferences = entry.getValue();
                    if (preferences == null) {
                        continue;
                    }
                    if (preferences.blockedPlayers == null) {
                        preferences.blockedPlayers = new LinkedHashMap<>();
                    }
                    PREFERENCES.put(entry.getKey(), preferences);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read Secure Trade player settings; preserving the damaged file", e);
            backupCorruptFile(path);
        }
    }

    private static void save() {
        if (loadedPath == null) {
            return;
        }

        Path temporary = loadedPath.resolveSibling(loadedPath.getFileName() + ".tmp");
        try {
            Files.createDirectories(loadedPath.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(PREFERENCES, writer);
            }
            try {
                Files.move(temporary, loadedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, loadedPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save Secure Trade player settings", e);
            try {
                Files.deleteIfExists(temporary);
            } catch (Exception cleanupError) {
                LOGGER.debug("Failed to remove temporary player settings file", cleanupError);
            }
        }
    }

    private static void backupCorruptFile(Path path) {
        Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Failed to preserve damaged player settings at {}", backup, e);
        }
    }
}
