package com.securetrade.escrow;

import net.minecraft.item.ItemStack;

/**
 * An escrowed item together with the offer slot it was placed in.
 */
public final class EscrowStack {
    private final int slot;
    private final ItemStack stack;

    public EscrowStack(int slot, ItemStack stack) {
        this.slot = slot;
        this.stack = stack;
    }

    public int slot() {
        return slot;
    }

    public ItemStack stack() {
        return stack;
    }
}
