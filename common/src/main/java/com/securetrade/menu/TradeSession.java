package com.securetrade.menu;

import com.securetrade.platform.Services;
import com.securetrade.TradeLogger;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private boolean isCancelled = false;
    private boolean isFinished = false;

    private int countdownTicks = -1;

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

        // Distance Check
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
            menu1.updateClientState(player1Locked, player2Locked, secs);
        }
        if (player2.containerMenu instanceof TradeMenu menu2) {
            menu2.updateClientState(player2Locked, player1Locked, secs);
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
        // Log transaction
        StringBuilder logMsg = new StringBuilder();
        logMsg.append("Trade completed between ")
              .append(player1.getScoreboardName()).append(" (").append(player1.getUUID()).append(") and ")
              .append(player2.getScoreboardName()).append(" (").append(player2.getUUID()).append(").\n");
        
        logMsg.append("  ").append(player1.getScoreboardName()).append(" offered: ");
        appendInventoryItems(logMsg, inventory1);
        logMsg.append("\n  ").append(player2.getScoreboardName()).append(" offered: ");
        appendInventoryItems(logMsg, inventory2);

        TradeLogger.log(logMsg.toString());

        // Give inv2 to player1
        for (int i = 0; i < inventory2.getContainerSize(); i++) {
            if (!inventory2.getItem(i).isEmpty()) {
                if (!player1.getInventory().add(inventory2.getItem(i))) {
                    player1.drop(inventory2.getItem(i), false);
                }
                inventory2.setItem(i, ItemStack.EMPTY);
            }
        }
        // Give inv1 to player2
        for (int i = 0; i < inventory1.getContainerSize(); i++) {
            if (!inventory1.getItem(i).isEmpty()) {
                if (!player2.getInventory().add(inventory1.getItem(i))) {
                    player2.drop(inventory1.getItem(i), false);
                }
                inventory1.setItem(i, ItemStack.EMPTY);
            }
        }
        
        player1.sendSystemMessage(net.minecraft.network.chat.Component.translatable("securetrade.trade_successful"));
        player2.sendSystemMessage(net.minecraft.network.chat.Component.translatable("securetrade.trade_successful"));
        
        playNotifySound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);

        isFinished = true;
        TradeSessionManager.unregister(this);
        player1.closeContainer();
        player2.closeContainer();
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

        playAbortedSound();

        // Return inv1 to player1
        for (int i = 0; i < inventory1.getContainerSize(); i++) {
            if (!inventory1.getItem(i).isEmpty()) {
                if (!player1.getInventory().add(inventory1.getItem(i))) {
                    player1.drop(inventory1.getItem(i), false);
                }
                inventory1.setItem(i, ItemStack.EMPTY);
            }
        }
        // Return inv2 to player2
        for (int i = 0; i < inventory2.getContainerSize(); i++) {
            if (!inventory2.getItem(i).isEmpty()) {
                if (!player2.getInventory().add(inventory2.getItem(i))) {
                    player2.drop(inventory2.getItem(i), false);
                }
                inventory2.setItem(i, ItemStack.EMPTY);
            }
        }
        
        player1.sendSystemMessage(net.minecraft.network.chat.Component.translatable("securetrade.trade_cancelled"));
        player2.sendSystemMessage(net.minecraft.network.chat.Component.translatable("securetrade.trade_cancelled"));

        TradeSessionManager.unregister(this);

        if (player1.containerMenu instanceof TradeMenu) player1.closeContainer();
        if (player2.containerMenu instanceof TradeMenu) player2.closeContainer();
    }
}
