package com.securetrade.menu;

import com.securetrade.platform.Services;
import com.securetrade.TradeLogger;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeMessages;
import com.securetrade.XPMath;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

public class TradeSession {
    public final ServerPlayer player1;
    public final ServerPlayer player2;
    public final SimpleContainer inventory1;
    public final SimpleContainer inventory2;

    public boolean player1Locked = false;
    public boolean player2Locked = false;
    public int player1XP = 0;
    public int player2XP = 0;
    private boolean isCancelled = false;
    private boolean isFinished = false;

    private int countdownTicks = -1;
    private long lastPlayer1XPTick = -1;
    private long lastPlayer2XPTick = -1;

    public TradeSession(ServerPlayer player1, ServerPlayer player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.inventory1 = new SimpleContainer(12);
        this.inventory2 = new SimpleContainer(12);
        TradeSessionManager.register(this);
    }

    public void onStateChanged() {
        // Reset locks if items changed
        if (player1Locked || player2Locked || countdownTicks > 0) {
            player1Locked = false;
            player2Locked = false;
            countdownTicks = -1;
            playAbortedSound();
        }
        syncState();
    }

    public void setLocked(ServerPlayer player, boolean locked) {
        if (player == player1) {
            player1Locked = locked;
        } else if (player == player2) {
            player2Locked = locked;
        }

        // Play click sound for both players
        playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);

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
            if (!player1.level().dimension().equals(player2.level().dimension()) ||
                player1.distanceToSqr(player2) > maxDist * maxDist) {
                cancelTrade();
                return;
            }
        }

        // Countdown tick
        if (countdownTicks > 0) {
            countdownTicks--;
            if (countdownTicks % 20 == 0) {
                int secsRemaining = countdownTicks / 20;
                if (secsRemaining > 0) {
                    // Play a tick sound with increasing pitch
                    float pitch = 1.0f + (3.0f - secsRemaining) * 0.2f;
                    playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, pitch);
                }
                syncState();
            }
            if (countdownTicks == 0) {
                executeTrade();
            }
        }
    }

    private void syncState() {
        int secs = countdownTicks == -1 ? -1 : (countdownTicks + 19) / 20;
        if (player1.containerMenu instanceof TradeMenu menu1) {
            menu1.syncToClient(player1Locked, player2Locked, secs, player1XP, player2XP);
        }
        if (player2.containerMenu instanceof TradeMenu menu2) {
            menu2.syncToClient(player2Locked, player1Locked, secs, player2XP, player1XP);
        }
    }

    public void setOfferedXP(ServerPlayer player, int xp) {
        if (xp < 0 || isCancelled || isFinished) {
            return;
        }

        long currentTick = player.server.getTickCount();
        int maxXP = XPMath.getPlayerXP(player);
        int offeredXP = Math.max(0, Math.min(maxXP, xp));

        if (player == player1) {
            if (lastPlayer1XPTick == currentTick) {
                return;
            }
            lastPlayer1XPTick = currentTick;
            if (player1XP != offeredXP) {
                player1XP = offeredXP;
                onStateChanged();
            }
        } else if (player == player2) {
            if (lastPlayer2XPTick == currentTick) {
                return;
            }
            lastPlayer2XPTick = currentTick;
            if (player2XP != offeredXP) {
                player2XP = offeredXP;
                onStateChanged();
            }
        }
    }

    private void playNotifySound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player1.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        player2.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    private void playAbortedSound() {
        playNotifySound(SoundEvents.DISPENSER_FAIL, 1.0f, 1.0f);
    }

    private void executeTrade() {
        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        // Verify blacklist before transferring items
        if (hasBlacklistedItems(inventory1) || hasBlacklistedItems(inventory2)) {
            cancelTrade();
            return;
        }

        // Verify XP before transferring
        int p1Xp = XPMath.getPlayerXP(player1);
        int p2Xp = XPMath.getPlayerXP(player2);
        if (p1Xp < player1XP || p2Xp < player2XP) {
            cancelTrade();
            return;
        }

        // FIX #2: Record trade history BEFORE transferring items (inventories are still full)
        TradeHistoryManager.recordTrade(player1, player2, inventory1, inventory2, player1XP, player2XP);

        // Log transaction
        StringBuilder logMsg = new StringBuilder();
        logMsg.append("Trade completed between ")
              .append(player1.getScoreboardName()).append(" (").append(player1.getUUID()).append(") and ")
              .append(player2.getScoreboardName()).append(" (").append(player2.getUUID()).append(").\n");

        logMsg.append("  ").append(player1.getScoreboardName()).append(" offered: ").append(player1XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory1);
        logMsg.append("\n  ").append(player2.getScoreboardName()).append(" offered: ").append(player2XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory2);

        TradeLogger.log(logMsg.toString());

        // Give inv2 to player1
        transferItems(inventory2, player1);
        // Give inv1 to player2
        transferItems(inventory1, player2);

        TradeMessages.success(player1, Component.translatable("securetrade.trade_successful"));
        TradeMessages.success(player2, Component.translatable("securetrade.trade_successful"));

        playNotifySound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);

        // Deduct and add XP
        XPMath.setPlayerXP(player1, p1Xp - player1XP + player2XP);
        XPMath.setPlayerXP(player2, p2Xp - player2XP + player1XP);

        isFinished = true;
        TradeSessionManager.unregister(this);
        player1.closeContainer();
        player2.closeContainer();
    }

    /**
     * FIX #1: Safely transfers items from a container to a player.
     * Handles cases where the player may have disconnected.
     */
    private void transferItems(SimpleContainer from, ServerPlayer to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                if (isPlayerOnline(to)) {
                    if (!to.getInventory().add(stack)) {
                        to.drop(stack, false);
                    }
                } else {
                    // Player disconnected вЂ” drop items at their last known position
                    to.level().addFreshEntity(
                        new net.minecraft.world.entity.item.ItemEntity(
                            to.level(), to.getX(), to.getY(), to.getZ(), stack
                        )
                    );
                }
                from.setItem(i, ItemStack.EMPTY);
            }
        }
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

    private boolean hasBlacklistedItems(SimpleContainer container) {
        java.util.List<String> blacklist = Services.PLATFORM.getBlacklistedItems();
        if (blacklist == null || blacklist.isEmpty()) return false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (blacklist.contains(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void cancelTrade() {
        if (isCancelled || isFinished) return;
        isCancelled = true;

        // FIX #1: Safely return items, handling disconnected players
        transferItems(inventory1, player1);
        transferItems(inventory2, player2);

        if (isPlayerOnline(player1)) {
            player1.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.MASTER, 1.0f, 1.0f);
            TradeMessages.warning(player1, Component.translatable("securetrade.trade_cancelled"));
            if (player1.containerMenu instanceof TradeMenu) player1.closeContainer();
        }
        if (isPlayerOnline(player2)) {
            player2.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.MASTER, 1.0f, 1.0f);
            TradeMessages.warning(player2, Component.translatable("securetrade.trade_cancelled"));
            if (player2.containerMenu instanceof TradeMenu) player2.closeContainer();
        }

        TradeSessionManager.unregister(this);
    }
}
