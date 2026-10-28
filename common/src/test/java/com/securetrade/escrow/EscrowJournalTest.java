package com.securetrade.escrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowJournalTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @TempDir
    Path root;

    @Test
    void savedRecordsSurviveReload() throws IOException {
        EscrowJournal journal = new EscrowJournal(root);
        EscrowRecord record = trade(EscrowPhase.PREPARED);
        journal.save(record, NOW);
        record.phase = EscrowPhase.COMMITTING;
        record.side(ALICE).outcome = EscrowOutcome.DELIVERED;
        journal.save(record, NOW + 5);

        EscrowJournal reloaded = new EscrowJournal(root);
        EscrowJournal.LoadReport report = reloaded.load();

        assertEquals(1, report.loaded());
        assertTrue(report.quarantinedFiles().isEmpty());
        EscrowRecord loaded = reloaded.get(record.transactionId());
        assertNotNull(loaded);
        assertEquals(EscrowPhase.COMMITTING, loaded.phase);
        assertEquals(2, loaded.revision);
        assertEquals(NOW + 5, loaded.updatedAt);
        assertEquals(EscrowOutcome.DELIVERED, loaded.side(ALICE).outcome);
        assertNull(loaded.side(BOB).outcome);
        assertEquals("{id:\"minecraft:diamond\",count:3}", loaded.side(ALICE).items.get(0).data);
        assertEquals(1, reloaded.forPlayer(BOB).size());
    }

    @Test
    void damagedFilesAreMovedAsideNotDeleted() throws IOException {
        Path transactions = Files.createDirectories(root.resolve("transactions"));
        UUID id = UUID.randomUUID();
        Files.write(transactions.resolve(id + ".json"), "{ not json".getBytes(StandardCharsets.UTF_8));
        Files.write(transactions.resolve(UUID.randomUUID() + ".json"),
                "{\"schema\":99,\"id\":\"x\",\"phase\":\"COMMITTING\",\"sides\":[]}".getBytes(StandardCharsets.UTF_8));

        EscrowJournal journal = new EscrowJournal(root);
        EscrowJournal.LoadReport report = journal.load();

        assertEquals(0, report.loaded());
        assertEquals(2, report.quarantinedFiles().size());
        try (Stream<Path> quarantined = Files.list(root.resolve("quarantine"))) {
            assertEquals(2, quarantined.count());
        }
        try (Stream<Path> remaining = Files.list(transactions)) {
            assertEquals(0, remaining.count());
        }
    }

    @Test
    void recordFileMustMatchItsId() throws IOException {
        EscrowJournal journal = new EscrowJournal(root);
        EscrowRecord record = trade(EscrowPhase.COMMITTING);
        journal.save(record, NOW);
        Path transactions = root.resolve("transactions");
        Files.move(transactions.resolve(record.id + ".json"), transactions.resolve(UUID.randomUUID() + ".json"));

        EscrowJournal.LoadReport report = new EscrowJournal(root).load();

        assertEquals(0, report.loaded());
        assertEquals(1, report.quarantinedFiles().size());
    }

    @Test
    void interruptedWritesAreDiscarded() throws IOException {
        EscrowJournal journal = new EscrowJournal(root);
        EscrowRecord record = trade(EscrowPhase.PREPARED);
        journal.save(record, NOW);
        Files.write(root.resolve("transactions").resolve(record.id + ".json.tmp"), "{ half".getBytes(StandardCharsets.UTF_8));

        EscrowJournal reloaded = new EscrowJournal(root);
        assertEquals(1, reloaded.load().loaded());
        assertEquals(EscrowPhase.PREPARED, reloaded.get(record.transactionId()).phase);
        assertFalse(Files.exists(root.resolve("transactions").resolve(record.id + ".json.tmp")));
    }

    @Test
    void purgesOnlyFinishedRecordsPastRetention() throws IOException {
        EscrowJournal journal = new EscrowJournal(root);
        EscrowRecord oldCompleted = trade(EscrowPhase.COMPLETED);
        EscrowRecord freshCompleted = trade(EscrowPhase.COMPLETED);
        EscrowRecord oldQuarantined = trade(EscrowPhase.QUARANTINED);
        EscrowRecord oldCommitting = trade(EscrowPhase.COMMITTING);
        journal.save(oldCompleted, NOW);
        journal.save(freshCompleted, NOW + 900);
        journal.save(oldQuarantined, NOW);
        journal.save(oldCommitting, NOW);

        assertEquals(1, journal.purgeFinished(NOW + 1000, 500));

        assertNull(journal.get(oldCompleted.transactionId()));
        assertNotNull(journal.get(freshCompleted.transactionId()));
        assertNotNull(journal.get(oldQuarantined.transactionId()));
        assertNotNull(journal.get(oldCommitting.transactionId()));
        assertEquals(3, new EscrowJournal(root).load().loaded());
    }

    @Test
    void sidesAreLookedUpByPlayer() {
        EscrowRecord record = trade(EscrowPhase.COMMITTING);
        assertEquals("Bob", record.counterpart(ALICE).name);
        assertEquals("Alice", record.counterpart(BOB).name);
        assertFalse(record.allSidesDone());
        record.side(ALICE).outcome = EscrowOutcome.DELIVERED;
        record.side(BOB).outcome = EscrowOutcome.DELIVERED;
        assertTrue(record.allSidesDone());
        assertTrue(record.anySideDelivered());
        assertEquals(EscrowPhase.COMPLETED, EscrowPhase.COMMITTING.terminalPhase());
        assertEquals(EscrowPhase.REFUNDED, EscrowPhase.PREPARED.terminalPhase());
    }

    @Test
    void refundIsOnlyAllowedForTradesThatNeverCommitted() {
        EscrowRecord prepared = trade(EscrowPhase.QUARANTINED);
        prepared.quarantinedFrom = EscrowPhase.REFUNDING;
        assertTrue(prepared.canBeRefunded());

        EscrowRecord committed = trade(EscrowPhase.QUARANTINED);
        committed.quarantinedFrom = EscrowPhase.COMMITTING;
        assertFalse(committed.canBeRefunded());

        EscrowRecord delivered = trade(EscrowPhase.QUARANTINED);
        delivered.quarantinedFrom = EscrowPhase.REFUNDING;
        delivered.side(ALICE).outcome = EscrowOutcome.DELIVERED;
        assertFalse(delivered.canBeRefunded());
    }

    private static EscrowRecord trade(EscrowPhase phase) {
        EscrowRecord record = new EscrowRecord(UUID.randomUUID(), phase, NOW);
        record.sides.add(new EscrowRecord.Side(ALICE, "Alice", 120,
                Collections.singletonList(new EscrowRecord.StoredItem(0, "{id:\"minecraft:diamond\",count:3}"))));
        record.sides.add(new EscrowRecord.Side(BOB, "Bob", 0,
                Collections.singletonList(new EscrowRecord.StoredItem(4, "{id:\"minecraft:emerald\",count:64}"))));
        return record;
    }
}
