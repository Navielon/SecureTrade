package com.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

public class TradeLockPacket {
    private final boolean locked;

    public TradeLockPacket(boolean locked) {
        this.locked = locked;
    }

    public TradeLockPacket(FriendlyByteBuf buf) {
        this.locked = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.locked);
    }

    public boolean locked() {
        return this.locked;
    }
}
