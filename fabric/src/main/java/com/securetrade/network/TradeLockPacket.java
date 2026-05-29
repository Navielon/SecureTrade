package com.securetrade.network;

import net.minecraft.network.PacketByteBuf;

public class TradeLockPacket {
    private final boolean locked;

    public TradeLockPacket(boolean locked) {
        this.locked = locked;
    }

    public TradeLockPacket(PacketByteBuf buf) {
        this.locked = buf.readBoolean();
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(this.locked);
    }

    public boolean locked() {
        return this.locked;
    }
}
