package com.securetrade.platform;

import com.securetrade.FabricTradeConfig;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeStateSyncPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public void sendLockPacket(boolean locked) {
        ClientPlayNetworking.send(new TradeLockPacket(locked));
    }

    @Override
    public void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds) {
        ServerPlayNetworking.send(player, new TradeStateSyncPacket(myLock, otherLock, countdownSeconds));
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
}
