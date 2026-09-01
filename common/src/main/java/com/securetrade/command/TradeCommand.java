package com.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                                .suggests((context, builder) -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    List<String> names = player == null
                                            ? List.of()
                                            : TradePreferencesManager.getBlockedPlayerNames(player);
                                    return SharedSuggestionProvider.suggest(names, builder);
                                })
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

    private static int requestTrade(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) return 0;

        if (sender.getUUID().equals(target.getUUID())) {
            TradeMessages.error(sender, Component.translatable("securetrade.cannot_trade_self"));
            return 0;
        }

        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_already_trading"));
            return 0;
        }
        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_target_already_trading", TradeMessages.playerName(target)));
            return 0;
        }

        if (TradePreferencesManager.isBlocked(sender, target.getUUID())) {
            TradeMessages.warning(sender, Component.translatable("securetrade.error_player_blocked_self"));
            return 0;
        }
        if (TradePreferencesManager.isBlocked(target, sender.getUUID())) {
            TradeMessages.warning(sender, Component.translatable("securetrade.error_player_unavailable"));
            return 0;
        }

        String senderDim = sender.level().dimension().identifier().toString();
        String targetDim = target.level().dimension().identifier().toString();

        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = sender.level().dimension().equals(target.level().dimension());
        if (!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_different_dimensions"));
            return 0;
        }
        if (sameDimension && maxDist > 0 && sender.distanceToSqr(target) > maxDist * maxDist) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_too_far"));
            return 0;
        }

        long now = System.currentTimeMillis();

        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        boolean mutualCandidate = TradeRequestManager.isMutualCandidate(
                sender.getUUID(), target.getUUID(), now, cooldownMillis
        );
        if (!mutualCandidate && TradePreferencesManager.isDnd(target)) {
            TradeMessages.warning(sender, Component.translatable("securetrade.error_target_dnd"));
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
            TradeMessages.success(target, Component.translatable("securetrade.trade_accepted"));
            TradeMessages.success(sender, Component.translatable("securetrade.target_accepted", TradeMessages.playerName(target)));
            TradeMenu.openTrade(sender, target);
            return 1;
        }
        if (result.status() == TradeRequestManager.CreateStatus.SENDER_BUSY) {
            TradeMessages.warning(sender, Component.translatable("securetrade.error_already_requested"));
            return 0;
        }
        if (result.status() == TradeRequestManager.CreateStatus.TARGET_BUSY) {
            TradeMessages.warning(sender, Component.translatable("securetrade.error_target_has_pending"));
            return 0;
        }
        if (result.status() == TradeRequestManager.CreateStatus.COOLDOWN) {
            TradeMessages.warning(sender, Component.translatable(
                    "securetrade.error_cooldown",
                    result.cooldownSeconds(),
                    TradeMessages.playerName(target)
            ));
            return 0;
        }

        TradeMessages.info(sender, Component.translatable("securetrade.request_sent", TradeMessages.playerName(target)));
        sender.level().playSound(null, sender.getX(), sender.getY(), sender.getZ(), SecureTradeSounds.TRADE_REQUEST_SENT, SoundSource.MASTER, 0.8f, 1.0f);

        Component acceptText = Component.translatable("securetrade.accept_button")
                .withStyle(Style.EMPTY.withColor(0x55FF55).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/trade accept"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("securetrade.accept_hover"))));

        Component denyText = Component.translatable("securetrade.deny_button")
                .withStyle(Style.EMPTY.withColor(0xFF5555).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/trade deny"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("securetrade.deny_hover"))));

        target.sendSystemMessage(TradeMessages.format(
                Component.translatable("securetrade.wants_to_trade", TradeMessages.playerName(sender))
                        .append(" ").append(acceptText).append(" ").append(denyText),
                ChatFormatting.YELLOW));

        return 1;
    }

    private static int acceptTrade(CommandSourceStack source) {
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, Component.translatable("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        TradeRequestManager.Request request = TradeRequestManager.takeIncoming(target.getUUID(), now, cooldownMillis);
        if (request == null) {
            TradeMessages.warning(target, Component.translatable("securetrade.no_pending_requests"));
            return 0;
        }

        ServerPlayer sender = target.level().getServer().getPlayerList().getPlayer(request.senderId());
        if (sender == null) {
            TradeMessages.error(target, Component.translatable("securetrade.sender_offline"));
            return 0;
        }

        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, Component.translatable("securetrade.error_target_already_trading", TradeMessages.playerName(sender)));
            return 0;
        }

        String targetDim = target.level().dimension().identifier().toString();
        String senderDim = sender.level().dimension().identifier().toString();

        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(target, Component.translatable("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(target, Component.translatable("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = sender.level().dimension().equals(target.level().dimension());
        if (!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) {
            TradeMessages.error(target, Component.translatable("securetrade.error_different_dimensions"));
            TradeMessages.error(sender, Component.translatable("securetrade.error_different_dimensions"));
            return 0;
        }
        if (sameDimension && maxDist > 0 && sender.distanceToSqr(target) > maxDist * maxDist) {
            TradeMessages.error(target, Component.translatable("securetrade.error_too_far"));
            TradeMessages.error(sender, Component.translatable("securetrade.error_too_far"));
            return 0;
        }

        TradeMessages.success(target, Component.translatable("securetrade.trade_accepted"));
        TradeMessages.success(sender, Component.translatable("securetrade.target_accepted", TradeMessages.playerName(target)));

        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(CommandSourceStack source) {
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, Component.translatable("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        TradeRequestManager.Request request = TradeRequestManager.takeIncoming(target.getUUID(), now, cooldownMillis);
        if (request == null) {
            TradeMessages.warning(target, Component.translatable("securetrade.no_pending_requests"));
            return 0;
        }

        TradeRequestManager.deny(request, now, cooldownMillis);

        ServerPlayer sender = target.level().getServer().getPlayerList().getPlayer(request.senderId());
        if (sender != null) {
            sender.level().playSound(null, sender.getX(), sender.getY(), sender.getZ(), SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(sender, Component.translatable("securetrade.target_denied", TradeMessages.playerName(target)));
        }
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
        TradeMessages.warning(target, Component.translatable("securetrade.trade_denied"));

        return 1;
    }

    private static int showHistory(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        TradeHistoryManager.showHistory(player);
        return 1;
    }

    private static int toggleDnd(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        boolean enabled = TradePreferencesManager.toggleDnd(player);
        TradeMessages.info(player, Component.translatable(
                enabled ? "securetrade.dnd.enabled" : "securetrade.dnd.disabled"
        ));
        return 1;
    }

    private static int blockPlayer(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (player.getUUID().equals(target.getUUID())) {
            TradeMessages.warning(player, Component.translatable("securetrade.block.cannot_self"));
            return 0;
        }

        TradePreferencesManager.block(player, target);
        TradeRequestManager.clearFor(player.getUUID(), target.getUUID());
        TradeMessages.success(player, Component.translatable(
                "securetrade.block.added", TradeMessages.playerName(target)
        ));
        return 1;
    }

    private static int unblockPlayer(CommandSourceStack source, String targetName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        String unblockedName = TradePreferencesManager.unblockByName(player, targetName);
        if (unblockedName == null) {
            TradeMessages.warning(player, Component.translatable(
                    "securetrade.block.not_blocked", targetName
            ));
            return 0;
        }

        TradeMessages.success(player, Component.translatable(
                "securetrade.block.removed", unblockedName
        ));
        return 1;
    }

    private static int showBlockedPlayers(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        List<String> blockedPlayers = TradePreferencesManager.getBlockedPlayerNames(player);
        if (blockedPlayers.isEmpty()) {
            TradeMessages.info(player, Component.translatable("securetrade.block.list_empty"));
            return 1;
        }

        TradeMessages.info(player, Component.translatable("securetrade.block.list_title"));
        for (String blockedPlayer : blockedPlayers) {
            player.sendSystemMessage(Component.literal("- " + blockedPlayer).withStyle(ChatFormatting.GRAY));
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
