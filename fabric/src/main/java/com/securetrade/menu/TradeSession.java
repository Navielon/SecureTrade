package com.securetrade.menu;

import com.securetrade.SecureTradeSounds;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeItemValidator;
import com.securetrade.TradeLogger;
import com.securetrade.TradeMessages;
import com.securetrade.TradeRules;
import com.securetrade.XPMath;
import com.securetrade.platform.Services;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.registry.Registry;

public class TradeSession {
    public final ServerPlayerEntity player1;
    public final ServerPlayerEntity player2;
    public final SimpleInventory inventory1;
    public final SimpleInventory inventory2;

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
        this.inventory1 = new SimpleInventory(TradeMenu.TRADE_SLOTS_COUNT);
        this.inventory2 = new SimpleInventory(TradeMenu.TRADE_SLOTS_COUNT);
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

    private static boolean hasChanged(SimpleInventory inventory, List<ItemStack> snapshot) {
        if (inventory.size() != snapshot.size()) {
            return true;
        }

        for (int i = 0; i < inventory.size(); i++) {
            if (!ItemStack.areEqual(inventory.getStack(i), snapshot.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static void refreshSnapshot(SimpleInventory inventory, List<ItemStack> snapshot) {
        snapshot.clear();
        for (int i = 0; i < inventory.size(); i++) {
            snapshot.add(inventory.getStack(i).copy());
        }
    }

    public void setLocked(ServerPlayerEntity player, boolean locked) {
        if (locked && (TradeItemValidator.containsBlacklistedItems(inventory1) || TradeItemValidator.containsBlacklistedItems(inventory2))) {
            Services.PLATFORM.sendBlacklistWarning(player);
            return;
        }

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
        } else if (countdownTicks > 0) {
            countdownTicks = -1;
            playAbortedSound();
        }

        syncState();
    }

    public void tick() {
        if (isCancelled || isFinished) return;

        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        if (!TradeRules.isDimensionAllowed(player1.world.getRegistryKey().getValue().toString()) ||
            !TradeRules.isDimensionAllowed(player2.world.getRegistryKey().getValue().toString())) {
            cancelTrade();
            return;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!player1.world.getRegistryKey().equals(player2.world.getRegistryKey()) ||
                player1.squaredDistanceTo(player2) > maxDist * maxDist) {
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
        if (player1.currentScreenHandler instanceof TradeMenu) {
            TradeMenu menu1 = (TradeMenu) player1.currentScreenHandler;
            menu1.syncToClient(player1Locked, player2Locked, secs, player1XP, player2XP, XPMath.getPlayerXP(player2), player2.getEntityName());
        }
        if (player2.currentScreenHandler instanceof TradeMenu) {
            TradeMenu menu2 = (TradeMenu) player2.currentScreenHandler;
            menu2.syncToClient(player2Locked, player1Locked, secs, player2XP, player1XP, XPMath.getPlayerXP(player1), player1.getEntityName());
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

    private void playNotifySound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        player1.playSound(sound, SoundCategory.MASTER, volume, pitch);
        player2.playSound(sound, SoundCategory.MASTER, volume, pitch);
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
              .append(player1.getEntityName()).append(" (").append(player1.getUuid()).append(") and ")
              .append(player2.getEntityName()).append(" (").append(player2.getUuid()).append(").\n");

        logMsg.append("  ").append(player1.getEntityName()).append(" offered: ").append(player1XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory1);
        logMsg.append("\n  ").append(player2.getEntityName()).append(" offered: ").append(player2XP).append(" XP, ");
        appendInventoryItems(logMsg, inventory2);

        TradeLogger.log(logMsg.toString());

        transferItems(inventory2, player1);
        transferItems(inventory1, player2);

        TradeMessages.success(player1, TradeMessages.trans("securetrade.trade_successful"));
        TradeMessages.success(player2, TradeMessages.trans("securetrade.trade_successful"));

        player1.sendMessage(TradeMessages.trans("securetrade.trade_completed_overlay", player2.getEntityName()).formatted(Formatting.GREEN), true);
        player2.sendMessage(TradeMessages.trans("securetrade.trade_completed_overlay", player1.getEntityName()).formatted(Formatting.GREEN), true);

        playNotifySound(SecureTradeSounds.TRADE_SUCCESS, 1.0f, 1.0f);

        XPMath.setPlayerXP(player1, p1Xp - player1XP + player2XP);
        XPMath.setPlayerXP(player2, p2Xp - player2XP + player1XP);

        isFinished = true;
        TradeSessionManager.unregister(this);
        player1.closeHandledScreen();
        player2.closeHandledScreen();
    }

    private void transferItems(SimpleInventory from, ServerPlayerEntity to) {
        for (int i = 0; i < from.size(); i++) {
            ItemStack stack = from.getStack(i);
            if (!stack.isEmpty()) {
                if (isPlayerOnline(to)) {
                    if (!to.inventory.insertStack(stack)) {
                        to.dropItem(stack, false, false);
                    }
                } else {
                    ((ServerWorld) to.world).spawnEntity(
                        new ItemEntity(to.world, to.getX(), to.getY(), to.getZ(), stack)
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

        transferItems(inventory1, player1);
        transferItems(inventory2, player2);

        if (isPlayerOnline(player1)) {
            player1.playSound(SecureTradeSounds.TRADE_CANCEL, SoundCategory.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player1, TradeMessages.trans("securetrade.trade_cancelled"));
            if (player1.currentScreenHandler instanceof TradeMenu) player1.closeHandledScreen();
        }
        if (isPlayerOnline(player2)) {
            player2.playSound(SecureTradeSounds.TRADE_CANCEL, SoundCategory.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player2, TradeMessages.trans("securetrade.trade_cancelled"));
            if (player2.currentScreenHandler instanceof TradeMenu) player2.closeHandledScreen();
        }

        TradeSessionManager.unregister(this);
    }
}
