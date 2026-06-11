package com.securetrade;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;

public final class TradeMessages {
    private TradeMessages() {
    }

    public static ITextComponent playerName(ServerPlayerEntity player) {
        return text(player.getScoreboardName()).withStyle(TextFormatting.AQUA);
    }

    public static ITextComponent playerName(String name) {
        return text(name).withStyle(TextFormatting.AQUA);
    }

    public static IFormattableTextComponent text(String value) {
        return new StringTextComponent(value);
    }

    public static IFormattableTextComponent trans(String key, Object... args) {
        return new TranslationTextComponent(key, args);
    }

    public static IFormattableTextComponent empty() {
        return new StringTextComponent("");
    }

    public static void info(ServerPlayerEntity player, ITextComponent message) {
        send(player, message, TextFormatting.GRAY);
    }

    public static void success(ServerPlayerEntity player, ITextComponent message) {
        send(player, message, TextFormatting.GREEN);
    }

    public static void warning(ServerPlayerEntity player, ITextComponent message) {
        send(player, message, TextFormatting.YELLOW);
    }

    public static void error(ServerPlayerEntity player, ITextComponent message) {
        send(player, message, TextFormatting.RED);
    }

    public static IFormattableTextComponent format(ITextComponent message, TextFormatting color) {
        return empty()
                .append(text("Secure Trade").withStyle(TextFormatting.GOLD, TextFormatting.BOLD))
                .append(text(" | ").withStyle(TextFormatting.DARK_GRAY))
                .append(message.copy().withStyle(color));
    }

    public static void sendRaw(ServerPlayerEntity player, ITextComponent message) {
        player.sendMessage(message, Util.NIL_UUID);
    }

    private static void send(ServerPlayerEntity player, ITextComponent message, TextFormatting color) {
        sendRaw(player, format(message, color));
    }
}


