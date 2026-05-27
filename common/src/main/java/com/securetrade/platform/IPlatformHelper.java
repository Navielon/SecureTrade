package com.securetrade.platform;

import net.minecraft.server.level.ServerPlayer;

public interface IPlatformHelper {
    // Client-to-Server: Send packet when player clicks Lock/Ready
    void sendLockPacket(boolean locked);

    // Server-to-Client: Sync trade state (locks, countdown) to specific player
    void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds);

    // Config Getters
    double getMaxTradeDistance();
    int getRequestTimeoutSeconds();
    int getTradeCooldownSeconds();
    int getCountdownSeconds();
    boolean isLoggingEnabled();
}
