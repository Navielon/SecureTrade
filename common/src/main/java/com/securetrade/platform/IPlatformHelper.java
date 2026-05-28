package com.securetrade.platform;

import net.minecraft.server.level.ServerPlayer;

public interface IPlatformHelper {
    // Client-to-Server: Send packet when player clicks Lock/Ready
    void sendLockPacket(boolean locked);

    // Client-to-Server: Send packet when player changes offered XP
    void sendXPChangePacket(int xpPoints);

    // Server-to-Client: Sync trade state (locks, countdown, offered XP) to specific player
    void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP);

    // Config Getters
    double getMaxTradeDistance();
    int getRequestTimeoutSeconds();
    int getTradeCooldownSeconds();
    int getCountdownSeconds();
    boolean isLoggingEnabled();
    java.util.List<String> getBlacklistedItems();
    java.util.List<String> getAllowedDimensions();
    java.util.List<String> getBlockedDimensions();
    int getMaxHistoryEntries();
}
