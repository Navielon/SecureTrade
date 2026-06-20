package com.securetrade;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class FabricTradeConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Path CONFIG_PATH = Paths.get("config", "securetrade-server.toml");

    public static int requestTimeoutSeconds = 60;
    public static double maxTradeDistance = -1.0;
    public static boolean enableTradeLogging = true;
    public static int countdownSeconds = 3;
    public static int tradeCooldownSeconds = 10;
    public static java.util.List<String> blacklistedItems = new java.util.ArrayList<>(Arrays.asList("minecraft:bedrock"));
    public static java.util.List<String> allowedDimensions = new java.util.ArrayList<>();
    public static java.util.List<String> blockedDimensions = new java.util.ArrayList<>();
    public static int maxHistoryEntries = 5;

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                writeDefaultConfig();
                return;
            }

            List<String> lines = Files.readAllLines(CONFIG_PATH);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String val = parts[1].trim();

                    try {
                        switch (key) {
                            case "requestTimeoutSeconds":
                                requestTimeoutSeconds = Math.max(10, Math.min(300, Integer.parseInt(val)));
                                break;
                            case "maxTradeDistance":
                                maxTradeDistance = Math.max(-1.0, Math.min(10000.0, Double.parseDouble(val)));
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
                                blacklistedItems = parseList(val);
                                break;
                            case "allowedDimensions":
                                allowedDimensions = parseList(val);
                                break;
                            case "blockedDimensions":
                                blockedDimensions = parseList(val);
                                break;
                            case "maxHistoryEntries":
                                maxHistoryEntries = Math.max(1, Math.min(100, Integer.parseInt(val)));
                                break;
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to parse config value for key: " + key + ", value: " + val, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load securetrade-server.toml", e);
        }
    }

    private static java.util.List<String> parseList(String val) {
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
                        list.add(item);
                    }
                }
            }
        }
        return list;
    }

    private static void writeDefaultConfig() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }

            String defaultConfig = "# Secure Trade Configuration\n" +
                    "[general]\n" +
                    "# Time in seconds before a trade request expires\n" +
                    "requestTimeoutSeconds = 60\n" +
                    "\n" +
                    "# Maximum distance in blocks allowed between players to trade (-1 for infinite)\n" +
                    "maxTradeDistance = -1.0\n" +
                    "\n" +
                    "# Enable detailed transaction logging in logs/securetrade.log\n" +
                    "enableTradeLogging = true\n" +
                    "\n" +
                    "# Seconds to wait before executing the trade after both players are ready\n" +
                    "countdownSeconds = 3\n" +
                    "\n" +
                    "# Cooldown in seconds before a player can send another trade request to the same player\n" +
                    "tradeCooldownSeconds = 10\n" +
                    "\n" +
                    "# List of item IDs that cannot be traded\n" +
                    "blacklistedItems = [\"minecraft:bedrock\"]\n" +
                    "\n" +
                    "# List of dimension IDs where trading is allowed (leave empty to allow all)\n" +
                    "allowedDimensions = []\n" +
                    "\n" +
                    "# List of dimension IDs where trading is blocked (leave empty to block none)\n" +
                    "blockedDimensions = []\n" +
                    "\n" +
                    "# Maximum number of trade history entries to keep\n" +
                    "maxHistoryEntries = 5\n";

            Files.write(CONFIG_PATH, defaultConfig.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Failed to write default securetrade-server.toml", e);
        }
    }
}
