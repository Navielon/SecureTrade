package com.securetrade.platform;

import com.securetrade.FabricTradeConfig;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeStateSyncPacket;
import com.securetrade.network.TradeXPChangePacket;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public void sendLockPacket(boolean locked) {
        ClientPlayNetworking.send(new TradeLockPacket(locked));
    }

    @Override
    public void sendXPChangePacket(int xpPoints) {
        ClientPlayNetworking.send(new TradeXPChangePacket(xpPoints));
    }

    @Override
    public void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP) {
        ServerPlayNetworking.send(player, new TradeStateSyncPacket(myLock, otherLock, countdownSeconds, myXP, otherXP));
    }

    @Override
    public boolean containsPlatformContainerItems(ItemStack stack, List<String> blacklist, int depth) {
        return false;
    }

    @Override
    public double getMaxTradeDistance() {
        return FabricTradeConfig.maxTradeDistance;
    }

    @Override
    public int getRequestTimeoutSeconds() {
        return FabricTradeConfig.requestTimeoutSeconds;
    }

    @Override
    public int getTradeCooldownSeconds() {
        return FabricTradeConfig.tradeCooldownSeconds;
    }

    @Override
    public int getCountdownSeconds() {
        return FabricTradeConfig.countdownSeconds;
    }

    @Override
    public boolean isLoggingEnabled() {
        return FabricTradeConfig.enableTradeLogging;
    }

    @Override
    public java.util.List<String> getBlacklistedItems() {
        return FabricTradeConfig.blacklistedItems;
    }

    @Override
    public java.util.List<String> getAllowedDimensions() {
        return FabricTradeConfig.allowedDimensions;
    }

    @Override
    public java.util.List<String> getBlockedDimensions() {
        return FabricTradeConfig.blockedDimensions;
    }

    @Override
    public int getMaxHistoryEntries() {
        return FabricTradeConfig.maxHistoryEntries;
    }
}
