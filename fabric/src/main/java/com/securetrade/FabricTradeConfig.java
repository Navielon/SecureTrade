package com.securetrade;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FabricTradeConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = Paths.get("config", "securetrade-server.toml");

    public static int requestTimeoutSeconds = 60;
    public static double maxTradeDistance = -1.0;
    public static boolean enableTradeLogging = true;
    public static int countdownSeconds = 3;
    public static int tradeCooldownSeconds = 10;

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
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.error("Failed to parse config value for key: " + key + ", value: " + val, e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load securetrade-server.toml", e);
        }
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
                    "tradeCooldownSeconds = 10\n";

            Files.writeString(CONFIG_PATH, defaultConfig);
        } catch (IOException e) {
            LOGGER.error("Failed to write default securetrade-server.toml", e);
        }
    }
}
