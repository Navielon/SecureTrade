package com.securetrade.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TradeRequestManagerTest {
    private static final long TIMEOUT = 60_000L;
    private static final long COOLDOWN = 10_000L;

    @AfterEach
    void clearState() {
        TradeRequestManager.clearAll();
    }

    @Test
    void participantCannotCreateParallelRequests() {
        UUID sender = UUID.randomUUID();
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();

        assertEquals(TradeRequestManager.CreateStatus.CREATED,
                TradeRequestManager.create(sender, firstTarget, 1_000L, TIMEOUT, COOLDOWN).status());
        assertEquals(TradeRequestManager.CreateStatus.SENDER_BUSY,
                TradeRequestManager.create(sender, secondTarget, 1_001L, TIMEOUT, COOLDOWN).status());
        assertEquals(TradeRequestManager.CreateStatus.TARGET_BUSY,
                TradeRequestManager.create(secondTarget, firstTarget, 1_002L, TIMEOUT, COOLDOWN).status());
    }

    @Test
    void reverseRequestCompletesMutualHandshake() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TradeRequestManager.create(first, second, 1_000L, TIMEOUT, COOLDOWN);

        TradeRequestManager.CreateResult result =
                TradeRequestManager.create(second, first, 1_001L, TIMEOUT, COOLDOWN);

        assertEquals(TradeRequestManager.CreateStatus.MUTUAL, result.status());
        assertNull(TradeRequestManager.takeIncoming(second, 1_002L, COOLDOWN));
    }

    @Test
    void mutualCandidateOnlyMatchesReversePair() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        TradeRequestManager.create(first, second, 1_000L, TIMEOUT, COOLDOWN);

        assertEquals(true, TradeRequestManager.isMutualCandidate(second, first, 1_001L, COOLDOWN));
        assertEquals(false, TradeRequestManager.isMutualCandidate(third, first, 1_001L, COOLDOWN));
    }

    @Test
    void expiredRequestAppliesPairCooldown() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        TradeRequestManager.create(sender, target, 1_000L, 1_000L, COOLDOWN);
        TradeRequestManager.prune(2_001L, COOLDOWN);

        TradeRequestManager.CreateResult result =
                TradeRequestManager.create(sender, target, 2_002L, TIMEOUT, COOLDOWN);
        assertEquals(TradeRequestManager.CreateStatus.COOLDOWN, result.status());
        assertEquals(10, result.cooldownSeconds());
    }

    @Test
    void onlyTargetCanAcceptIncomingRequest() {
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        TradeRequestManager.create(sender, target, 1_000L, TIMEOUT, COOLDOWN);

        assertNull(TradeRequestManager.takeIncoming(sender, 1_001L, COOLDOWN));
        assertEquals(sender, TradeRequestManager.takeIncoming(target, 1_002L, COOLDOWN).senderId());
    }
}
