package com.securetrade.platform;

import com.securetrade.FabricSecureTradeMod;
import com.securetrade.FabricTradeConfig;
import com.securetrade.network.TradeBlacklistWarningPacket;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeStateSyncPacket;
import com.securetrade.network.TradeXPChangePacket;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public void sendLockPacket(boolean locked) {
        PacketByteBuf buf = PacketByteBufs.create();
        new TradeLockPacket(locked).write(buf);
        ClientPlayNetworking.send(FabricSecureTradeMod.TRADE_LOCK_ID, buf);
    }

    @Override
    public void sendXPChangePacket(long xpPoints) {
        PacketByteBuf buf = PacketByteBufs.create();
        new TradeXPChangePacket(xpPoints).write(buf);
        ClientPlayNetworking.send(FabricSecureTradeMod.TRADE_XP_CHANGE_ID, buf);
    }

    @Override
    public void sendStateSync(ServerPlayerEntity player, boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName) {
        PacketByteBuf buf = PacketByteBufs.create();
        new TradeStateSyncPacket(myLock, otherLock, countdownSeconds, myXP, otherXP, otherTotalXP, partnerName).write(buf);
        ServerPlayNetworking.send(player, FabricSecureTradeMod.TRADE_STATE_SYNC_ID, buf);
    }

    @Override
    public void sendBlacklistWarning(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        new TradeBlacklistWarningPacket().write(buf);
        ServerPlayNetworking.send(player, FabricSecureTradeMod.TRADE_BLACKLIST_WARNING_ID, buf);
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
