package com.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.securetrade.platform.Services;
import com.securetrade.menu.TradeMenu;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TradeCommand {

    private static class TradeRequest {
        public final UUID senderId;
        public final long expirationTime;

        public TradeRequest(UUID senderId, long expirationTime) {
            this.senderId = senderId;
            this.expirationTime = expirationTime;
        }
    }

    private static class CooldownKey {
        public final UUID sender;
        public final UUID target;

        public CooldownKey(UUID sender, UUID target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CooldownKey)) return false;
            CooldownKey that = (CooldownKey) o;
            return sender.equals(that.sender) && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            return 31 * sender.hashCode() + target.hashCode();
        }
    }

    // Maps Target Player UUID to Sender Player TradeRequest
    private static final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();

    // Maps CooldownKey to Cooldown Expiration Timestamp (epoch ms)
    private static final Map<CooldownKey, Long> tradeCooldowns = new HashMap<>();

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
        );
    }

    private static int requestTrade(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();

        if (sender.getUUID().equals(target.getUUID())) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.cannot_trade_self"));
            return 0;
        }

        // Busy Check: Check if either player is already trading
        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }
        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_target_already_trading", TradeMessages.playerName(target)));
            return 0;
        }

        // Dimension Restrictions Check
        String senderDim = sender.level.dimension().location().toString();
        String targetDim = target.level.dimension().location().toString();

        if (!isDimensionAllowed(senderDim)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!isDimensionAllowed(targetDim)) {
            TradeMessages.error(sender, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        // Distance Check
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!sender.level.dimension().equals(target.level.dimension())) {
                TradeMessages.error(sender, TradeMessages.trans("securetrade.error_different_dimensions"));
                return 0;
            }
            double distSq = sender.distanceToSqr(target);
            if (distSq > maxDist * maxDist) {
                TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
                return 0;
            }
        }

        long now = System.currentTimeMillis();

        // Prune expired cooldowns
        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());

        // Check and prune target's expired pending requests
        TradeRequest targetPending = pendingRequests.get(target.getUUID());
        if (targetPending != null) {
            if (now > targetPending.expirationTime) {
                pendingRequests.remove(target.getUUID());
                long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
                if (cooldownTime > 0) {
                    tradeCooldowns.put(new CooldownKey(targetPending.senderId, target.getUUID()), targetPending.expirationTime + cooldownTime);
                }
                targetPending = null;
            }
        }

        // If target still has an active pending request from someone else
        if (targetPending != null) {
            if (targetPending.senderId.equals(sender.getUUID())) {
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_already_requested"));
            } else {
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_target_has_pending"));
            }
            return 0;
        }

        // Cooldown Check: Is the sender on cooldown towards target?
        CooldownKey cooldownKey = new CooldownKey(sender.getUUID(), target.getUUID());
        Long cooldownExpire = tradeCooldowns.get(cooldownKey);
        if (cooldownExpire != null) {
            if (now < cooldownExpire) {
                int secsLeft = (int) Math.ceil((cooldownExpire - now) / 1000.0);
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_cooldown", secsLeft, TradeMessages.playerName(target)));
                return 0;
            } else {
                tradeCooldowns.remove(cooldownKey);
            }
        }

        // Mutual Request Detection: Does target have a pending request towards sender?
        TradeRequest senderPending = pendingRequests.get(sender.getUUID());
        if (senderPending != null && senderPending.senderId.equals(target.getUUID()) && now <= senderPending.expirationTime) {
            pendingRequests.remove(sender.getUUID());
            TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
            TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));
            TradeMenu.openTrade(sender, target);
            return 1;
        }

        // Create new request
        long expireAt = now + (Services.PLATFORM.getRequestTimeoutSeconds() * 1000L);
        pendingRequests.put(target.getUUID(), new TradeRequest(sender.getUUID(), expireAt));

        TradeMessages.info(sender, TradeMessages.trans("securetrade.request_sent", TradeMessages.playerName(target)));

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

        // Busy Check: is target already trading?
        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUUID());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUUID());
                long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
                if (cooldownTime > 0) {
                    tradeCooldowns.put(new CooldownKey(request.senderId, target.getUUID()), request.expirationTime + cooldownTime);
                }
            }
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId);
        if (sender == null) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.sender_offline"));
            return 0;
        }

        // Busy Check: is sender already trading?
        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_target_already_trading", TradeMessages.playerName(sender)));
            return 0;
        }

        // Dimension Restrictions Check at Acceptance
        String targetDim = target.level.dimension().location().toString();
        String senderDim = sender.level.dimension().location().toString();

        if (!isDimensionAllowed(targetDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!isDimensionAllowed(senderDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        // Distance Check at Acceptance
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!sender.level.dimension().equals(target.level.dimension()) || 
                sender.distanceToSqr(target) > maxDist * maxDist) {
                TradeMessages.error(target, TradeMessages.trans("securetrade.error_too_far"));
                TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
                return 0;
            }
        }

        TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
        TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));

        // Open Trade Menu for both players
        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer target = source.getPlayerOrException();

        // Busy Check: is target already trading?
        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUUID());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUUID());
                long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
                if (cooldownTime > 0) {
                    tradeCooldowns.put(new CooldownKey(request.senderId, target.getUUID()), request.expirationTime + cooldownTime);
                }
            }
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        // Apply Cooldown
        long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        if (cooldownTime > 0) {
            tradeCooldowns.put(new CooldownKey(request.senderId, target.getUUID()), now + cooldownTime);
        }

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId);
        if (sender != null) {
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.target_denied", TradeMessages.playerName(target)));
        }
        TradeMessages.warning(target, TradeMessages.trans("securetrade.trade_denied"));

        return 1;
    }

    private static boolean isDimensionAllowed(String dimensionId) {
        java.util.List<String> allowed = Services.PLATFORM.getAllowedDimensions();
        java.util.List<String> blocked = Services.PLATFORM.getBlockedDimensions();

        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(dimensionId);
        }
        if (blocked != null && !blocked.isEmpty()) {
            return !blocked.contains(dimensionId);
        }
        return true;
    }

    private static int showHistory(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        TradeHistoryManager.showHistory(player);
        return 1;
    }

    // FIX #4: Clear all cached data on server stop to prevent memory leaks
    public static void clearAll() {
        pendingRequests.clear();
        tradeCooldowns.clear();
    }

    // FIX #4: Periodic cleanup of expired entries (called from server tick)
    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> now > entry.getValue().expirationTime);
        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());
    }
}
