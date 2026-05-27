package com.securetrade;

import net.neoforged.neoforge.common.ModConfigSpec;

public class TradeConfig {
    public static final ModConfigSpec SPEC;
    
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ModConfigSpec.DoubleValue MAX_TRADE_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_TRADE_LOGGING;
    public static final ModConfigSpec.IntValue COUNTDOWN_SECONDS;
    public static final ModConfigSpec.IntValue TRADE_COOLDOWN_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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

        builder.pop();
        SPEC = builder.build();
    }
}
