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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.List;

public class TradeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trade")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> requestTrade(context.getSource(), EntityArgument.getPlayer(context, "target"))))
                .then(Commands.literal("accept")
                        .executes(context -> acceptTrade(context.getSource())))
                .then(Commands.literal("deny")
                        .executes(context -> denyTrade(context.getSource())))
                .then(Commands.literal("history")
                        .executes(context -> showHistory(context.getSource())))
                .then(Commands.literal("dnd")
                        .executes(context -> toggleDnd(context.getSource())))
                .then(Commands.literal("block")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> blockPlayer(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("unblock")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        TradePreferencesManager.getBlockedPlayerNames(context.getSource().getPlayerOrException()),
                                        builder
                                ))
                                .executes(context -> unblockPlayer(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .then(Commands.literal("blocked")
                        .executes(context -> showBlockedPlayers(context.getSource())))
                .then(Commands.literal("blocklist")
                        .executes(context -> showBlockedPlayers(context.getSource())))
        );
    }

    private static int requestTrade(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (sender.getUUID().equals(target.getUUID())) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.cannot_trade_self"));
            return 0;
        }

        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }
        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_target_already_trading", TradeMessages.playerName(target)));
            return 0;
        }

        if (TradePreferencesManager.isBlocked(sender, target.getUUID())) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_player_blocked_self"));
            return 0;
        }
        if (TradePreferencesManager.isBlocked(target, sender.getUUID())) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_player_unavailable"));
            return 0;
        }

        String senderDim = sender.level.dimension().location().toString();
        String targetDim = target.level.dimension().location().toString();

        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = sender.level.dimension().equals(target.level.dimension());
        if (!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_different_dimensions"));
            return 0;
        }
        if (sameDimension && maxDist > 0 && sender.distanceToSqr(target) > maxDist * maxDist) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
            return 0;
        }

        long now = System.currentTimeMillis();

        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        boolean mutualCandidate = TradeRequestManager.isMutualCandidate(
                sender.getUUID(), target.getUUID(), now, cooldownMillis
        );
        if (!mutualCandidate && TradePreferencesManager.isDnd(target)) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_target_dnd"));
            return 0;
        }
        TradeRequestManager.CreateResult result = TradeRequestManager.create(
                sender.getUUID(),
                target.getUUID(),
                now,
                Services.PLATFORM.getRequestTimeoutSeconds() * 1000L,
                cooldownMillis
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
            TradeMessages.warning(sender, TradeMessages.trans(
                    "securetrade.error_cooldown",
                    result.cooldownSeconds(),
                    TradeMessages.playerName(target)
            ));
            return 0;
        }

        TradeMessages.info(sender, TradeMessages.trans("securetrade.request_sent", TradeMessages.playerName(target)));
        sender.playNotifySound(SecureTradeSounds.TRADE_REQUEST_SENT, SoundSource.MASTER, 0.8f, 1.0f);

        Component acceptText = TradeMessages.trans("securetrade.accept_button")
                .withStyle(Style.EMPTY.withColor(0x55FF55).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.trans("securetrade.accept_hover"))));

        Component denyText = TradeMessages.trans("securetrade.deny_button")
                .withStyle(Style.EMPTY.withColor(0xFF5555).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.trans("securetrade.deny_hover"))));

        TradeMessages.sendRaw(target, TradeMessages.format(
                TradeMessages.trans("securetrade.wants_to_trade", TradeMessages.playerName(sender))
                        .append(" ").append(acceptText).append(" ").append(denyText),
                ChatFormatting.YELLOW));

        return 1;
    }

    private static int acceptTrade(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer target = source.getPlayerOrException();

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        TradeRequestManager.Request request = TradeRequestManager.takeIncoming(target.getUUID(), now, cooldownMillis);
        if (request == null) {
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId());
        if (sender == null) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.sender_offline"));
            return 0;
        }

        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_target_already_trading", TradeMessages.playerName(sender)));
            return 0;
        }

        String targetDim = target.level.dimension().location().toString();
        String senderDim = sender.level.dimension().location().toString();

        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = sender.level.dimension().equals(target.level.dimension());
        if (!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_different_dimensions"));
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_different_dimensions"));
            return 0;
        }
        if (sameDimension && maxDist > 0 && sender.distanceToSqr(target) > maxDist * maxDist) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_too_far"));
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
            return 0;
        }

        TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
        TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));

        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer target = source.getPlayerOrException();

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        TradeRequestManager.Request request = TradeRequestManager.takeIncoming(target.getUUID(), now, cooldownMillis);
        if (request == null) {
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        TradeRequestManager.deny(request, now, cooldownMillis);

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId());
        if (sender != null) {
            sender.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.target_denied", TradeMessages.playerName(target)));
        }
        target.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
        TradeMessages.warning(target, TradeMessages.trans("securetrade.trade_denied"));

        return 1;
    }

    private static int showHistory(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        TradeHistoryManager.showHistory(player);
        return 1;
    }

    private static int toggleDnd(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = TradePreferencesManager.toggleDnd(player);
        TradeMessages.info(player, TradeMessages.trans(
                enabled ? "securetrade.dnd.enabled" : "securetrade.dnd.disabled"
        ));
        return 1;
    }

    private static int blockPlayer(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.getUUID().equals(target.getUUID())) {
            TradeMessages.warning(player, TradeMessages.trans("securetrade.block.cannot_self"));
            return 0;
        }

        TradePreferencesManager.block(player, target);
        TradeRequestManager.clearFor(player.getUUID(), target.getUUID());
        TradeMessages.success(player, TradeMessages.trans(
                "securetrade.block.added", TradeMessages.playerName(target)
        ));
        return 1;
    }

    private static int unblockPlayer(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String unblockedName = TradePreferencesManager.unblockByName(player, targetName);
        if (unblockedName == null) {
            TradeMessages.warning(player, TradeMessages.trans("securetrade.block.not_blocked", targetName));
            return 0;
        }

        TradeMessages.success(player, TradeMessages.trans("securetrade.block.removed", unblockedName));
        return 1;
    }

    private static int showBlockedPlayers(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<String> blockedPlayers = TradePreferencesManager.getBlockedPlayerNames(player);
        if (blockedPlayers.isEmpty()) {
            TradeMessages.info(player, TradeMessages.trans("securetrade.block.list_empty"));
            return 1;
        }

        TradeMessages.info(player, TradeMessages.trans("securetrade.block.list_title"));
        for (String blockedPlayer : blockedPlayers) {
            TradeMessages.sendRaw(player, TradeMessages.text("- " + blockedPlayer).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    public static void clearAll() {
        TradeRequestManager.clearAll();
    }

    public static void pruneExpired() {
        TradeRequestManager.prune(
                System.currentTimeMillis(),
                Services.PLATFORM.getTradeCooldownSeconds() * 1000L
        );
    }
}


