package com.securetrade;

import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TradeMessages {
    private TradeMessages() {
    }

    public static Text playerName(ServerPlayerEntity player) {
        return text(player.getEntityName()).formatted(Formatting.AQUA);
    }

    public static Text playerName(String name) {
        return text(name).formatted(Formatting.AQUA);
    }

    public static MutableText text(String value) {
        return new LiteralText(value);
    }

    public static MutableText trans(String key, Object... args) {
        return new TranslatableText(key, args);
    }

    public static MutableText empty() {
        return new LiteralText("");
    }

    public static void info(ServerPlayerEntity player, Text message) {
        send(player, message, Formatting.GRAY);
    }

    public static void success(ServerPlayerEntity player, Text message) {
        send(player, message, Formatting.GREEN);
    }

    public static void warning(ServerPlayerEntity player, Text message) {
        send(player, message, Formatting.YELLOW);
    }

    public static void error(ServerPlayerEntity player, Text message) {
        send(player, message, Formatting.RED);
    }

    public static MutableText format(Text message, Formatting color) {
        MutableText mutable = message instanceof MutableText
                ? (MutableText) message
                : message.copy();
        return empty()
                .append(text("Secure Trade").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(text(" | ").formatted(Formatting.DARK_GRAY))
                .append(mutable.formatted(color));
    }

    public static void sendRaw(ServerPlayerEntity player, Text message) {
        player.sendSystemMessage(message, Util.NIL_UUID);
    }

    private static void send(ServerPlayerEntity player, Text message, Formatting color) {
        sendRaw(player, format(message, color));
    }
}
