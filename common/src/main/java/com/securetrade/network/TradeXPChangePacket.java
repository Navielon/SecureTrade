package com.securetrade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeXPChangePacket(int xpPoints) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeXPChangePacket> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("securetrade", "trade_xp_change"));
    public static final StreamCodec<ByteBuf, TradeXPChangePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TradeXPChangePacket::xpPoints,
            TradeXPChangePacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
