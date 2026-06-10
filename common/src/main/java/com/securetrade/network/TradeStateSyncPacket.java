package com.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

public class TradeStateSyncPacket {
    private final boolean myLock;
    private final boolean otherLock;
    private final int countdownSeconds;
    private final long myXP;
    private final long otherXP;
    private final long otherTotalXP;
    private final String partnerName;

    public TradeStateSyncPacket(boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName) {
        this.myLock = myLock;
        this.otherLock = otherLock;
        this.countdownSeconds = countdownSeconds;
        this.myXP = myXP;
        this.otherXP = otherXP;
        this.otherTotalXP = otherTotalXP;
        this.partnerName = partnerName;
    }

    public TradeStateSyncPacket(FriendlyByteBuf buf) {
        this.myLock = buf.readBoolean();
        this.otherLock = buf.readBoolean();
        this.countdownSeconds = buf.readVarInt();
        this.myXP = buf.readVarLong();
        this.otherXP = buf.readVarLong();
        this.otherTotalXP = buf.readVarLong();
        this.partnerName = buf.readUtf();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.myLock);
        buf.writeBoolean(this.otherLock);
        buf.writeVarInt(this.countdownSeconds);
        buf.writeVarLong(this.myXP);
        buf.writeVarLong(this.otherXP);
        buf.writeVarLong(this.otherTotalXP);
        buf.writeUtf(this.partnerName);
    }

    public boolean myLock() { return myLock; }
    public boolean otherLock() { return otherLock; }
    public int countdownSeconds() { return countdownSeconds; }
    public long myXP() { return myXP; }
    public long otherXP() { return otherXP; }
    public long otherTotalXP() { return otherTotalXP; }
    public String partnerName() { return partnerName; }
}
