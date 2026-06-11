package com.securetrade.network;

import net.minecraft.network.PacketBuffer;

public class TradeXPChangePacket {
    private final long xpPoints;

    public TradeXPChangePacket(long xpPoints) {
        this.xpPoints = xpPoints;
    }

    public TradeXPChangePacket(PacketBuffer buf) {
        this.xpPoints = buf.readVarLong();
    }

    public void write(PacketBuffer buf) {
        buf.writeVarLong(this.xpPoints);
    }

    public long xpPoints() {
        return this.xpPoints;
    }
}

