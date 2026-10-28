package com.securetrade.escrow;

import com.mojang.logging.LogUtils;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeLogger;
import com.securetrade.TradeMessages;
import com.securetrade.XPMath;
import com.securetrade.menu.TradeSession;
import com.securetrade.menu.TradeSessionManager;
import com.securetrade.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;


import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Crash-safe escrow for trades. See Docs/CRASH_RECOVERY.md for the protocol.
 * <p>
 * Every method runs on the server thread, except the player data hooks, which vanilla may call
 * from a save; those only touch the synchronized player data map.
 */
public final class TradeEscrow {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FINISHED_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(7);
    private static final long MARKER_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(30);
    private static final Map<UUID, PlayerEscrowData> PLAYER_DATA = new HashMap<>();

    private static EscrowJournal journal;
    private static MinecraftServer server;

    public enum ResolveStatus {
        RESOLVED,
        NOT_QUARANTINED,
        REFUND_AFTER_DELIVERY,
        REFUND_AFTER_COMMIT,
        COUNTERPART_UNKNOWN,
        SAVE_FAILED
    }

    public record ResolveResult(ResolveStatus status, int appliedNow, int pending) {
    }

    /**
     * One side of a journaled trade, prepared for display. {@code items} is null when they can no longer be read.
     */
    public record OfferView(String playerName, EscrowOutcome outcome, List<TradeHistoryManager.ItemInfo> items, long xp) {
    }

    /**
     * @param onDisk what the player's file must contain once this update is saved; null if the
     *               player's file already holds the outcome and only the journal is behind
     */
    private record SideUpdate(EscrowRecord record, EscrowRecord.Side side, EscrowOutcome outcome, Predicate<CompoundTag> onDisk) {
    }

    private TradeEscrow() {
    }

    // ---- lifecycle -------------------------------------------------------------------------

    public static void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
        EscrowJournal loaded = new EscrowJournal(startedServer.getWorldPath(LevelResource.ROOT).resolve("securetrade"));
        try {
            EscrowJournal.LoadReport report = loaded.load();
            for (String file : report.quarantinedFiles()) {
                LOGGER.warn("Moved an unreadable Secure Trade escrow record to securetrade/quarantine/{}", file);
            }
            long now = System.currentTimeMillis();
            for (EscrowRecord record : loaded.inPhase(EscrowPhase.PREPARED)) {
                // The commit never became durable, so neither player can have received anything.
                record.phase = EscrowPhase.REFUNDING;
                loaded.save(record, now);
            }
            loaded.purgeFinished(now, FINISHED_RETENTION_MILLIS);
            journal = loaded;
        } catch (IOException e) {
            journal = null;
            LOGGER.error("Failed to open the Secure Trade escrow journal; trading stays disabled until it can be read", e);
            return;
        }

