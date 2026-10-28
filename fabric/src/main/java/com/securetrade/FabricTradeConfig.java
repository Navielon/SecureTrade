package com.securetrade;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public class FabricTradeConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = Paths.get("config", "securetrade-server.toml");

    public static int requestTimeoutSeconds = 60;
    public static double maxTradeDistance = -1.0;
    public static boolean allowCrossDimensionTrades = true;
    public static boolean enableTradeLogging = true;
    public static int countdownSeconds = 3;
    public static int tradeCooldownSeconds = 10;
    public static java.util.List<String> blacklistedItems = new java.util.ArrayList<>(java.util.List.of("minecraft:bedrock"));
    public static java.util.List<String> allowedDimensions = new java.util.ArrayList<>();
    public static java.util.List<String> blockedDimensions = new java.util.ArrayList<>();
    public static int maxHistoryEntries = 5;
    public static String chatPrefix = "Secure Trade";

    private record Option(String key, String comment, String defaultValue) {
    }

    private static final List<Option> OPTIONS = List.of(
            new Option("requestTimeoutSeconds", "Time in seconds before a trade request expires", "60"),
            new Option("maxTradeDistance", "Maximum distance in blocks allowed between players to trade (-1 for infinite)", "-1.0"),
            new Option("allowCrossDimensionTrades", "Allow trades between players in different dimensions when no distance limit prevents it", "true"),
            new Option("enableTradeLogging", "Enable detailed transaction logging in logs/securetrade.log", "true"),
            new Option("countdownSeconds", "Seconds to wait before executing the trade after both players are ready", "3"),
            new Option("tradeCooldownSeconds", "Cooldown in seconds before a player can send another trade request to the same player", "10"),
            new Option("blacklistedItems", "List of item IDs that cannot be traded", "[\"minecraft:bedrock\"]"),
            new Option("allowedDimensions", "List of dimension IDs where trading is allowed (leave empty to allow all)", "[]"),
            new Option("blockedDimensions", "List of dimension IDs where trading is blocked (leave empty to block none)", "[]"),
            new Option("maxHistoryEntries", "Maximum number of trade history entries to keep", "5"),
            new Option("chatPrefix", "Prefix shown before Secure Trade chat messages (empty to show no prefix)", "\"Secure Trade\"")
    );

    public static synchronized void load() {
        resetDefaults();
        try {
            if (!Files.exists(CONFIG_PATH)) {
                writeDefaultConfig();
                return;
            }

            List<String> lines = Files.readAllLines(CONFIG_PATH);
            Set<String> presentKeys = new HashSet<>();
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String val = parts[1].trim();
                    presentKeys.add(key);

                    try {
                        switch (key) {
                            case "requestTimeoutSeconds":
                                requestTimeoutSeconds = Math.max(10, Math.min(300, Integer.parseInt(val)));
                                break;
                            case "maxTradeDistance":
                                maxTradeDistance = Math.max(-1.0, Math.min(10000.0, Double.parseDouble(val)));
                                break;
                            case "allowCrossDimensionTrades":
                                allowCrossDimensionTrades = Boolean.parseBoolean(val);
                                break;
                            case "enableTradeLogging":
                                enableTradeLogging = Boolean.parseBoolean(val);
                                break;
                            case "countdownSeconds":
                                countdownSeconds = Math.max(1, Math.min(10, Integer.parseInt(val)));
                                break;
                            case "tradeCooldownSeconds":
                                tradeCooldownSeconds = Math.max(0, Math.min(3600, Integer.parseInt(val)));
                                break;
                            case "blacklistedItems":
                                blacklistedItems = parseIdentifierList(val, key);
                                break;
                            case "allowedDimensions":
                                allowedDimensions = parseIdentifierList(val, key);
                                break;
                            case "blockedDimensions":
                                blockedDimensions = parseIdentifierList(val, key);
                                break;
                            case "maxHistoryEntries":
                                maxHistoryEntries = Math.max(1, Math.min(100, Integer.parseInt(val)));
                                break;
                            case "chatPrefix":
                                chatPrefix = parseChatPrefix(val);
                                break;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse config value for key: " + key + ", value: " + val, e);
                    }
                }
            }
            appendMissingOptions(presentKeys);
        } catch (IOException e) {
            LOGGER.error("Failed to load securetrade-server.toml", e);
        }
    }

    private static void resetDefaults() {
        requestTimeoutSeconds = 60;
        maxTradeDistance = -1.0;
        allowCrossDimensionTrades = true;
        enableTradeLogging = true;
        countdownSeconds = 3;
        tradeCooldownSeconds = 10;
        blacklistedItems = new java.util.ArrayList<>(java.util.List.of("minecraft:bedrock"));
        allowedDimensions = new java.util.ArrayList<>();
        blockedDimensions = new java.util.ArrayList<>();
        maxHistoryEntries = 5;
        chatPrefix = "Secure Trade";
    }

    private static java.util.List<String> parseIdentifierList(String val, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        val = val.trim();
        if (val.startsWith("[") && val.endsWith("]")) {
            val = val.substring(1, val.length() - 1);
            if (!val.trim().isEmpty()) {
                String[] items = val.split(",");
                for (String item : items) {
                    item = item.trim();
                    if ((item.startsWith("\"") && item.endsWith("\"")) || (item.startsWith("'") && item.endsWith("'"))) {
                        item = item.substring(1, item.length() - 1);
                    }
                    if (!item.isEmpty()) {
                        if (ResourceLocation.tryParse(item) != null) {
                            list.add(item);
                        } else {
                            LOGGER.warn("Ignoring invalid identifier '{}' in {}", item, key);
                        }
                    }
                }
            }
        }
        return list;
    }

    private static String parseChatPrefix(String val) {
        if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
            val = val.substring(1, val.length() - 1);
        }
        return val.length() > 32 ? val.substring(0, 32) : val;
    }

    private static void writeDefaultConfig() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }

            StringBuilder defaultConfig = new StringBuilder("# Secure Trade Configuration\n[general]\n");
            for (Option option : OPTIONS) {
                defaultConfig.append(formatOption(option));
            }

            Files.writeString(CONFIG_PATH, defaultConfig.toString());
        } catch (IOException e) {
            LOGGER.error("Failed to write default securetrade-server.toml", e);
        }
    }

    /**
     * Options added by newer versions are appended to an existing file, so server owners can see them.
     */
    private static void appendMissingOptions(Set<String> presentKeys) {
        StringBuilder missing = new StringBuilder();
        for (Option option : OPTIONS) {
            if (!presentKeys.contains(option.key())) {
                missing.append(formatOption(option));
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        try {
            Files.writeString(CONFIG_PATH, missing.toString(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to add new options to securetrade-server.toml", e);
        }
    }

    private static String formatOption(Option option) {
        return "\n# " + option.comment() + "\n" + option.key() + " = " + option.defaultValue() + "\n";
    }
}
