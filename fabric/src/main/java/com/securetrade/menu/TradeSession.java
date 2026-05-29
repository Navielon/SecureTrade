package com.securetrade.menu;

import com.securetrade.platform.Services;
import com.securetrade.TradeLogger;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeItemValidator;
import com.securetrade.TradeMessages;
import com.securetrade.XPMath;
import net.minecraft.util.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

public class TradeSession {
    public final ServerPlayerEntity player1;
    public final ServerPlayerEntity player2;
    public final SimpleInventory inventory1;
    public final SimpleInventory inventory2;

    public boolean player1Locked = false;
    public boolean player2Locked = false;
    public int player1XP = 0;
    public int player2XP = 0;
    private boolean isCancelled = false;
    private boolean isFinished = false;

    private int countdownTicks = -1;
    private int pendingPlayer1XP = -1;
    private int pendingPlayer2XP = -1;

    public TradeSession(ServerPlayerEntity player1, ServerPlayerEntity player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.inventory1 = new SimpleInventory(12);
        this.inventory2 = new SimpleInventory(12);
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

    public void setLocked(ServerPlayerEntity player, boolean locked) {
        if (player == player1) {
            player1Locked = locked;
        } else if (player == player2) {
            player2Locked = locked;
        }

        // Play click sound for both players
        playNotifySound(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);

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
            if (!player1.world.getRegistryKey().equals(player2.world.getRegistryKey()) ||
                player1.squaredDistanceTo(player2) > maxDist * maxDist) {
                cancelTrade();
                return;
            }
        }

        // Apply pending XP changes вЂ” always use the last value received this tick
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

        // Countdown tick
        if (countdownTicks > 0) {
            countdownTicks--;
            if (countdownTicks % 20 == 0) {
                int secsRemaining = countdownTicks / 20;
                if (secsRemaining > 0) {
                    // Play a tick sound with increasing pitch
                    float pitch = 1.0f + (3.0f - secsRemaining) * 0.2f;
                    playNotifySound(SoundEvents.UI_BUTTON_CLICK, 1.0f, pitch);
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
        if (player1.currentScreenHandler instanceof TradeMenu) {
            TradeMenu menu1 = (TradeMenu) player1.currentScreenHandler;
            menu1.syncToClient(player1Locked, player2Locked, secs, player1XP, player2XP);
        }
        if (player2.currentScreenHandler instanceof TradeMenu) {
            TradeMenu menu2 = (TradeMenu) player2.currentScreenHandler;
            menu2.syncToClient(player2Locked, player1Locked, secs, player2XP, player1XP);
        }
    }

    public void setOfferedXP(ServerPlayerEntity player, int xp) {
        if (xp < 0 || isCancelled || isFinished) {
            return;
        }
        int maxXP = XPMath.getPlayerXP(player);
        int offeredXP = Math.max(0, Math.min(maxXP, xp));
        if (player == player1) {
            pendingPlayer1XP = offeredXP;
        } else if (player == player2) {
            pendingPlayer2XP = offeredXP;
        }
    }

    private void playNotifySound(SoundEvent sound, float volume, float pitch) {
        player1.playSound(sound, SoundCategory.MASTER, volume, pitch);
        player2.playSound(sound, SoundCategory.MASTER, volume, pitch);
    }

    private void playAbortedSound() {
        playNotifySound(SoundEvents.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
    }

    private void executeTrade() {
        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        // Verify blacklist before transferring items
        if (TradeItemValidator.containsBlacklistedItems(inventory1) || TradeItemValidator.containsBlacklistedItems(inventory2)) {
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
              .append(player1.getEntityName()).append(" (").append(player1.getUuid()).append(") and ")
              .append(player2.getEntityName()).append(" (").append(player2.getUuid()).append(").\n");

        logMsg.append("  ").append(player1.getEntityName()).append(" offered: ").append(player1XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory1);
        logMsg.append("\n  ").append(player2.getEntityName()).append(" offered: ").append(player2XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory2);

        TradeLogger.log(logMsg.toString());

        // Give inv2 to player1
        transferItems(inventory2, player1);
        // Give inv1 to player2
        transferItems(inventory1, player2);

        TradeMessages.success(player1, TradeMessages.trans("securetrade.trade_successful"));
        TradeMessages.success(player2, TradeMessages.trans("securetrade.trade_successful"));

        playNotifySound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Deduct and add XP
        XPMath.setPlayerXP(player1, p1Xp - player1XP + player2XP);
        XPMath.setPlayerXP(player2, p2Xp - player2XP + player1XP);

        isFinished = true;
        TradeSessionManager.unregister(this);
        player1.closeHandledScreen();
        player2.closeHandledScreen();
    }

    /**
     * FIX #1: Safely transfers items from a container to a player.
     * Handles cases where the player may have disconnected.
     */
    private void transferItems(SimpleInventory from, ServerPlayerEntity to) {
        for (int i = 0; i < from.size(); i++) {
            ItemStack stack = from.getStack(i);
            if (!stack.isEmpty()) {
                if (isPlayerOnline(to)) {
                    if (!to.inventory.insertStack(stack)) {
                        to.dropItem(stack, false, false);
                    }
                } else {
                    // Player disconnected РІР‚вЂќ drop items at their last known position
                    ((ServerWorld) to.world).spawnEntity(
                        new net.minecraft.entity.ItemEntity(
                            to.world, to.getX(), to.getY(), to.getZ(), stack
                        )
                    );
                }
                from.setStack(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isPlayerOnline(ServerPlayerEntity player) {
        return player.networkHandler != null && !player.isDisconnected();
    }

    private void appendInventoryItems(StringBuilder sb, SimpleInventory container) {
        boolean first = true;
        sb.append("[");
        for (int i = 0; i < container.size(); i++) {
            ItemStack stack = container.getStack(i);
            if (!stack.isEmpty()) {
                if (!first) sb.append(", ");
                sb.append(stack.getCount()).append("x ").append(Registry.ITEM.getId(stack.getItem()));
                first = false;
            }
        }
        sb.append("]");
    }

    public void cancelTrade() {
        if (isCancelled || isFinished) return;
        isCancelled = true;

        // FIX #1: Safely return items, handling disconnected players
        transferItems(inventory1, player1);
        transferItems(inventory2, player2);

        if (isPlayerOnline(player1)) {
            player1.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.MASTER, 1.0f, 1.0f);
            TradeMessages.warning(player1, TradeMessages.trans("securetrade.trade_cancelled"));
            if (player1.currentScreenHandler instanceof TradeMenu) player1.closeHandledScreen();
        }
        if (isPlayerOnline(player2)) {
            player2.playSound(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.MASTER, 1.0f, 1.0f);
            TradeMessages.warning(player2, TradeMessages.trans("securetrade.trade_cancelled"));
            if (player2.currentScreenHandler instanceof TradeMenu) player2.closeHandledScreen();
        }

        TradeSessionManager.unregister(this);
    }
}
