package com.securetrade.platform;

import com.securetrade.TradeConfig;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeNetwork;
import com.securetrade.network.TradeStateSyncPacket;
import com.securetrade.network.TradeXPChangePacket;
import net.minecraft.server.level.ServerPlayer;

public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public void sendLockPacket(boolean locked) {
        TradeNetwork.sendToServer(new TradeLockPacket(locked));
    }

    @Override
    public void sendXPChangePacket(int xpPoints) {
        TradeNetwork.sendToServer(new TradeXPChangePacket(xpPoints));
    }

    @Override
    public void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP) {
        TradeNetwork.sendToPlayer(player, new TradeStateSyncPacket(myLock, otherLock, countdownSeconds, myXP, otherXP));
    }

    @Override
    public double getMaxTradeDistance() {
        return TradeConfig.MAX_TRADE_DISTANCE.get();
    }

    @Override
    public int getRequestTimeoutSeconds() {
        return TradeConfig.REQUEST_TIMEOUT_SECONDS.get();
    }

    @Override
    public int getTradeCooldownSeconds() {
        return TradeConfig.TRADE_COOLDOWN_SECONDS.get();
    }

    @Override
    public int getCountdownSeconds() {
        return TradeConfig.COUNTDOWN_SECONDS.get();
    }

    @Override
    public boolean isLoggingEnabled() {
        return TradeConfig.ENABLE_TRADE_LOGGING.get();
    }

    @Override
    public java.util.List<String> getBlacklistedItems() {
        return (java.util.List<String>) TradeConfig.BLACKLISTED_ITEMS.get();
    }

    @Override
    public java.util.List<String> getAllowedDimensions() {
        return (java.util.List<String>) TradeConfig.ALLOWED_DIMENSIONS.get();
    }

    @Override
    public java.util.List<String> getBlockedDimensions() {
        return (java.util.List<String>) TradeConfig.BLOCKED_DIMENSIONS.get();
    }

    @Override
    public int getMaxHistoryEntries() {
        return TradeConfig.MAX_HISTORY_ENTRIES.get();
    }
}
