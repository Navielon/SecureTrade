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
        return switch (this) {
            case COMMITTING -> COMPLETED;
            case PREPARED, REFUNDING -> REFUNDED;
            case RESOLVING -> RESOLVED;
            default -> this;
        };
    }
}
