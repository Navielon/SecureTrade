package com.securetrade.escrow;

/**
 * What a side of a transaction ended up with. Stored both in the journal and, as a marker,
 * in the player's own data so that applying it twice is impossible.
 */
public enum EscrowOutcome {
    DELIVERED,
    REFUNDED,
    DISMISSED
}
