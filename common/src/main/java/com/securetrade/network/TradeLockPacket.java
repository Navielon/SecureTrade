package com.securetrade.network;

import net.minecraft.network.PacketBuffer;

public class TradeLockPacket {
    private final boolean locked;

    public TradeLockPacket(boolean locked) {
        this.locked = locked;
    }

    public TradeLockPacket(PacketBuffer buf) {
        this.locked = buf.readBoolean();
    }

    public void write(PacketBuffer buf) {
        buf.writeBoolean(this.locked);
    }

    public boolean locked() {
        return this.locked;
    }
}

