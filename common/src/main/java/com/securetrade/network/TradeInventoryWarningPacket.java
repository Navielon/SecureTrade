package com.securetrade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TradeInventoryWarningPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeInventoryWarningPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("securetrade", "trade_inventory_warning"));
    public static final StreamCodec<ByteBuf, TradeInventoryWarningPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
            },
            buffer -> new TradeInventoryWarningPacket()
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
