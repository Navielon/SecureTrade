package com.securetrade.network;

import net.minecraft.network.PacketByteBuf;

public class TradeXPChangePacket {
    private final int xpPoints;

    public TradeXPChangePacket(int xpPoints) {
        this.xpPoints = xpPoints;
    }

    public TradeXPChangePacket(PacketByteBuf buf) {
        this.xpPoints = buf.readVarInt();
    }

    public void write(PacketByteBuf buf) {
        buf.writeVarInt(this.xpPoints);
    }

    public int xpPoints() {
        return this.xpPoints;
    }
}
