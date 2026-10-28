package com.securetrade.escrow;

public enum EscrowPhase {
    PREPARED,
    COMMITTING,
    COMPLETED,
    REFUNDING,
    REFUNDED,
    QUARANTINED,
    RESOLVING,
    RESOLVED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REFUNDED || this == RESOLVED;
    }

    public EscrowPhase terminalPhase() {
        switch (this) {
            case COMMITTING:
                return COMPLETED;
            case PREPARED:
            case REFUNDING:
                return REFUNDED;
            case RESOLVING:
                return RESOLVED;
            default:
                return this;
        }
    }
}
