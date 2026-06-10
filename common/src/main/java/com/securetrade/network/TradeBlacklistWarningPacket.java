package com.securetrade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeBlacklistWarningPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeBlacklistWarningPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("securetrade", "trade_blacklist_warning"));
    public static final StreamCodec<ByteBuf, TradeBlacklistWarningPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
            },
            buffer -> new TradeBlacklistWarningPacket()
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
