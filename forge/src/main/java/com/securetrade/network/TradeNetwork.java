package com.securetrade.network;

import com.securetrade.menu.TradeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Supplier;

public class TradeNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("securetrade", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;

        CHANNEL.registerMessage(id++, TradeLockPacket.class,
                TradeLockPacket::write,
                TradeLockPacket::new,
                TradeNetwork::handleLock,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TradeXPChangePacket.class,
                TradeXPChangePacket::write,
                TradeXPChangePacket::new,
                TradeNetwork::handleXPChange,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TradeStateSyncPacket.class,
                TradeStateSyncPacket::write,
                TradeStateSyncPacket::new,
                TradeNetwork::handleStateSync,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TradeBlacklistWarningPacket.class,
                TradeBlacklistWarningPacket::write,
                TradeBlacklistWarningPacket::new,
                TradeNetwork::handleBlacklistWarning,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handleLock(TradeLockPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.setLocked(player, msg.locked());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleXPChange(TradeXPChangePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.setOfferedXP(player, msg.xpPoints());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleStateSync(TradeStateSyncPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.updateFields(msg.myLock(), msg.otherLock(), msg.countdownSeconds(), msg.myXP(), msg.otherXP(), msg.otherTotalXP(), msg.partnerName());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleBlacklistWarning(TradeBlacklistWarningPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof TradeMenu tradeMenu) {
                tradeMenu.showBlacklistWarning();
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
