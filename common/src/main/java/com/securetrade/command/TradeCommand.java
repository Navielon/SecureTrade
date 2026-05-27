package com.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.securetrade.platform.Services;
import com.securetrade.menu.TradeMenu;
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
        );
    }

    private static int requestTrade(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) return 0;

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.translatable("securetrade.cannot_trade_self"));
            return 0;
        }

        // Busy Check: Check if either player is already trading
        if (sender.containerMenu instanceof TradeMenu) {
            sender.sendSystemMessage(Component.translatable("securetrade.error_already_trading"));
            return 0;
        }
        if (target.containerMenu instanceof TradeMenu) {
            sender.sendSystemMessage(Component.translatable("securetrade.error_target_already_trading", target.getScoreboardName()));
            return 0;
        }

        // Distance Check
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!sender.level().dimension().equals(target.level().dimension())) {
                sender.sendSystemMessage(Component.translatable("securetrade.error_different_dimensions"));
                return 0;
            }
            double distSq = sender.distanceToSqr(target);
            if (distSq > maxDist * maxDist) {
                sender.sendSystemMessage(Component.translatable("securetrade.error_too_far"));
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
                sender.sendSystemMessage(Component.translatable("securetrade.error_already_requested"));
            } else {
                sender.sendSystemMessage(Component.translatable("securetrade.error_target_has_pending"));
            }
            return 0;
        }

        // Cooldown Check: Is the sender on cooldown towards target?
        CooldownKey cooldownKey = new CooldownKey(sender.getUUID(), target.getUUID());
        Long cooldownExpire = tradeCooldowns.get(cooldownKey);
        if (cooldownExpire != null) {
            if (now < cooldownExpire) {
                int secsLeft = (int) Math.ceil((cooldownExpire - now) / 1000.0);
                sender.sendSystemMessage(Component.translatable("securetrade.error_cooldown", secsLeft, target.getScoreboardName()));
                return 0;
            } else {
                tradeCooldowns.remove(cooldownKey);
            }
        }

        // Mutual Request Detection: Does target have a pending request towards sender?
        TradeRequest senderPending = pendingRequests.get(sender.getUUID());
        if (senderPending != null && senderPending.senderId.equals(target.getUUID()) && now <= senderPending.expirationTime) {
            pendingRequests.remove(sender.getUUID());
            target.sendSystemMessage(Component.translatable("securetrade.trade_accepted"));
            sender.sendSystemMessage(Component.translatable("securetrade.target_accepted", target.getScoreboardName()));
            TradeMenu.openTrade(sender, target);
            return 1;
        }

        // Create new request
        long expireAt = now + (Services.PLATFORM.getRequestTimeoutSeconds() * 1000L);
        pendingRequests.put(target.getUUID(), new TradeRequest(sender.getUUID(), expireAt));

        sender.sendSystemMessage(Component.translatable("securetrade.request_sent", target.getScoreboardName()));

        Component acceptText = Component.translatable("securetrade.accept_button")
                .withStyle(Style.EMPTY.withColor(0x00FF00)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("securetrade.accept_hover"))));

        Component denyText = Component.translatable("securetrade.deny_button")
                .withStyle(Style.EMPTY.withColor(0xFF0000)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("securetrade.deny_hover"))));

        target.sendSystemMessage(Component.translatable("securetrade.wants_to_trade", sender.getScoreboardName())
                .append(" ").append(acceptText).append(" ").append(denyText));

        return 1;
    }

    private static int acceptTrade(CommandSourceStack source) {
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;

        // Busy Check: is target already trading?
        if (target.containerMenu instanceof TradeMenu) {
            target.sendSystemMessage(Component.translatable("securetrade.error_already_trading"));
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
            target.sendSystemMessage(Component.translatable("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId);
        if (sender == null) {
            target.sendSystemMessage(Component.translatable("securetrade.sender_offline"));
            return 0;
        }

        // Busy Check: is sender already trading?
        if (sender.containerMenu instanceof TradeMenu) {
            target.sendSystemMessage(Component.translatable("securetrade.error_target_already_trading", sender.getScoreboardName()));
            return 0;
        }

        // Distance Check at Acceptance
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!sender.level().dimension().equals(target.level().dimension()) || 
                sender.distanceToSqr(target) > maxDist * maxDist) {
                target.sendSystemMessage(Component.translatable("securetrade.error_too_far"));
                sender.sendSystemMessage(Component.translatable("securetrade.error_too_far"));
                return 0;
            }
        }

        target.sendSystemMessage(Component.translatable("securetrade.trade_accepted"));
        sender.sendSystemMessage(Component.translatable("securetrade.target_accepted", target.getScoreboardName()));

        // Open Trade Menu for both players
        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(CommandSourceStack source) {
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;

        // Busy Check: is target already trading?
        if (target.containerMenu instanceof TradeMenu) {
            target.sendSystemMessage(Component.translatable("securetrade.error_already_trading"));
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
            target.sendSystemMessage(Component.translatable("securetrade.no_pending_requests"));
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
            sender.sendSystemMessage(Component.translatable("securetrade.target_denied", target.getScoreboardName()));
        }
        target.sendSystemMessage(Component.translatable("securetrade.trade_denied"));

        return 1;
    }
}
