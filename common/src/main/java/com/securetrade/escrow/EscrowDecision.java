package com.securetrade.escrow;

/**
 * Decides what to do with one side of a journaled transaction when that player's data is loaded.
 * <p>
 * The inputs come from two places: the journal phase, and the player's own saved file (its marker
 * and its escrowed offer). The marker and the offer are written atomically together with the
 * inventory, so they always describe one consistent moment of that player's state.
 */
public final class EscrowDecision {
    public enum Action {
        /** Nothing to do for this side. */
        NONE,
        /** The side's state is already persisted; only the journal bookkeeping is behind. */
        MARK_DONE,
        /** Return the escrowed offer to its owner. */
        RESTORE_OFFER,
        /** Take the escrowed offer and give the counterpart's offer from the journal. */
        DELIVER,
        /** Apply the administrator's resolution. */
        APPLY_RESOLUTION,
        /** Keep the offer frozen in the player's data until an administrator decides. */
        KEEP_FROZEN,
        QUARANTINE
    }

    public enum OfferState {
        NONE,
        MATCHING,
        MISMATCHED
    }

    public record Result(Action action, QuarantineReason reason) {
        static Result of(Action action) {
            return new Result(action, null);
        }

        static Result quarantine(QuarantineReason reason) {
            return new Result(Action.QUARANTINE, reason);
        }
    }

    private EscrowDecision() {
    }

    /**
     * @param marker the outcome marker found in the player's data for this transaction, or null
     * @param offer  whether the player's data holds an escrowed offer for this transaction and
     *               whether it matches the journal copy
     * @param enoughXp whether the player currently has the XP they offered
     */
    public static Result decide(EscrowPhase phase, EscrowOutcome marker, OfferState offer, boolean enoughXp) {
        if (marker != null && offer != OfferState.NONE && phase != EscrowPhase.QUARANTINED) {
            // A marker is written in the same save that consumes the offer, so both at once is impossible.
            return Result.quarantine(QuarantineReason.INCONSISTENT_STATE);
        }

        return switch (phase) {
            case PREPARED, REFUNDING, REFUNDED -> {
                if (marker == EscrowOutcome.DELIVERED) {
                    yield Result.quarantine(QuarantineReason.INCONSISTENT_STATE);
                }
                if (offer != OfferState.NONE) {
                    yield Result.of(Action.RESTORE_OFFER);
                }
                yield Result.of(phase == EscrowPhase.REFUNDED ? Action.NONE : Action.MARK_DONE);
            }
            case COMMITTING, COMPLETED -> {
                if (marker == EscrowOutcome.DELIVERED) {
                    yield Result.of(phase == EscrowPhase.COMPLETED ? Action.NONE : Action.MARK_DONE);
                }
                if (marker != null) {
                    yield Result.quarantine(QuarantineReason.INCONSISTENT_STATE);
                }
                if (offer == OfferState.MATCHING) {
                    yield enoughXp ? Result.of(Action.DELIVER) : Result.quarantine(QuarantineReason.INSUFFICIENT_XP);
                }
                if (offer == OfferState.MISMATCHED) {
                    yield Result.quarantine(QuarantineReason.OFFER_MISMATCH);
                }
                // A completed trade was confirmed on disk for both players; a file without the
                // marker or the offer can only be an administrator's backup restore.
                yield phase == EscrowPhase.COMPLETED
                        ? Result.of(Action.NONE)
                        : Result.quarantine(QuarantineReason.OFFER_MISSING);
            }
            // A marker still has to reach the journal: an administrator must see that this side
            // already received its part before deciding anything.
            case QUARANTINED -> {
                if (offer != OfferState.NONE) {
                    yield Result.of(Action.KEEP_FROZEN);
                }
                yield Result.of(marker != null ? Action.MARK_DONE : Action.NONE);
            }
            case RESOLVING -> Result.of(marker != null ? Action.MARK_DONE : Action.APPLY_RESOLUTION);
            case RESOLVED -> Result.of(marker == null && offer != OfferState.NONE ? Action.APPLY_RESOLUTION : Action.NONE);
        };
    }
}
