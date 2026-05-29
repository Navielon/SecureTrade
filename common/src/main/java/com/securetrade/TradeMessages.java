package com.securetrade;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class TradeMessages {
    private TradeMessages() {
    }

    public static Component playerName(ServerPlayer player) {
        return text(player.getScoreboardName()).withStyle(ChatFormatting.AQUA);
    }

    public static Component playerName(String name) {
        return text(name).withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent text(String value) {
        return new TextComponent(value);
    }

    public static MutableComponent trans(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
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
        return empty()
                .append(text("Secure Trade").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(text(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(message.copy().withStyle(color));
    }

    public static void sendRaw(ServerPlayer player, Component message) {
        player.sendMessage(message, Util.NIL_UUID);
    }

    private static void send(ServerPlayer player, Component message, ChatFormatting color) {
        sendRaw(player, format(message, color));
    }
}
