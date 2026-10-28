package com.securetrade;

import com.securetrade.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class TradeMessages {
    private TradeMessages() {
    }

    public static Component playerName(ServerPlayer player) {
        return Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.AQUA);
    }

    public static void info(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.GRAY);
    }

    public static void success(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.GREEN);
    }

    public static void warning(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.YELLOW);
    }

    public static void error(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.RED);
    }

    public static MutableComponent format(Component message, ChatFormatting color) {
        String prefix = Services.PLATFORM.getChatPrefix();
        MutableComponent formatted = Component.empty();
        if (prefix != null && !prefix.isBlank()) {
            formatted.append(Component.literal(prefix).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        }
        return formatted.append(message.copy().withStyle(color));
    }

    private static void send(ServerPlayer player, Component message, ChatFormatting color) {
        player.sendSystemMessage(format(message, color));
    }
}