        int quarantined = journal.inPhase(EscrowPhase.QUARANTINED).size();
        if (quarantined > 0) {
            LOGGER.warn("{} Secure Trade transaction(s) are quarantined and wait for an administrator: /trade admin quarantine", quarantined);
        }
    }

    public static void onServerStopped() {
        journal = null;
        server = null;
        synchronized (PLAYER_DATA) {
            PLAYER_DATA.clear();
        }
    }

    public static boolean isAvailable() {
        return journal != null;
    }

    /**
     * A player whose escrow data was written by a newer mod version cannot trade: their live offer
     * could not be saved next to that data.
     */
    public static boolean canTrade(ServerPlayer player) {
        if (journal == null) {
            return false;
        }
        synchronized (PLAYER_DATA) {
            PlayerEscrowData data = PLAYER_DATA.get(player.getUUID());
            return data == null || !data.isUnsupported();
        }
    }

    // ---- player data hooks -----------------------------------------------------------------

    public static void writePlayerData(ServerPlayer player, CompoundTag output) {
        PlayerEscrowData data;
        synchronized (PLAYER_DATA) {
            data = PLAYER_DATA.get(player.getUUID());
        }
        PlayerEscrowData.Offer liveOffer = TradeSessionManager.escrowOffer(player.getUUID());
        if ((data == null || data.isEmpty()) && liveOffer == null) {
            return;
        }
        (data != null ? data : new PlayerEscrowData()).write(output, liveOffer);
    }

    public static void readPlayerData(ServerPlayer player, CompoundTag input) {
        PlayerEscrowData data = PlayerEscrowData.read(input, player.level().getServer().getFixerUpper());
        synchronized (PLAYER_DATA) {
            if (data == null || data.isEmpty()) {
                PLAYER_DATA.remove(player.getUUID());
            } else {
                PLAYER_DATA.put(player.getUUID(), data);
            }
        }
        if (data != null && data.isUnsupported()) {
            LOGGER.error("{} has Secure Trade escrow data from a newer mod version; it is kept untouched", player.getScoreboardName());
        }
    }

    // ---- trade commit ----------------------------------------------------------------------

    /**
     * Step 1: writes both offers to the journal. Returns null if the journal cannot be written;
     * nothing is durable then and the trade can simply be cancelled.
     */
    public static EscrowRecord prepare(TradeSession session) {
        if (journal == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        EscrowRecord record = new EscrowRecord(session.transactionId, EscrowPhase.PREPARED, now);
        record.dataVersion = EscrowItems.currentDataVersion();
        try {
            record.sides.add(side(session.player1, session.player1XP, EscrowItems.fromContainer(session.inventory1)));
            record.sides.add(side(session.player2, session.player2XP, EscrowItems.fromContainer(session.inventory2)));
        } catch (RuntimeException e) {
            LOGGER.error("Failed to serialize the offers of trade {}", record.id, e);
            return null;
        }

        session.setPrepared(true);
        try {
            journal.save(record, now);
        } catch (IOException e) {
            session.setPrepared(false);
            LOGGER.error("Failed to write trade {} to the escrow journal; the trade is cancelled", record.id, e);
            return null;
        }
        CrashPoint.AFTER_PREPARED.reach();
        return record;
    }

    /**
     * Step 2: makes both escrowed offers durable in the players' files, then marks the trade as
     * committed. From that moment it is completed even if the server dies.
     */
    public static boolean beginCommit(EscrowRecord record, ServerPlayer player1, ServerPlayer player2) {
        UUID id = record.transactionId();
        Predicate<CompoundTag> offerOnDisk = file -> PlayerEscrowData.fileHasPreparedOffer(file, id);
        if (!saveAndVerify(player1, offerOnDisk) || !saveAndVerify(player2, offerOnDisk)) {
            LOGGER.error("Could not confirm the escrowed offers of trade {} on disk; the trade is cancelled", record.id);
            return false;
        }
        CrashPoint.AFTER_OFFERS_SAVED.reach();

        record.phase = EscrowPhase.COMMITTING;
        try {
            journal.save(record, System.currentTimeMillis());
        } catch (IOException e) {
            record.phase = EscrowPhase.PREPARED;
            LOGGER.error("Failed to commit trade {} in the escrow journal; the trade is cancelled", record.id, e);
            return false;
        }
        CrashPoint.AFTER_COMMITTING.reach();
        return true;
    }

    /**
     * Records that the delivery to this player happened in memory. The marker is saved with the
     * next write of the player's file, together with the delivered items.
     */
    public static void markDelivered(ServerPlayer player, EscrowRecord record) {
        dataFor(player.getUUID()).putMarker(record.transactionId(), EscrowOutcome.DELIVERED, System.currentTimeMillis());
    }

    /**
     * Step 3: the session must already be finished, so its offers are no longer written.
     */
    public static void complete(EscrowRecord record, ServerPlayer player1, ServerPlayer player2) {
        UUID id = record.transactionId();
        Predicate<CompoundTag> delivered = file -> PlayerEscrowData.fileHasMarker(file, id)
                && !PlayerEscrowData.fileHasOffer(file, id);
        saveOutcome(record, player1, delivered, EscrowOutcome.DELIVERED);
        CrashPoint.AFTER_FIRST_COMPLETION_SAVE.reach();
        saveOutcome(record, player2, delivered, EscrowOutcome.DELIVERED);
        finishIfDone(record);
    }

    /**
     * Called after the session returned both offers to their owners in memory.
     */
    public static void abort(EscrowRecord record, ServerPlayer player1, ServerPlayer player2) {
        long now = System.currentTimeMillis();
        record.phase = EscrowPhase.REFUNDING;
        try {
            journal.save(record, now);
        } catch (IOException e) {
            // Still PREPARED on disk, which the next start turns into a refund as well.
            LOGGER.error("Failed to mark trade {} as refunding", record.id, e);
        }

        UUID id = record.transactionId();
        Predicate<CompoundTag> offerGone = file -> !PlayerEscrowData.fileHasOffer(file, id);
        saveOutcome(record, player1, offerGone, EscrowOutcome.REFUNDED);
        saveOutcome(record, player2, offerGone, EscrowOutcome.REFUNDED);
        finishIfDone(record);
    }

    private static void saveOutcome(EscrowRecord record, ServerPlayer player, Predicate<CompoundTag> expectation, EscrowOutcome outcome) {
        if (saveAndVerify(player, expectation)) {
            record.side(player.getUUID()).outcome = outcome;
        }
    }

    // ---- recovery on login -----------------------------------------------------------------

    public static void onPlayerJoin(ServerPlayer player) {
        if (journal == null) {
            return;
        }
        UUID playerId = player.getUUID();
        PlayerEscrowData data = dataFor(playerId);
        if (data.isUnsupported()) {
            LOGGER.error("Skipping Secure Trade recovery for {}: their escrow data comes from a newer mod version",
                    player.getScoreboardName());
            return;
        }
        long now = System.currentTimeMillis();
        attachToRecordsWithoutJournal(player, data, now);

        List<SideUpdate> updates = new ArrayList<>();
        for (EscrowRecord record : journal.forPlayer(playerId)) {
            SideUpdate update = reconcile(player, data, record, now);
            if (update != null) {
                updates.add(update);
            }
        }

        List<Predicate<CompoundTag>> expectations = new ArrayList<>();
        for (PlayerEscrowData.Offer offer : data.offers()) {
            UUID id = offer.transactionId();
            if (journal.get(id) != null) {
                continue;
            }
            if (!offer.isReadable()) {
                quarantineWithoutJournal(player, offer, QuarantineReason.UNREADABLE_ITEMS, now);
            } else if (offer.prepared()) {
                quarantineWithoutJournal(player, offer, QuarantineReason.JOURNAL_MISSING, now);
            } else {
                // The trade was still open when the server stopped; it was never committed.
                giveItems(player, offer.items());
                data.removeOffer(offer);
                expectations.add(file -> !PlayerEscrowData.fileHasOffer(file, id));
                logRecovery("Returned the open offer of interrupted trade", id, player);
                TradeMessages.success(player, Component.translatable("securetrade.escrow.recovered_refunded"));
            }
        }
        data.pruneMarkers(now - MARKER_RETENTION_MILLIS, id -> journal.get(id) != null);
        cleanup(playerId, data);

        for (SideUpdate update : updates) {
            if (update.onDisk() != null) {
                expectations.add(update.onDisk());
            }
        }
        boolean saved = true;
        if (!expectations.isEmpty()) {
            CrashPoint.DURING_LOGIN_RECOVERY.reach();
            saved = saveAndVerify(player, file -> expectations.stream().allMatch(expectation -> expectation.test(file)));
        }
        for (SideUpdate update : updates) {
            if (saved || update.onDisk() == null) {
                update.side().outcome = update.outcome();
                finishIfDone(update.record());
            }
        }

        notifyAboutQuarantine(player);
    }

    /**
     * A record created because its journal entry was lost knows only the player who logged in
     * first. Every other participant is added as they show up, so the administrator sees who
     * already received what.
     */
    private static void attachToRecordsWithoutJournal(ServerPlayer player, PlayerEscrowData data, long now) {
        for (EscrowRecord record : journal.inPhase(EscrowPhase.QUARANTINED)) {
            if (record.quarantineReason != QuarantineReason.JOURNAL_MISSING || record.involves(player.getUUID())) {
                continue;
            }
            UUID id = record.transactionId();
            PlayerEscrowData.Offer offer = data.offer(id);
            PlayerEscrowData.Marker marker = data.marker(id);
            if (offer == null && marker == null) {
                continue;
            }

            EscrowRecord.Side side = new EscrowRecord.Side(player.getUUID(), player.getScoreboardName(),
                    offer == null ? 0L : offer.xp(),
                    offer == null ? List.of() : EscrowItems.encode(offer.items()));
            side.outcome = marker == null ? null : marker.outcome();
            record.sides.add(side);
            try {
                journal.save(record, now);
            } catch (IOException e) {
                record.sides.remove(side);
                LOGGER.error("Failed to add {} to quarantined trade {}", player.getScoreboardName(), record.id, e);
            }
        }
    }

    private static SideUpdate reconcile(ServerPlayer player, PlayerEscrowData data, EscrowRecord record, long now) {
        UUID playerId = player.getUUID();
        UUID id = record.transactionId();
        EscrowRecord.Side side = record.side(playerId);
        PlayerEscrowData.Marker marker = data.marker(id);
        PlayerEscrowData.Offer offer = data.offer(id);

        if (offer != null && !offer.isReadable() && record.phase != EscrowPhase.QUARANTINED) {
            quarantine(record, QuarantineReason.UNREADABLE_ITEMS, side.name, now);
            return null;
        }

        EscrowDecision.OfferState offerState = EscrowDecision.OfferState.NONE;
        if (offer != null) {
            offerState = EscrowDecision.OfferState.MATCHING;
            if (record.phase == EscrowPhase.COMMITTING || record.phase == EscrowPhase.COMPLETED) {
                List<EscrowStack> journaled = journaledItems(record, side);
                if (journaled == null) {
                    quarantine(record, QuarantineReason.UNREADABLE_ITEMS, side.name, now);
                    return null;
                }
                if (!EscrowItems.sameItems(offer.items(), journaled)) {
                    offerState = EscrowDecision.OfferState.MISMATCHED;
                }
            }
        }

        boolean enoughXp = XPMath.getPlayerXP(player) >= side.xp;
        EscrowDecision.Result result = EscrowDecision.decide(record.phase, marker == null ? null : marker.outcome(), offerState, enoughXp);
        return switch (result.action()) {
            case NONE, KEEP_FROZEN -> null;
            case MARK_DONE -> new SideUpdate(record, side, marker != null ? marker.outcome() : EscrowOutcome.REFUNDED, null);
            case RESTORE_OFFER -> {
                giveItems(player, offer.items());
                data.removeOffer(offer);
                logRecovery("Refunded interrupted trade", id, player);
                TradeMessages.success(player, Component.translatable("securetrade.escrow.recovered_refunded"));
                yield new SideUpdate(record, side, EscrowOutcome.REFUNDED, file -> !PlayerEscrowData.fileHasOffer(file, id));
            }
            case DELIVER -> deliver(player, data, record, offer, now);
            case APPLY_RESOLUTION -> applyResolution(player, data, record, now);
            case QUARANTINE -> {
                quarantine(record, result.reason(), side.name, now);
                yield null;
            }
        };
    }

    private static SideUpdate deliver(ServerPlayer player, PlayerEscrowData data, EscrowRecord record,
                                      PlayerEscrowData.Offer offer, long now) {
        EscrowRecord.Side side = record.side(player.getUUID());
        EscrowRecord.Side counterpart = record.counterpart(player.getUUID());
        List<EscrowStack> incoming = journaledItems(record, counterpart);
        if (incoming == null) {
            quarantine(record, QuarantineReason.UNREADABLE_ITEMS, counterpart.name, now);
            return null;
        }

        UUID id = record.transactionId();
        data.removeOffer(offer);
        giveItems(player, incoming);
        XPMath.setPlayerXP(player, XPMath.getPlayerXP(player) - side.xp + counterpart.xp);
        data.putMarker(id, EscrowOutcome.DELIVERED, now);
        logRecovery("Completed interrupted trade", id, player);
        TradeMessages.success(player, Component.translatable("securetrade.escrow.recovered_delivered",
                Component.literal(counterpart.name).withStyle(ChatFormatting.AQUA)));
        return new SideUpdate(record, side, EscrowOutcome.DELIVERED, file -> PlayerEscrowData.fileHasMarker(file, id));
    }

    private static List<EscrowStack> journaledItems(EscrowRecord record, EscrowRecord.Side side) {
        return EscrowItems.decode(server.getFixerUpper(), side.items, record.dataVersion);
    }

    // ---- quarantine and administration -----------------------------------------------------

    public static List<EscrowRecord> quarantined() {
        return journal == null ? List.of() : journal.inPhase(EscrowPhase.QUARANTINED);
    }

    public static List<EscrowRecord> findByIdPrefix(String prefix) {
        if (journal == null) {
            return List.of();
        }
        String normalized = prefix.toLowerCase();
        List<EscrowRecord> matches = new ArrayList<>();
        for (EscrowRecord record : journal.all()) {
            if (record.id.startsWith(normalized)) {
                matches.add(record);
            }
        }
        return matches;
    }

    public static List<OfferView> describe(EscrowRecord record) {
        List<OfferView> offers = new ArrayList<>();
        for (EscrowRecord.Side side : record.sides) {
            List<EscrowStack> items = journaledItems(record, side);
            offers.add(new OfferView(
                    side.name,
                    side.outcome,
                    items == null ? null : EscrowItems.describe(items),
                    side.xp
            ));
        }
        return offers;
    }

    public static ResolveResult resolve(EscrowRecord record, ResolutionAction action, String administrator) {
        if (record.phase != EscrowPhase.QUARANTINED) {
            return new ResolveResult(ResolveStatus.NOT_QUARANTINED, 0, 0);
        }
        if (action == ResolutionAction.REFUND && !record.canBeRefunded()) {
            return new ResolveResult(record.anySideDelivered() ? ResolveStatus.REFUND_AFTER_DELIVERY : ResolveStatus.REFUND_AFTER_COMMIT, 0, 0);
        }
        if (action == ResolutionAction.COMPLETE && record.sides.size() < 2) {
            return new ResolveResult(ResolveStatus.COUNTERPART_UNKNOWN, 0, 0);
        }

        long now = System.currentTimeMillis();
        record.phase = EscrowPhase.RESOLVING;
        record.resolution = new EscrowRecord.Resolution(action, administrator, now);
        try {
            journal.save(record, now);
        } catch (IOException e) {
            record.phase = EscrowPhase.QUARANTINED;
            record.resolution = null;
            LOGGER.error("Failed to save the resolution of trade {}", record.id, e);
            return new ResolveResult(ResolveStatus.SAVE_FAILED, 0, 0);
        }
        String message = "Administrator " + administrator + " resolved quarantined trade " + record.id + " with " + action;
        LOGGER.warn(message);
        TradeLogger.log(message);

        int appliedNow = 0;
        int pending = 0;
        for (EscrowRecord.Side side : record.sides) {
            if (side.isDone()) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(side.playerId());
            if (player == null) {
                pending++;
                continue;
            }
            SideUpdate update = applyResolution(player, dataFor(side.playerId()), record, now);
            if (update != null && (update.onDisk() == null || saveAndVerify(player, update.onDisk()))) {
                side.outcome = update.outcome();
                appliedNow++;
            } else {
                pending++;
            }
        }
        finishIfDone(record);
        return new ResolveResult(ResolveStatus.RESOLVED, appliedNow, pending);
    }

    private static SideUpdate applyResolution(ServerPlayer player, PlayerEscrowData data, EscrowRecord record, long now) {
        UUID id = record.transactionId();
        EscrowRecord.Side side = record.side(player.getUUID());
        PlayerEscrowData.Offer offer = data.offer(id);
        ResolutionAction action = record.resolution.action;

        PlayerEscrowData.Marker marker = data.marker(id);
        if (marker != null) {
            // This side already has an outcome in its own file; applying anything again would duplicate.
            return new SideUpdate(record, side, marker.outcome(), null);
        }

        EscrowOutcome outcome;
        switch (action) {
            case REFUND -> {
                List<EscrowStack> own = offer == null
                        ? journaledItems(record, side)
                        : offer.isReadable() ? offer.items() : null;
                if (own == null) {
                    LOGGER.error("Cannot refund trade {} to {}: the escrowed items are unreadable", id, player.getScoreboardName());
                    return null;
                }
                giveItems(player, own);
                outcome = EscrowOutcome.REFUNDED;
            }
            case COMPLETE -> {
                EscrowRecord.Side counterpart = record.counterpart(player.getUUID());
                List<EscrowStack> incoming = journaledItems(record, counterpart);
                if (incoming == null) {
                    LOGGER.error("Cannot complete trade {} for {}: the journaled items are unreadable", id, player.getScoreboardName());
                    return null;
                }
                giveItems(player, incoming);
                long remainingXp = Math.max(0L, XPMath.getPlayerXP(player) - side.xp);
                XPMath.setPlayerXP(player, remainingXp + counterpart.xp);
                outcome = EscrowOutcome.DELIVERED;
            }
            default -> outcome = EscrowOutcome.DISMISSED;
        }

        if (offer != null) {
            data.removeOffer(offer);
        }
        data.putMarker(id, outcome, now);
        cleanup(player.getUUID(), data);
        logRecovery("Applied the administrator's decision on trade", id, player);
        EscrowRecord.Side counterpart = record.counterpart(player.getUUID());
        TradeMessages.info(player, Component.translatable("securetrade.escrow.resolved",
                Component.literal(counterpart == null ? "?" : counterpart.name).withStyle(ChatFormatting.AQUA)));
        return new SideUpdate(record, side, outcome, file -> PlayerEscrowData.fileHasMarker(file, id));
    }

    private static void quarantine(EscrowRecord record, QuarantineReason reason, String detail, long now) {
        if (record.phase != EscrowPhase.QUARANTINED) {
            record.quarantinedFrom = record.phase;
            record.phase = EscrowPhase.QUARANTINED;
            record.quarantineReason = reason;
            record.quarantineDetail = detail;
            record.quarantinedAt = now;
            try {
                journal.save(record, now);
            } catch (IOException e) {
                LOGGER.error("Failed to save the quarantine of trade {}", record.id, e);
            }
        }
        String message = "Trade " + record.id + " was quarantined (" + reason + ", " + detail + "); inventories are left untouched";
        LOGGER.warn(message);
        TradeLogger.log(message);
        notifyAdministrators();
    }

    private static void quarantineWithoutJournal(ServerPlayer player, PlayerEscrowData.Offer offer, QuarantineReason reason, long now) {
        EscrowRecord record = new EscrowRecord(offer.transactionId(), EscrowPhase.QUARANTINED, now);
        record.dataVersion = EscrowItems.currentDataVersion();
        record.sides.add(new EscrowRecord.Side(player.getUUID(), player.getScoreboardName(), offer.xp(),
                EscrowItems.encode(offer.items())));
        record.quarantineReason = reason;
        record.quarantineDetail = player.getScoreboardName();
        record.quarantinedAt = now;
        try {
            journal.save(record, now);
        } catch (IOException e) {
            // The offer stays frozen in the player's data either way; the next login retries.
            LOGGER.error("Failed to record quarantined trade {}", record.id, e);
            return;
        }
        String message = "Trade " + record.id + " of " + player.getScoreboardName()
                + " has an escrowed offer without a usable journal record (" + reason + "); quarantined";
        LOGGER.warn(message);
        TradeLogger.log(message);
        notifyAdministrators();
    }

    private static void notifyAboutQuarantine(ServerPlayer player) {
        long own = journal.inPhase(EscrowPhase.QUARANTINED).stream()
                .filter(record -> {
                    EscrowRecord.Side side = record.side(player.getUUID());
                    return side != null && !side.isDone();
                })
                .count();
        if (own > 0) {
            TradeMessages.warning(player, Component.translatable("securetrade.escrow.player_quarantined", own));
        }
        if (isAdministrator(player)) {
            sendAdministratorNotice(player);
        }
    }

    private static void notifyAdministrators() {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAdministrator(player)) {
                sendAdministratorNotice(player);
            }
        }
    }

    private static void sendAdministratorNotice(ServerPlayer player) {
        int count = journal.inPhase(EscrowPhase.QUARANTINED).size();
        if (count == 0) {
            return;
        }
        Component show = Component.translatable("securetrade.escrow.admin_show")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade admin quarantine"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/trade admin quarantine"))));
        player.sendSystemMessage(TradeMessages.format(
                Component.translatable("securetrade.escrow.admin_quarantined", count).append(" ").append(show),
                ChatFormatting.YELLOW));
    }

    public static boolean isAdministrator(ServerPlayer player) {
        return player.hasPermissions(3);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static EscrowRecord.Side side(ServerPlayer player, long xp, List<EscrowStack> items) {
        return new EscrowRecord.Side(player.getUUID(), player.getScoreboardName(), xp,
                EscrowItems.encode(items));
    }

    private static void finishIfDone(EscrowRecord record) {
        if (journal == null) {
            return;
        }
        if (record.allSidesDone() && record.phase != EscrowPhase.QUARANTINED) {
            record.phase = record.phase.terminalPhase();
        }
        try {
            journal.save(record, System.currentTimeMillis());
        } catch (IOException e) {
            // The player files already hold the outcome; the next login catches the journal up.
            LOGGER.error("Failed to update escrow record {}", record.id, e);
        }
    }

    /**
     * Forces the player's file to disk and reads it back, so a step only counts as done once it is
     * really there. Vanilla logs and swallows save errors, so the read-back is the only reliable check.
     */
    private static boolean saveAndVerify(ServerPlayer player, Predicate<CompoundTag> expectation) {
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
        try {
            Services.PLATFORM.savePlayerData(server, player);
            if (!Files.exists(file) || !expectation.test(NbtIo.readCompressed(file.toFile()))) {
                return false;
            }
            DurableFiles.force(file);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to save Secure Trade escrow state of {}", player.getScoreboardName(), e);
            return false;
        }
    }

    private static void giveItems(ServerPlayer player, List<EscrowStack> items) {
        for (EscrowStack item : items) {
            ItemStack stack = item.stack().copy();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static PlayerEscrowData dataFor(UUID playerId) {
        synchronized (PLAYER_DATA) {
            return PLAYER_DATA.computeIfAbsent(playerId, ignored -> new PlayerEscrowData());
        }
    }

    private static void cleanup(UUID playerId, PlayerEscrowData data) {
        if (data.isEmpty()) {
            synchronized (PLAYER_DATA) {
                PLAYER_DATA.remove(playerId, data);
            }
        }
    }

    private static void logRecovery(String action, UUID transactionId, ServerPlayer player) {
        String message = action + " " + transactionId + " for " + player.getScoreboardName();
        LOGGER.info(message);
        TradeLogger.log(message);
    }
}
