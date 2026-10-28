package com.securetrade.menu;

import com.securetrade.platform.Services;
import com.securetrade.TradeLogger;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeItemValidator;
import com.securetrade.TradeInventoryCapacity;
import com.securetrade.TradeMessages;
import com.securetrade.TradeRules;
import com.securetrade.SecureTradeSounds;
import com.securetrade.XPMath;
import com.securetrade.escrow.EscrowRecord;
import com.securetrade.escrow.PlayerEscrowData;
import com.securetrade.escrow.TradeEscrow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TradeSession {
    public final UUID transactionId = UUID.randomUUID();
    public final ServerPlayer player1;
    public final ServerPlayer player2;
    public final SimpleContainer inventory1;
    public final SimpleContainer inventory2;

    public boolean player1Locked = false;
    public boolean player2Locked = false;
    public long player1XP = 0;
    public long player2XP = 0;
    private boolean isCancelled = false;
    private boolean isFinished = false;
    private boolean prepared = false;

    private int countdownTicks = -1;
    private long pendingPlayer1XP = -1;
    private long pendingPlayer2XP = -1;
    private long player1LastLockTick = Long.MIN_VALUE;
    private long player2LastLockTick = Long.MIN_VALUE;
    private final List<ItemStack> inventory1Snapshot = new ArrayList<>();
    private final List<ItemStack> inventory2Snapshot = new ArrayList<>();

    public TradeSession(ServerPlayer player1, ServerPlayer player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.inventory1 = new SimpleContainer(27);
        this.inventory2 = new SimpleContainer(27);
        refreshSnapshot(inventory1, inventory1Snapshot);
        refreshSnapshot(inventory2, inventory2Snapshot);
        TradeSessionManager.register(this);
    }

    public void onItemsChanged() {
        if (!hasChanged(inventory1, inventory1Snapshot) && !hasChanged(inventory2, inventory2Snapshot)) {
            return;
        }

        refreshSnapshot(inventory1, inventory1Snapshot);
        refreshSnapshot(inventory2, inventory2Snapshot);
        onStateChanged();
    }

    public void onStateChanged() {
        if (player1Locked || player2Locked || countdownTicks > 0) {
            player1Locked = false;
            player2Locked = false;
            countdownTicks = -1;
            playAbortedSound();
        }
        syncState();
    }

    private static boolean hasChanged(SimpleContainer inventory, List<ItemStack> snapshot) {
        if (inventory.getContainerSize() != snapshot.size()) {
            return true;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!ItemStack.matches(inventory.getItem(i), snapshot.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static void refreshSnapshot(SimpleContainer inventory, List<ItemStack> snapshot) {
        snapshot.clear();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            snapshot.add(inventory.getItem(i).copy());
        }
    }

    public void setLocked(ServerPlayer player, boolean locked) {
        if (player != player1 && player != player2) {
            return;
        }
        if (locked && (TradeItemValidator.containsBlacklistedItems(inventory1) || TradeItemValidator.containsBlacklistedItems(inventory2))) {
            Services.PLATFORM.sendBlacklistWarning(player);
            return;
        }
        if (locked && !canFitIncomingOffer(player)) {
            Services.PLATFORM.sendInventoryWarning(player);
            return;
        }

        if (player == player1) {
            if (player1Locked == locked) {
                return;
            }
            long tick = player.level().getGameTime();
            if (player1LastLockTick == tick) {
                return;
            }
            player1LastLockTick = tick;
            player1Locked = locked;
        } else if (player == player2) {
            if (player2Locked == locked) {
                return;
            }
            long tick = player.level().getGameTime();
            if (player2LastLockTick == tick) {
                return;
            }
            player2LastLockTick = tick;
            player2Locked = locked;
        }

        if (player1Locked && player2Locked) {
            countdownTicks = Services.PLATFORM.getCountdownSeconds() * 20;
        } else {
            if (countdownTicks > 0) {
                countdownTicks = -1;
                playAbortedSound();
            }
        }

        syncState();
    }

    public void tick() {
        if (isCancelled || isFinished) return;

        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        if (!TradeRules.isDimensionAllowed(player1.level().dimension().location().toString()) ||
            !TradeRules.isDimensionAllowed(player2.level().dimension().location().toString())) {
            cancelTrade();
            return;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        boolean sameDimension = player1.level().dimension().equals(player2.level().dimension());
        if ((!sameDimension && (!TradeRules.canTradeAcrossDimensions() || maxDist > 0)) ||
            (sameDimension && maxDist > 0 && player1.distanceToSqr(player2) > maxDist * maxDist)) {
            cancelTrade();
            return;
        }

        boolean xpChanged = false;
        if (pendingPlayer1XP >= 0) {
            if (player1XP != pendingPlayer1XP) {
                player1XP = pendingPlayer1XP;
                xpChanged = true;
            }
            pendingPlayer1XP = -1;
        }
        if (pendingPlayer2XP >= 0) {
            if (player2XP != pendingPlayer2XP) {
                player2XP = pendingPlayer2XP;
                xpChanged = true;
            }
            pendingPlayer2XP = -1;
        }
        if (xpChanged) {
            onStateChanged();
        }

        if (countdownTicks > 0) {
            countdownTicks--;
            if (countdownTicks % 20 == 0) {
                int secsRemaining = countdownTicks / 20;
                if (secsRemaining > 0) {
                    playNotifySound(SecureTradeSounds.TRADE_COUNTDOWN_TICK, 1.6f, 1.0f);
                }
                syncState();
            }
            if (countdownTicks == 0) {
                executeTrade();
            }
        }
    }

    void syncState() {
        int secs = countdownTicks == -1 ? -1 : (countdownTicks + 19) / 20;
        if (player1.containerMenu instanceof TradeMenu menu1) {
            menu1.syncToClient(player1Locked, player2Locked, secs, player1XP, player2XP, XPMath.getPlayerXP(player2), player2.getScoreboardName());
        }
        if (player2.containerMenu instanceof TradeMenu menu2) {
            menu2.syncToClient(player2Locked, player1Locked, secs, player2XP, player1XP, XPMath.getPlayerXP(player1), player1.getScoreboardName());
        }
    }

    public void setOfferedXP(ServerPlayer player, long xp) {
        if (xp < 0 || isCancelled || isFinished) {
            return;
        }

        long maxXP = XPMath.getPlayerXP(player);
        long offeredXP = Math.max(0L, Math.min(maxXP, xp));

        if (player == player1) {
            pendingPlayer1XP = offeredXP;
        } else if (player == player2) {
            pendingPlayer2XP = offeredXP;
        }
    }

    private void playNotifySound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player1.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        player2.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    private void playAbortedSound() {
        playNotifySound(SecureTradeSounds.TRADE_CANCEL, 0.9f, 1.0f);
    }

    private void executeTrade() {
        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        if (TradeItemValidator.containsBlacklistedItems(inventory1) || TradeItemValidator.containsBlacklistedItems(inventory2)) {
            cancelTrade();
            return;
        }

        long p1Xp = XPMath.getPlayerXP(player1);
        long p2Xp = XPMath.getPlayerXP(player2);
        if (p1Xp < player1XP || p2Xp < player2XP) {
            cancelTrade();
            return;
        }
        if (!TradeInventoryCapacity.canFit(player1.getInventory(), inventory2) ||
            !TradeInventoryCapacity.canFit(player2.getInventory(), inventory1)) {
            cancelTrade();
            return;
        }

        EscrowRecord escrow = TradeEscrow.prepare(this);
        if (escrow == null) {
            cancelTrade();
            return;
        }
        if (!TradeEscrow.beginCommit(escrow, player1, player2)) {
            cancelTrade();
            TradeEscrow.abort(escrow, player1, player2);
            return;
        }

        // From here on the trade is committed: after a crash, recovery completes it instead of refunding.
        SimpleContainer offered1 = copyOf(inventory1);
        SimpleContainer offered2 = copyOf(inventory2);

        StringBuilder logMsg = new StringBuilder();
        logMsg.append("Trade completed between ")
              .append(player1.getScoreboardName()).append(" (").append(player1.getUUID()).append(") and ")
              .append(player2.getScoreboardName()).append(" (").append(player2.getUUID()).append(").\n");

        logMsg.append("  ").append(player1.getScoreboardName()).append(" offered: ").append(player1XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory1);
        logMsg.append("\n  ").append(player2.getScoreboardName()).append(" offered: ").append(player2XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory2);

        TradeLogger.log(logMsg.toString());

        transferItems(inventory2, player1);
        transferItems(inventory1, player2);
        XPMath.setPlayerXP(player1, p1Xp - player1XP + player2XP);
        XPMath.setPlayerXP(player2, p2Xp - player2XP + player1XP);
        TradeEscrow.markDelivered(player1, escrow);
        TradeEscrow.markDelivered(player2, escrow);

        isFinished = true;
        TradeSessionManager.unregister(this);
        TradeEscrow.complete(escrow, player1, player2);
        TradeHistoryManager.recordTrade(player1, player2, offered1, offered2, player1XP, player2XP);

        TradeMessages.success(player1, Component.translatable("securetrade.trade_successful"));
        TradeMessages.success(player2, Component.translatable("securetrade.trade_successful"));

        player1.sendSystemMessage(
            Component.translatable("securetrade.trade_completed_overlay", player2.getScoreboardName())
                .withStyle(net.minecraft.ChatFormatting.GREEN),
            true
        );
        player2.sendSystemMessage(
            Component.translatable("securetrade.trade_completed_overlay", player1.getScoreboardName())
                .withStyle(net.minecraft.ChatFormatting.GREEN),
            true
        );

        playNotifySound(SecureTradeSounds.TRADE_SUCCESS, 1.0f, 1.0f);

        player1.closeContainer();
        player2.closeContainer();
    }

    private static SimpleContainer copyOf(SimpleContainer container) {
        SimpleContainer copy = new SimpleContainer(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) {
            copy.setItem(i, container.getItem(i).copy());
        }
        return copy;
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }

    /**
     * The player's current offer as it must be saved with their data, or null if this session
     * does not hold anything for them anymore.
     */
    public PlayerEscrowData.Offer escrowOffer(UUID playerId) {
        if (isCancelled || isFinished) {
            return null;
        }
        if (player1.getUUID().equals(playerId)) {
            return PlayerEscrowData.Offer.of(transactionId, prepared, player1XP, inventory1);
        }
        if (player2.getUUID().equals(playerId)) {
            return PlayerEscrowData.Offer.of(transactionId, prepared, player2XP, inventory2);
        }
        return null;
    }

    private void transferItems(SimpleContainer from, ServerPlayer to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                if (!to.getInventory().add(stack)) {
                    to.drop(stack, false);
                }
                from.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private boolean canFitIncomingOffer(ServerPlayer player) {
        SimpleContainer incoming = player == player1 ? inventory2 : inventory1;
        return TradeInventoryCapacity.canFit(player.getInventory(), incoming);
    }

    private static boolean isPlayerOnline(ServerPlayer player) {
        return player.connection != null && !player.hasDisconnected();
    }

    private void appendInventoryItems(StringBuilder sb, SimpleContainer container) {
        boolean first = true;
        sb.append("[");
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                if (!first) sb.append(", ");
                sb.append(stack.getCount()).append("x ").append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                first = false;
            }
        }
        sb.append("]");
    }

    public void cancelTrade() {
        if (isCancelled || isFinished) return;
        isCancelled = true;

        transferItems(inventory1, player1);
        transferItems(inventory2, player2);

        if (isPlayerOnline(player1)) {
            player1.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player1, Component.translatable("securetrade.trade_cancelled"));
            if (player1.containerMenu instanceof TradeMenu) player1.closeContainer();
        }
        if (isPlayerOnline(player2)) {
            player2.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player2, Component.translatable("securetrade.trade_cancelled"));
            if (player2.containerMenu instanceof TradeMenu) player2.closeContainer();
        }

        TradeSessionManager.unregister(this);
    }
}
