package com.securetrade;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Collections;
import java.util.Arrays;

public class TradeConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MAX_TRADE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TRADE_LOGGING;
    public static final ForgeConfigSpec.IntValue COUNTDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue TRADE_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> BLACKLISTED_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ALLOWED_DIMENSIONS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> BLOCKED_DIMENSIONS;
    public static final ForgeConfigSpec.IntValue MAX_HISTORY_ENTRIES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Secure Trade Configuration").push("general");

        REQUEST_TIMEOUT_SECONDS = builder
                .comment("Time in seconds before a trade request expires")
                .defineInRange("requestTimeoutSeconds", 60, 10, 300);

        MAX_TRADE_DISTANCE = builder
                .comment("Maximum distance in blocks allowed between players to trade (-1 for infinite)")
                .defineInRange("maxTradeDistance", -1.0, -1.0, 10000.0);

        ENABLE_TRADE_LOGGING = builder
                .comment("Enable detailed transaction logging in logs/securetrade.log")
                .define("enableTradeLogging", true);

        COUNTDOWN_SECONDS = builder
                .comment("Seconds to wait before executing the trade after both players are ready")
                .defineInRange("countdownSeconds", 3, 1, 10);

        TRADE_COOLDOWN_SECONDS = builder
                .comment("Cooldown in seconds before a player can send another trade request to the same player")
                .defineInRange("tradeCooldownSeconds", 10, 0, 3600);

        BLACKLISTED_ITEMS = builder
                .comment("List of item IDs that cannot be traded")
                .defineList("blacklistedItems", Arrays.asList("minecraft:bedrock"), o -> o instanceof String);

        ALLOWED_DIMENSIONS = builder
                .comment("List of dimension IDs where trading is allowed (leave empty to allow all)")
                .defineList("allowedDimensions", Collections.emptyList(), o -> o instanceof String);

        BLOCKED_DIMENSIONS = builder
                .comment("List of dimension IDs where trading is blocked (leave empty to block none)")
                .defineList("blockedDimensions", Collections.emptyList(), o -> o instanceof String);

        MAX_HISTORY_ENTRIES = builder
                .comment("Maximum number of trade history entries to keep")
                .defineInRange("maxHistoryEntries", 5, 1, 100);

        builder.pop();
        SPEC = builder.build();
    }
}
