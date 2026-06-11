package com.securetrade.network;

import net.minecraft.network.PacketByteBuf;

public class TradeXPChangePacket {
    private final long xpPoints;

    public TradeXPChangePacket(long xpPoints) {
        this.xpPoints = xpPoints;
    }

    public TradeXPChangePacket(PacketByteBuf buf) {
        this.xpPoints = buf.readVarLong();
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarLong(this.xpPoints);
    }

    public long xpPoints() {
        return this.xpPoints;
    }
}
