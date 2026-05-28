package com.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

public class TradeXPChangePacket {
    private final int xpPoints;

    public TradeXPChangePacket(int xpPoints) {
        this.xpPoints = xpPoints;
    }

    public TradeXPChangePacket(FriendlyByteBuf buf) {
        this.xpPoints = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.xpPoints);
    }

    public int xpPoints() {
        return this.xpPoints;
    }
}
