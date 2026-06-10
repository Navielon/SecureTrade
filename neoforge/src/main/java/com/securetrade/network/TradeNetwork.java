package com.securetrade.network;

import com.securetrade.menu.TradeMenu;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class TradeNetwork {
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                TradeLockPacket.TYPE,
                TradeLockPacket.STREAM_CODEC,
                TradeNetwork::handleLockPacket
        );
        registrar.playToClient(
                TradeStateSyncPacket.TYPE,
                TradeStateSyncPacket.STREAM_CODEC,
                TradeNetwork::handleStateSyncPacket
        );
        registrar.playToClient(
                TradeBlacklistWarningPacket.TYPE,
                TradeBlacklistWarningPacket.STREAM_CODEC,
                TradeNetwork::handleBlacklistWarningPacket
        );
        registrar.playToServer(
                TradeXPChangePacket.TYPE,
                TradeXPChangePacket.STREAM_CODEC,
                TradeNetwork::handleXPChangePacket
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

    private static void handleXPChangePacket(TradeXPChangePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.setOfferedXP(player, payload.xpPoints());
            }
        });
    }

    private static void handleStateSyncPacket(TradeStateSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.updateFields(payload.myLock(), payload.otherLock(), payload.countdownSeconds(), payload.myXP(), payload.otherXP(), payload.otherTotalXP(), payload.partnerName());
            }
        });
    }

    private static void handleBlacklistWarningPacket(TradeBlacklistWarningPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.showBlacklistWarning();
            }
        });
    }

}
