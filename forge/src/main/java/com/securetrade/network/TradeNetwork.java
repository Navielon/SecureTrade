package com.securetrade.network;

import com.securetrade.menu.TradeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraft.entity.player.ServerPlayerEntity;

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
            ServerPlayerEntity player = context.getSender();
            if (player != null && player.containerMenu instanceof TradeMenu) {
                TradeMenu tradeMenu = (TradeMenu) player.containerMenu;
                tradeMenu.setLocked(player, msg.locked());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleXPChange(TradeXPChangePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayerEntity player = context.getSender();
            if (player != null && player.containerMenu instanceof TradeMenu) {
                TradeMenu tradeMenu = (TradeMenu) player.containerMenu;
                tradeMenu.setOfferedXP(player, msg.xpPoints());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleStateSync(TradeStateSyncPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof TradeMenu) {
                TradeMenu tradeMenu = (TradeMenu) mc.player.containerMenu;
                tradeMenu.updateFields(msg.myLock(), msg.otherLock(), msg.countdownSeconds(), msg.myXP(), msg.otherXP(), msg.otherTotalXP(), msg.partnerName());
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleBlacklistWarning(TradeBlacklistWarningPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu instanceof TradeMenu) {
                TradeMenu tradeMenu = (TradeMenu) mc.player.containerMenu;
                tradeMenu.showBlacklistWarning();
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayerEntity player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}


