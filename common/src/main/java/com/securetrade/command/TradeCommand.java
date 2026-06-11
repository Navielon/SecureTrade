package com.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.securetrade.platform.Services;
import com.securetrade.menu.TradeMenu;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeMessages;
import com.securetrade.SecureTradeSounds;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.Style;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;

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

    private static final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();

    private static final Map<CooldownKey, Long> tradeCooldowns = new HashMap<>();

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
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

    private static int requestTrade(CommandSource source, ServerPlayerEntity target) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrException();

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

        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());

        TradeRequest targetPending = pendingRequests.get(target.getUUID());
        if (targetPending != null) {
            if (now > targetPending.expirationTime) {
                pendingRequests.remove(target.getUUID());
                applyExpiredRequestCooldown(target.getUUID(), targetPending);
                targetPending = null;
            }
        }

        if (targetPending != null) {
            if (targetPending.senderId.equals(sender.getUUID())) {
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_already_requested"));
            } else {
                TradeMessages.warning(sender, TradeMessages.trans("securetrade.error_target_has_pending"));
            }
            return 0;
        }

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

        TradeRequest senderPending = pendingRequests.get(sender.getUUID());
        if (senderPending != null && senderPending.senderId.equals(target.getUUID()) && now <= senderPending.expirationTime) {
            pendingRequests.remove(sender.getUUID());
            TradeMessages.success(target, TradeMessages.trans("securetrade.trade_accepted"));
            TradeMessages.success(sender, TradeMessages.trans("securetrade.target_accepted", TradeMessages.playerName(target)));
            TradeMenu.openTrade(sender, target);
            return 1;
        }

        long expireAt = now + (Services.PLATFORM.getRequestTimeoutSeconds() * 1000L);
        pendingRequests.put(target.getUUID(), new TradeRequest(sender.getUUID(), expireAt));

        TradeMessages.info(sender, TradeMessages.trans("securetrade.request_sent", TradeMessages.playerName(target)));
        sender.playNotifySound(SecureTradeSounds.TRADE_REQUEST_SENT, SoundCategory.MASTER, 0.8f, 1.0f);

        ITextComponent acceptText = TradeMessages.trans("securetrade.accept_button")
                .withStyle(Style.EMPTY.withColor(TextFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.trans("securetrade.accept_hover"))));

        ITextComponent denyText = TradeMessages.trans("securetrade.deny_button")
                .withStyle(Style.EMPTY.withColor(TextFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.trans("securetrade.deny_hover"))));

        TradeMessages.sendRaw(target, TradeMessages.format(
                TradeMessages.trans("securetrade.wants_to_trade", TradeMessages.playerName(sender))
                        .append(" ").append(acceptText).append(" ").append(denyText),
                TextFormatting.YELLOW));

        return 1;
    }

    private static int acceptTrade(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity target = source.getPlayerOrException();

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUUID());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUUID());
                applyExpiredRequestCooldown(target.getUUID(), request);
            }
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        ServerPlayerEntity sender = target.server.getPlayerList().getPlayer(request.senderId);
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

        if (!isDimensionAllowed(targetDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!isDimensionAllowed(senderDim)) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_blocked_dimension_target"));
            return 0;
        }

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

        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity target = source.getPlayerOrException();

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, TradeMessages.trans("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUUID());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUUID());
                applyExpiredRequestCooldown(target.getUUID(), request);
            }
            TradeMessages.warning(target, TradeMessages.trans("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        if (cooldownTime > 0) {
            tradeCooldowns.put(new CooldownKey(request.senderId, target.getUUID()), now + cooldownTime);
        }

        ServerPlayerEntity sender = target.server.getPlayerList().getPlayer(request.senderId);
        if (sender != null) {
            sender.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundCategory.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(sender, TradeMessages.trans("securetrade.target_denied", TradeMessages.playerName(target)));
        }
        target.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundCategory.MASTER, 0.9f, 1.0f);
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

    private static int showHistory(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();

        TradeHistoryManager.showHistory(player);
        return 1;
    }

    public static void clearAll() {
        pendingRequests.clear();
        tradeCooldowns.clear();
    }

    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> {
            TradeRequest request = entry.getValue();
            if (now <= request.expirationTime) {
                return false;
            }
            applyExpiredRequestCooldown(entry.getKey(), request);
            return true;
        });
        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());
    }

    private static void applyExpiredRequestCooldown(UUID targetId, TradeRequest request) {
        long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        if (cooldownTime > 0) {
            tradeCooldowns.put(new CooldownKey(request.senderId, targetId), request.expirationTime + cooldownTime);
        }
    }
}





