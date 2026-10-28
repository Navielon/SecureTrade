package com.securetrade.escrow;

import net.minecraft.world.item.ItemStack;

/**
 * An escrowed item together with the offer slot it was placed in.
 */
public record EscrowStack(int slot, ItemStack stack) {
}
