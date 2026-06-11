package com.securetrade.menu;

import com.securetrade.platform.Services;
import com.securetrade.TradeLogger;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeItemValidator;
import com.securetrade.TradeMessages;
import com.securetrade.SecureTradeSounds;
import com.securetrade.XPMath;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TradeSession {
    public final ServerPlayerEntity player1;
    public final ServerPlayerEntity player2;
    public final Inventory inventory1;
    public final Inventory inventory2;

    public boolean player1Locked = false;
    public boolean player2Locked = false;
    public long player1XP = 0;
    public long player2XP = 0;
    private boolean isCancelled = false;
    private boolean isFinished = false;

    private int countdownTicks = -1;
    private long pendingPlayer1XP = -1;
    private long pendingPlayer2XP = -1;
    private final List<ItemStack> inventory1Snapshot = new ArrayList<>();
    private final List<ItemStack> inventory2Snapshot = new ArrayList<>();

    public TradeSession(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.inventory1 = new Inventory(27);
        this.inventory2 = new Inventory(27);
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

    private static boolean hasChanged(Inventory inventory, List<ItemStack> snapshot) {
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

    private static void refreshSnapshot(Inventory inventory, List<ItemStack> snapshot) {
        snapshot.clear();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            snapshot.add(inventory.getItem(i).copy());
        }
    }

    public void setLocked(ServerPlayerEntity player, boolean locked) {
        if (player == player1) {
            if (player1Locked == locked) {
                return;
            }
            player1Locked = locked;
        } else if (player == player2) {
            if (player2Locked == locked) {
                return;
            }
            player2Locked = locked;
        } else {
            return;
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

        // Distance Check: maxDist <= 0 means infinite range (no distance/dimension restriction).
        // Dimension restrictions are handled separately via allowedDimensions/blockedDimensions config.
        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!player1.level.dimension().equals(player2.level.dimension()) ||
                player1.distanceToSqr(player2) > maxDist * maxDist) {
                cancelTrade();
                return;
            }
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
        if (player1.containerMenu instanceof TradeMenu) {
            TradeMenu menu1 = (TradeMenu) player1.containerMenu;
            menu1.syncToClient(player1Locked, player2Locked, secs, player1XP, player2XP, XPMath.getPlayerXP(player2), player2.getScoreboardName());
        }
        if (player2.containerMenu instanceof TradeMenu) {
            TradeMenu menu2 = (TradeMenu) player2.containerMenu;
            menu2.syncToClient(player2Locked, player1Locked, secs, player2XP, player1XP, XPMath.getPlayerXP(player1), player1.getScoreboardName());
        }
    }

    public void setOfferedXP(ServerPlayerEntity player, long xp) {
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

    private void playNotifySound(net.minecraft.util.SoundEvent sound, float volume, float pitch) {
        player1.playNotifySound(sound, SoundCategory.MASTER, volume, pitch);
        player2.playNotifySound(sound, SoundCategory.MASTER, volume, pitch);
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

        TradeHistoryManager.recordTrade(player1, player2, inventory1, inventory2, player1XP, player2XP);

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

        TradeMessages.success(player1, TradeMessages.trans("securetrade.trade_successful"));
        TradeMessages.success(player2, TradeMessages.trans("securetrade.trade_successful"));

        player1.displayClientMessage(
            TradeMessages.trans("securetrade.trade_completed_overlay", player2.getScoreboardName())
                .withStyle(net.minecraft.util.text.TextFormatting.GREEN),
            true
        );
        player2.displayClientMessage(
            TradeMessages.trans("securetrade.trade_completed_overlay", player1.getScoreboardName())
                .withStyle(net.minecraft.util.text.TextFormatting.GREEN),
            true
        );

        playNotifySound(SecureTradeSounds.TRADE_SUCCESS, 1.0f, 1.0f);

        XPMath.setPlayerXP(player1, p1Xp - player1XP + player2XP);
        XPMath.setPlayerXP(player2, p2Xp - player2XP + player1XP);

        isFinished = true;
        TradeSessionManager.unregister(this);
        player1.closeContainer();
        player2.closeContainer();
    }

    /**
     * Transfers items safely even if the recipient disconnected.
     */
    private void transferItems(Inventory from, ServerPlayerEntity to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                if (isPlayerOnline(to)) {
                    if (!to.inventory.add(stack)) {
                        to.drop(stack, false);
                    }
                } else {
                    // Drop items at the last known position if the player disconnected.
                    to.level.addFreshEntity(
                        new net.minecraft.entity.item.ItemEntity(
                            to.level, to.getX(), to.getY(), to.getZ(), stack
                        )
                    );
                }
                from.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isPlayerOnline(ServerPlayerEntity player) {
        return player.connection != null && !player.hasDisconnected();
    }

    private void appendInventoryItems(StringBuilder sb, Inventory container) {
        boolean first = true;
        sb.append("[");
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                if (!first) sb.append(", ");
                sb.append(stack.getCount()).append("x ").append(Registry.ITEM.getKey(stack.getItem()));
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
            player1.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundCategory.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player1, TradeMessages.trans("securetrade.trade_cancelled"));
            if (player1.containerMenu instanceof TradeMenu) player1.closeContainer();
        }
        if (isPlayerOnline(player2)) {
            player2.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundCategory.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player2, TradeMessages.trans("securetrade.trade_cancelled"));
            if (player2.containerMenu instanceof TradeMenu) player2.closeContainer();
        }

        TradeSessionManager.unregister(this);
    }
}





