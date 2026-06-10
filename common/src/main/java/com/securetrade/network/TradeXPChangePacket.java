package com.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

public class TradeXPChangePacket {
    private final long xpPoints;

    public TradeXPChangePacket(long xpPoints) {
        this.xpPoints = xpPoints;
    }

    public TradeXPChangePacket(FriendlyByteBuf buf) {
        this.xpPoints = buf.readVarLong();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.xpPoints);
    }

    public long xpPoints() {
        return this.xpPoints;
    }
}
