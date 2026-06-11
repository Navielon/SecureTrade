package com.securetrade.platform;

import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;

public interface IPlatformHelper {
    // Client-to-Server: Send packet when player clicks Lock/Ready
    void sendLockPacket(boolean locked);

    // Client-to-Server: Send packet when player changes offered XP
    void sendXPChangePacket(long xpPoints);

    // Server-to-Client: Sync trade state (locks, countdown, offered XP) to specific player
    void sendStateSync(ServerPlayerEntity player, boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName);

    // Server-to-Client: Tell the client that a blocked item was rejected.
    void sendBlacklistWarning(ServerPlayerEntity player);

    // Loader-specific nested item storage checks, such as Forge item handler capabilities.
    boolean containsPlatformContainerItems(ItemStack stack, List<String> blacklist, int depth);

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
