package com.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.securetrade.platform.Services;
import com.securetrade.menu.TradeMenu;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeMessages;
import com.securetrade.TradePreferencesManager;
import com.securetrade.TradeRules;
import com.securetrade.SecureTradeSounds;
import net.minecraft.util.Formatting;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

import java.util.List;

public class TradeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("trade")
                .then(CommandManager.argument("target", EntityArgumentType.player())
                        .executes(context -> requestTrade(context.getSource(), EntityArgumentType.getPlayer(context, "target"))))
                .then(CommandManager.literal("accept")
                        .executes(context -> acceptTrade(context.getSource())))
                .then(CommandManager.literal("deny")
                        .executes(context -> denyTrade(context.getSource())))
                .then(CommandManager.literal("history")
                        .executes(context -> showHistory(context.getSource())))
                .then(CommandManager.literal("dnd")
                        .executes(context -> toggleDnd(context.getSource())))
                .then(CommandManager.literal("block")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> blockPlayer(context.getSource(), EntityArgumentType.getPlayer(context, "player")))))
                .then(CommandManager.literal("unblock")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        TradePreferencesManager.getBlockedPlayerNames(context.getSource().getPlayer()), builder
                                ))
                                .executes(context -> unblockPlayer(context.getSource(), StringArgumentType.getString(context, "player")))))
                .then(CommandManager.literal("blocked")
                        .executes(context -> showBlockedPlayers(context.getSource())))
                .then(CommandManager.literal("blocklist")
                        .executes(context -> showBlockedPlayers(context.getSource())))
        );
    }

    private static int requestTrade(ServerCommandSource source, ServerPlayerEntity target) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayer();

        if (sender.getUuid().equals(target.getUuid())) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.cannot_trade_self"));
            return 0;
        }

        // Busy Check: Check if either player is already trading
        if (sender.currentScreenHandler instanceof TradeMenu) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }
        if (target.currentScreenHandler instanceof TradeMenu) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_target_already_trading", TradeMessages.playerName(target)));
            return 0;
        }

        if (TradePreferencesManager.isBlocked(sender, target.getUuid())) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_player_blocked_self"));
            return 0;
        }
        if (TradePreferencesManager.isBlocked(target, sender.getUuid())) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_player_unavailable"));
            return 0;
        }

        // Dimension Restrictions Check
        String senderDim = sender.world.getRegistryKey().getValue().toString();
        String targetDim = target.world.getRegistryKey().getValue().toString();

        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        // Distance Check
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = sender.world.getRegistryKey().equals(target.world.getRegistryKey());
        if (!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_different_dimensions"));
            return 0;
        }
        if (sameDimension && maxDist > 0 && sender.squaredDistanceTo(target) > maxDist * maxDist) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
            return 0;
        }

        long now = System.currentTimeMillis();

        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        boolean mutualCandidate = TradeRequestManager.isMutualCandidate(
                sender.getUuid(), target.getUuid(), now, cooldownMillis
        );
        if (!mutualCandidate && TradePreferencesManager.isDnd(target)) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_target_dnd"));
            return 0;
        }
        TradeRequestManager.CreateResult result = TradeRequestManager.create(
                sender.getUuid(), target.getUuid(), now,
                Services.PLATFORM.getRequestTimeoutSeconds() * 1000L, cooldownMillis
        );
        if (result.status() == TradeRequestManager.CreateStatus.MUTUAL) {
            TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
            TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));
            TradeMenu.openTrade(sender, target);
            return 1;
        }
        if (result.status() == TradeRequestManager.CreateStatus.SENDER_BUSY) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_already_requested"));
            return 0;
        }
        if (result.status() == TradeRequestManager.CreateStatus.TARGET_BUSY) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_target_has_pending"));
            return 0;
        }
        if (result.status() == TradeRequestManager.CreateStatus.COOLDOWN) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_cooldown", result.cooldownSeconds(), TradeMessages.playerName(target)));
            return 0;
        }

        TradeMessages.info(sender, TradeMessages.trans("securetrade.request_sent", TradeMessages.playerName(target)));
        sender.playSound(SecureTradeSounds.TRADE_REQUEST_SENT, SoundCategory.MASTER, 0.8f, 1.0f);

        Text acceptText = TradeMessages.trans("securetrade.accept_button")
                .setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.trans("securetrade.accept_hover"))));

        Text denyText = TradeMessages.trans("securetrade.deny_button")
                .setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.trans("securetrade.deny_hover"))));

        TradeMessages.sendRaw(target, TradeMessages.format(
                TradeMessages.trans("securetrade.wants_to_trade", TradeMessages.playerName(sender))
                        .append(" ").append(acceptText).append(" ").append(denyText),
                Formatting.YELLOW));

        return 1;
    }

    private static int acceptTrade(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity target = source.getPlayer();

        // Busy Check: is target already trading?
        if (target.currentScreenHandler instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        TradeRequestManager.Request request = TradeRequestManager.takeIncoming(target.getUuid(), now, cooldownMillis);
        if (request == null) {
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        ServerPlayerEntity sender = target.server.getPlayerManager().getPlayer(request.senderId());
        if (sender == null) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.sender_offline"));
            return 0;
        }

        // Busy Check: is sender already trading?
        if (sender.currentScreenHandler instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_target_already_trading", TradeMessages.playerName(sender)));
            return 0;
        }

        // Dimension Restrictions Check at Acceptance
        String targetDim = target.world.getRegistryKey().getValue().toString();
        String senderDim = sender.world.getRegistryKey().getValue().toString();

        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        // Distance Check at Acceptance
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = sender.world.getRegistryKey().equals(target.world.getRegistryKey());
        if (!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_different_dimensions"));
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_different_dimensions"));
            return 0;
        }
        if (sameDimension && maxDist > 0 && sender.squaredDistanceTo(target) > maxDist * maxDist) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_too_far"));
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
            return 0;
        }

        TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
        TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));

        // Open Trade Menu for both players
        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity target = source.getPlayer();

        // Busy Check: is target already trading?
        if (target.currentScreenHandler instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        TradeRequestManager.Request request = TradeRequestManager.takeIncoming(target.getUuid(), now, cooldownMillis);
        if (request == null) {
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        TradeRequestManager.deny(request, now, cooldownMillis);

        ServerPlayerEntity sender = target.server.getPlayerManager().getPlayer(request.senderId());
        if (sender != null) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.target_denied", TradeMessages.playerName(target)));
        }
        TradeMessages.warning(target, TradeMessages.trans("securetrade.trade_denied"));

        return 1;
    }

    private static int showHistory(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();

        TradeHistoryManager.showHistory(player);
        return 1;
    }

    private static int toggleDnd(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        boolean enabled = TradePreferencesManager.toggleDnd(player);
        TradeMessages.info(player, TradeMessages.trans(
                enabled ? "securetrade.dnd.enabled" : "securetrade.dnd.disabled"
        ));
        return 1;
    }

    private static int blockPlayer(ServerCommandSource source, ServerPlayerEntity target) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        if (player.getUuid().equals(target.getUuid())) {
            TradeMessages.warning(player, TradeMessages.trans("securetrade.block.cannot_self"));
            return 0;
        }

        TradePreferencesManager.block(player, target);
        TradeRequestManager.clearFor(player.getUuid(), target.getUuid());
        TradeMessages.success(player, TradeMessages.trans(
                "securetrade.block.added", TradeMessages.playerName(target)
        ));
        return 1;
    }

    private static int unblockPlayer(ServerCommandSource source, String targetName) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        String unblockedName = TradePreferencesManager.unblockByName(player, targetName);
        if (unblockedName == null) {
            TradeMessages.warning(player, TradeMessages.trans("securetrade.block.not_blocked", targetName));
            return 0;
        }

        TradeMessages.success(player, TradeMessages.trans("securetrade.block.removed", unblockedName));
        return 1;
    }

    private static int showBlockedPlayers(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        List<String> blockedPlayers = TradePreferencesManager.getBlockedPlayerNames(player);
        if (blockedPlayers.isEmpty()) {
            TradeMessages.info(player, TradeMessages.trans("securetrade.block.list_empty"));
            return 1;
        }

        TradeMessages.info(player, TradeMessages.trans("securetrade.block.list_title"));
        for (String blockedPlayer : blockedPlayers) {
            TradeMessages.sendRaw(player, TradeMessages.text("- " + blockedPlayer).formatted(Formatting.GRAY));
        }
        return 1;
    }

    public static void clearAll() {
        TradeRequestManager.clearAll();
    }

    public static void pruneExpired() {
        TradeRequestManager.prune(System.currentTimeMillis(), Services.PLATFORM.getTradeCooldownSeconds() * 1000L);
    }
}

