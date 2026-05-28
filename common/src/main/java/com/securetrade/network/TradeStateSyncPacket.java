package com.securetrade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeStateSyncPacket(boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeStateSyncPacket> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("securetrade", "trade_state_sync"));
    public static final StreamCodec<ByteBuf, TradeStateSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TradeStateSyncPacket::myLock,
            ByteBufCodecs.BOOL, TradeStateSyncPacket::otherLock,
            ByteBufCodecs.VAR_INT, TradeStateSyncPacket::countdownSeconds,
            ByteBufCodecs.VAR_INT, TradeStateSyncPacket::myXP,
            ByteBufCodecs.VAR_INT, TradeStateSyncPacket::otherXP,
            TradeStateSyncPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
