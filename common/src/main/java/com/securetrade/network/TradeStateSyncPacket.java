package com.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

public class TradeStateSyncPacket {
    private final boolean myLock;
    private final boolean otherLock;
    private final int countdownSeconds;
    private final int myXP;
    private final int otherXP;

    public TradeStateSyncPacket(boolean myLock, boolean otherLock, int countdownSeconds, int myXP, int otherXP) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
    }

    public TradeStateSyncPacket(FriendlyByteBuf buf) {
        this.myLock = buf.readBoolean();
        this.otherLock = buf.readBoolean();
        this.countdownSeconds = buf.readVarInt();
        this.myXP = buf.readVarInt();
        this.otherXP = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.myLock);
        buf.writeBoolean(this.otherLock);
        buf.writeVarInt(this.countdownSeconds);
        buf.writeVarInt(this.myXP);
        buf.writeVarInt(this.otherXP);
    }

    public boolean myLock() { return myLock; }
    public boolean otherLock() { return otherLock; }
    public int countdownSeconds() { return countdownSeconds; }
    public int myXP() { return myXP; }
    public int otherXP() { return otherXP; }
}
