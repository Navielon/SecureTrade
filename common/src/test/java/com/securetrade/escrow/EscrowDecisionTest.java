package com.securetrade.escrow;

import com.securetrade.escrow.EscrowDecision.Action;
import com.securetrade.escrow.EscrowDecision.OfferState;
import com.securetrade.escrow.EscrowDecision.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscrowDecisionTest {
    @Test
    void refundsUncommittedTradesFromThePlayersOwnFile() {
        for (EscrowPhase phase : new EscrowPhase[] {EscrowPhase.PREPARED, EscrowPhase.REFUNDING, EscrowPhase.REFUNDED}) {
            assertAction(Action.RESTORE_OFFER, phase, null, OfferState.MATCHING);
            assertAction(Action.RESTORE_OFFER, phase, null, OfferState.MISMATCHED);
        }
        assertAction(Action.MARK_DONE, EscrowPhase.REFUNDING, null, OfferState.NONE);
        assertAction(Action.NONE, EscrowPhase.REFUNDED, null, OfferState.NONE);
    }

    @Test
    void neverRefundsASideThatWasAlreadyDelivered() {
        assertQuarantine(QuarantineReason.INCONSISTENT_STATE, EscrowPhase.REFUNDING, EscrowOutcome.DELIVERED, OfferState.NONE);
        assertQuarantine(QuarantineReason.INCONSISTENT_STATE, EscrowPhase.PREPARED, EscrowOutcome.DELIVERED, OfferState.NONE);
    }

    @Test
    void deliversCommittedTradeOnlyAgainstTheMatchingOffer() {
        assertAction(Action.DELIVER, EscrowPhase.COMMITTING, null, OfferState.MATCHING);
        assertQuarantine(QuarantineReason.OFFER_MISMATCH, EscrowPhase.COMMITTING, null, OfferState.MISMATCHED);
        assertQuarantine(QuarantineReason.OFFER_MISSING, EscrowPhase.COMMITTING, null, OfferState.NONE);
    }

    @Test
    void doesNotDeliverTwice() {
        assertAction(Action.MARK_DONE, EscrowPhase.COMMITTING, EscrowOutcome.DELIVERED, OfferState.NONE);
        assertAction(Action.NONE, EscrowPhase.COMPLETED, EscrowOutcome.DELIVERED, OfferState.NONE);
    }

    @Test
    void quarantinesWhenTheOfferedXpIsGone() {
        Result result = EscrowDecision.decide(EscrowPhase.COMMITTING, null, OfferState.MATCHING, false);
        assertEquals(Action.QUARANTINE, result.action());
        assertEquals(QuarantineReason.INSUFFICIENT_XP, result.reason());
    }

    @Test
    void markerAndOfferTogetherIsInconsistent() {
        for (EscrowPhase phase : new EscrowPhase[] {EscrowPhase.COMMITTING, EscrowPhase.REFUNDING, EscrowPhase.RESOLVING}) {
            assertQuarantine(QuarantineReason.INCONSISTENT_STATE, phase, EscrowOutcome.DELIVERED, OfferState.MATCHING);
        }
    }

    @Test
    void completedTradeIgnoresRestoredBackupsWithoutOffer() {
        assertAction(Action.NONE, EscrowPhase.COMPLETED, null, OfferState.NONE);
        assertAction(Action.DELIVER, EscrowPhase.COMPLETED, null, OfferState.MATCHING);
    }

    @Test
    void quarantineFreezesOffersAndChangesNothing() {
        assertAction(Action.KEEP_FROZEN, EscrowPhase.QUARANTINED, null, OfferState.MATCHING);
        assertAction(Action.KEEP_FROZEN, EscrowPhase.QUARANTINED, null, OfferState.MISMATCHED);
        assertAction(Action.NONE, EscrowPhase.QUARANTINED, null, OfferState.NONE);
    }

    @Test
    void quarantineStillRecordsSidesThatWereAlreadyDelivered() {
        assertAction(Action.MARK_DONE, EscrowPhase.QUARANTINED, EscrowOutcome.DELIVERED, OfferState.NONE);
    }

    @Test
    void appliesAdministratorResolutionOncePerSide() {
        assertAction(Action.APPLY_RESOLUTION, EscrowPhase.RESOLVING, null, OfferState.MATCHING);
        assertAction(Action.APPLY_RESOLUTION, EscrowPhase.RESOLVING, null, OfferState.NONE);
        assertAction(Action.MARK_DONE, EscrowPhase.RESOLVING, EscrowOutcome.REFUNDED, OfferState.NONE);
        assertAction(Action.NONE, EscrowPhase.RESOLVED, EscrowOutcome.DISMISSED, OfferState.NONE);
        assertAction(Action.NONE, EscrowPhase.RESOLVED, null, OfferState.NONE);
    }

    private static void assertAction(Action expected, EscrowPhase phase, EscrowOutcome marker, OfferState offer) {
        assertEquals(expected, EscrowDecision.decide(phase, marker, offer, true).action(),
                () -> phase + " / " + marker + " / " + offer);
    }

    private static void assertQuarantine(QuarantineReason reason, EscrowPhase phase, EscrowOutcome marker, OfferState offer) {
        Result result = EscrowDecision.decide(phase, marker, offer, true);
        assertEquals(Action.QUARANTINE, result.action(), () -> phase + " / " + marker + " / " + offer);
        assertEquals(reason, result.reason());
    }
}
