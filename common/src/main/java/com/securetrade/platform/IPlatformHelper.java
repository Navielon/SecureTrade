package com.securetrade.platform;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface IPlatformHelper {
    void sendLockPacket(boolean locked);

    void sendXPChangePacket(long xpPoints);

    void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName);

    void sendBlacklistWarning(ServerPlayer player);

    boolean containsPlatformContainerItems(ItemStack stack, List<String> blacklist, int depth);

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
