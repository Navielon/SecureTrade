package com.securetrade.network;

import com.securetrade.menu.TradeMenu;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class TradeNetwork {
    public static void register(PayloadRegistrar registrar) {
        registrar.playBidirectional(
                TradeLockPacket.TYPE,
                TradeLockPacket.STREAM_CODEC,
                TradeNetwork::handleLockPacket
        );
        registrar.playBidirectional(
                TradeStateSyncPacket.TYPE,
                TradeStateSyncPacket.STREAM_CODEC,
                TradeNetwork::handleStateSyncPacket
        );
    }

    private static void handleLockPacket(TradeLockPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.setLocked(player, payload.locked());
            }
        });
    }

    private static void handleStateSyncPacket(TradeStateSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.updateClientState(payload.myLock(), payload.otherLock(), payload.countdownSeconds());
            }
        });
    }
}
