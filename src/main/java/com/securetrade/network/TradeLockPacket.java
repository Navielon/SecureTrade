package com.securetrade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeLockPacket(boolean locked) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeLockPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("securetrade", "trade_lock"));
    public static final StreamCodec<ByteBuf, TradeLockPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TradeLockPacket::locked,
            TradeLockPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
