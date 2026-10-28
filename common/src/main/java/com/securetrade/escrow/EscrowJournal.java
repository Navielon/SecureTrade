package com.securetrade.escrow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable store of escrow records: one JSON file per transaction under {@code <world>/securetrade}.
 * Every write is flushed to disk before the call returns, so a record that was saved survives a crash.
 */
public final class EscrowJournal {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXTENSION = ".json";

    private final Path transactionsDir;
    private final Path quarantineDir;
    private final Map<String, EscrowRecord> records = new LinkedHashMap<>();

    public record LoadReport(int loaded, List<String> quarantinedFiles) {
    }

    public EscrowJournal(Path root) {
        this.transactionsDir = root.resolve("transactions");
        this.quarantineDir = root.resolve("quarantine");
    }

    public LoadReport load() throws IOException {
        records.clear();
        List<String> quarantinedFiles = new ArrayList<>();
        if (!Files.isDirectory(transactionsDir)) {
            return new LoadReport(0, quarantinedFiles);
        }

        try (DirectoryStream<Path> files = Files.newDirectoryStream(transactionsDir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.endsWith(".tmp")) {
                    // An interrupted write; the previous version of the record is still intact.
                    Files.deleteIfExists(file);
                    continue;
                }
                if (!name.endsWith(EXTENSION)) {
                    continue;
                }

                EscrowRecord record = read(file);
                String expectedId = name.substring(0, name.length() - EXTENSION.length());
                if (record == null || !record.isStructurallyValid() || !record.id.equals(expectedId)) {
                    quarantinedFiles.add(moveToQuarantine(file));
                    continue;
                }
                records.put(record.id, record);
            }
        }
        return new LoadReport(records.size(), quarantinedFiles);
    }

    public void save(EscrowRecord record, long now) throws IOException {
        int previousRevision = record.revision;
        long previousUpdatedAt = record.updatedAt;
        record.revision++;
        record.updatedAt = now;
        try {
            DurableFiles.writeAtomically(fileFor(record.id), GSON.toJson(record));
        } catch (IOException e) {
            record.revision = previousRevision;
            record.updatedAt = previousUpdatedAt;
            throw e;
        }
        records.put(record.id, record);
    }

    public EscrowRecord get(UUID transactionId) {
        return records.get(transactionId.toString());
    }

    public Collection<EscrowRecord> all() {
        return Collections.unmodifiableCollection(records.values());
    }

    public List<EscrowRecord> forPlayer(UUID playerId) {
        List<EscrowRecord> result = new ArrayList<>();
        for (EscrowRecord record : records.values()) {
            if (record.involves(playerId)) {
                result.add(record);
            }
        }
        return result;
    }

    public List<EscrowRecord> inPhase(EscrowPhase phase) {
        List<EscrowRecord> result = new ArrayList<>();
        for (EscrowRecord record : records.values()) {
            if (record.phase == phase) {
                result.add(record);
            }
        }
        return result;
    }

    public int purgeFinished(long now, long retentionMillis) throws IOException {
        int removed = 0;
        for (EscrowRecord record : new ArrayList<>(records.values())) {
            if (record.phase.isTerminal() && now - record.updatedAt > retentionMillis) {
                Files.deleteIfExists(fileFor(record.id));
                records.remove(record.id);
                removed++;
            }
        }
        return removed;
    }

    private Path fileFor(String id) {
        return transactionsDir.resolve(id + EXTENSION);
    }

    private static EscrowRecord read(Path file) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), EscrowRecord.class);
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }

    private String moveToQuarantine(Path file) throws IOException {
        Files.createDirectories(quarantineDir);
        Path target = quarantineDir.resolve(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
        return target.getFileName().toString();
    }
}
