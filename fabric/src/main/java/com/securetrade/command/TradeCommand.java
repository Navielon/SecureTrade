package com.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.securetrade.platform.Services;
import com.securetrade.menu.TradeMenu;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeMessages;
import com.securetrade.TradeRules;
import com.securetrade.SecureTradeSounds;
import net.minecraft.util.Formatting;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

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
        if (maxDist > 0) {
            if (!sender.world.getRegistryKey().equals(target.world.getRegistryKey())) {
                TradeMessages.error(sender, TradeMessages.trans("securetrade.error_different_dimensions"));
                return 0;
            }
            double distSq = sender.squaredDistanceTo(target);
            if (distSq > maxDist * maxDist) {
                TradeMessages.error(sender, TradeMessages.trans("securetrade.error_too_far"));
                return 0;
            }
        }

        long now = System.currentTimeMillis();

        // Prune expired cooldowns
        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());

        // Check and prune target's expired pending requests
        TradeRequest targetPending = pendingRequests.get(target.getUuid());
        if (targetPending != null) {
            if (now > targetPending.expirationTime) {
                pendingRequests.remove(target.getUuid());
                long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
                if (cooldownTime > 0) {
                    tradeCooldowns.put(new CooldownKey(targetPending.senderId, target.getUuid()), targetPending.expirationTime + cooldownTime);
                }
                targetPending = null;
            }
        }

        // If target still has an active pending request from someone else
        if (targetPending != null) {
            if (targetPending.senderId.equals(sender.getUuid())) {
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_already_requested"));
            } else {
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_target_has_pending"));
            }
            return 0;
        }

        // Cooldown Check: Is the sender on cooldown towards target?
        CooldownKey cooldownKey = new CooldownKey(sender.getUuid(), target.getUuid());
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
        TradeRequest senderPending = pendingRequests.get(sender.getUuid());
        if (senderPending != null && senderPending.senderId.equals(target.getUuid()) && now <= senderPending.expirationTime) {
            pendingRequests.remove(sender.getUuid());
            TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
            TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));
            TradeMenu.openTrade(sender, target);
            return 1;
        }

        // Create new request
        long expireAt = now + (Services.PLATFORM.getRequestTimeoutSeconds() * 1000L);
        pendingRequests.put(target.getUuid(), new TradeRequest(sender.getUuid(), expireAt));

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
        TradeRequest request = pendingRequests.get(target.getUuid());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUuid());
                long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
                if (cooldownTime > 0) {
                    tradeCooldowns.put(new CooldownKey(request.senderId, target.getUuid()), request.expirationTime + cooldownTime);
                }
            }
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUuid());

        ServerPlayerEntity sender = target.server.getPlayerManager().getPlayer(request.senderId);
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
        if (maxDist > 0) {
            if (!sender.world.getRegistryKey().equals(target.world.getRegistryKey()) || 
                sender.squaredDistanceTo(target) > maxDist * maxDist) {
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

    private static int denyTrade(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity target = source.getPlayer();

        // Busy Check: is target already trading?
        if (target.currentScreenHandler instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUuid());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUuid());
                long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
                if (cooldownTime > 0) {
                    tradeCooldowns.put(new CooldownKey(request.senderId, target.getUuid()), request.expirationTime + cooldownTime);
                }
            }
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUuid());

        // Apply Cooldown
        long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        if (cooldownTime > 0) {
            tradeCooldowns.put(new CooldownKey(request.senderId, target.getUuid()), now + cooldownTime);
        }

        ServerPlayerEntity sender = target.server.getPlayerManager().getPlayer(request.senderId);
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

