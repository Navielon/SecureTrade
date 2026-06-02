package com.securetrade.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeStateSyncPacket(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TradeStateSyncPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("securetrade", "trade_state_sync"));
    public static final StreamCodec<ByteBuf, TradeStateSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                ByteBufCodecs.BOOL.encode(buffer, payload.myLock());
                ByteBufCodecs.BOOL.encode(buffer, payload.otherLock());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.countdownSeconds());
                ByteBufCodecs.VAR_LONG.encode(buffer, payload.myXP());
                ByteBufCodecs.VAR_LONG.encode(buffer, payload.otherXP());
                ByteBufCodecs.VAR_LONG.encode(buffer, payload.otherTotalXP());
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.partnerName());
            },
            buffer -> new TradeStateSyncPacket(
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)
            )
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
