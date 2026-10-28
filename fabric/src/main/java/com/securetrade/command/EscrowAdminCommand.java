package com.securetrade.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.securetrade.TradeHistoryManager;
import com.securetrade.TradeMessages;
import com.securetrade.escrow.EscrowOutcome;
import com.securetrade.escrow.EscrowPhase;
import com.securetrade.escrow.EscrowRecord;
import com.securetrade.escrow.QuarantineReason;
import com.securetrade.escrow.ResolutionAction;
import com.securetrade.escrow.TradeEscrow;
import net.minecraft.util.Formatting;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;

import java.util.List;
import java.util.Locale;

/**
 * {@code /trade admin ...}: inspection and resolution of quarantined escrow transactions.
 */
final class EscrowAdminCommand {
    private EscrowAdminCommand() {
    }

    static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("admin")
                .requires(source -> source.hasPermissionLevel(3))
                .then(CommandManager.literal("quarantine")
                        .executes(context -> list(context.getSource())))
                .then(CommandManager.literal("info")
                        .then(transactionArgument()
                                .executes(context -> info(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(CommandManager.literal("refund")
                        .then(transactionArgument()
                                .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), ResolutionAction.REFUND))))
                .then(CommandManager.literal("complete")
                        .then(transactionArgument()
                                .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), ResolutionAction.COMPLETE))))
                .then(CommandManager.literal("dismiss")
                        .then(transactionArgument()
                                .executes(context -> askDismissConfirmation(context.getSource(), StringArgumentType.getString(context, "id")))
                                .then(CommandManager.literal("confirm")
                                        .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), ResolutionAction.DISMISS)))));
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> transactionArgument() {
        return CommandManager.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> CommandSource.suggestMatching(
                        TradeEscrow.quarantined().stream().map(EscrowRecord::shortId),
                        builder
                ));
    }

    private static int list(ServerCommandSource source) {
        List<EscrowRecord> quarantined = TradeEscrow.quarantined();
        if (quarantined.isEmpty()) {
            send(source, TradeMessages.trans("securetrade.admin.list_empty"), Formatting.GRAY);
            return 1;
        }

        send(source, TradeMessages.trans("securetrade.admin.list_title", quarantined.size()), Formatting.GOLD);
        for (EscrowRecord record : quarantined) {
            sendLine(source, TradeMessages.empty()
                    .append(TradeMessages.text("- ").formatted(Formatting.DARK_GRAY))
                    .append(transactionLink(record))
                    .append(TradeMessages.text(" " + playerNames(record) + " - ").formatted(Formatting.GRAY))
                    .append(reason(record).formatted(Formatting.YELLOW)));
        }
        return 1;
    }

    private static int info(ServerCommandSource source, String id) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }

        send(source, TradeMessages.trans("securetrade.admin.info_title", record.id, record.phase.name()), Formatting.GOLD);
        if (record.quarantineReason != null) {
            sendLine(source, TradeMessages.trans("securetrade.admin.info_reason",
                    reason(record), record.quarantineDetail == null ? "" : record.quarantineDetail).formatted(Formatting.YELLOW));
        }
        if (record.quarantineReason == QuarantineReason.JOURNAL_MISSING || record.quarantineReason == QuarantineReason.UNREADABLE_ITEMS) {
            sendLine(source, TradeMessages.trans("securetrade.admin.journal_missing_warning").formatted(Formatting.RED));
        }
        for (TradeEscrow.OfferView offer : TradeEscrow.describe(record)) {
            Text items = offer.items() == null
                    ? TradeMessages.trans("securetrade.admin.items_unreadable").formatted(Formatting.RED)
                    : TradeHistoryManager.formatItemsAndXP(offer.items(), offer.xp());
            sendLine(source, TradeMessages.trans("securetrade.admin.info_side",
                    TradeMessages.text(offer.playerName()).formatted(Formatting.AQUA),
                    sideState(offer.outcome()),
                    items
            ).formatted(Formatting.GRAY));
        }

        if (record.phase == EscrowPhase.QUARANTINED) {
            sendLine(source, TradeMessages.empty()
                    .append(actionButton(record, ResolutionAction.REFUND, Formatting.GREEN))
                    .append(" ")
                    .append(actionButton(record, ResolutionAction.COMPLETE, Formatting.AQUA))
                    .append(" ")
                    .append(actionButton(record, ResolutionAction.DISMISS, Formatting.RED)));
        }
        return 1;
    }

    private static int askDismissConfirmation(ServerCommandSource source, String id) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }
        send(source, TradeMessages.trans("securetrade.admin.dismiss_confirm", record.shortId(), record.shortId()), Formatting.YELLOW);
        return 1;
    }

    private static int resolve(ServerCommandSource source, String id, ResolutionAction action) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }

        TradeEscrow.ResolveResult result = TradeEscrow.resolve(record, action, source.getName());
        if (result.status() == TradeEscrow.ResolveStatus.RESOLVED) {
            send(source, TradeMessages.trans("securetrade.admin.resolved",
                    record.shortId(), actionName(action), result.appliedNow(), result.pending()), Formatting.GREEN);
            return 1;
        }
        send(source, TradeMessages.trans(failureKey(result.status()), record.shortId()), Formatting.RED);
        return 0;
    }

    private static String failureKey(TradeEscrow.ResolveStatus status) {
        switch (status) {
            case NOT_QUARANTINED:
                return "securetrade.admin.not_quarantined";
            case REFUND_AFTER_DELIVERY:
                return "securetrade.admin.refund_after_delivery";
            case REFUND_AFTER_COMMIT:
                return "securetrade.admin.refund_after_commit";
            case COUNTERPART_UNKNOWN:
                return "securetrade.admin.counterpart_unknown";
            default:
                return "securetrade.admin.save_failed";
        }
    }

    private static EscrowRecord find(ServerCommandSource source, String id) {
        List<EscrowRecord> matches = TradeEscrow.findByIdPrefix(id);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        send(source, TradeMessages.trans(matches.isEmpty() ? "securetrade.admin.not_found" : "securetrade.admin.ambiguous", id), Formatting.RED);
        return null;
    }

    private static Text transactionLink(EscrowRecord record) {
        return TradeMessages.text(record.shortId()).setStyle(Style.EMPTY
                .withColor(Formatting.GOLD)
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade admin info " + record.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.text(record.id))));
    }

    private static Text actionButton(EscrowRecord record, ResolutionAction action, Formatting color) {
        String command = "/trade admin " + action.name().toLowerCase(Locale.ROOT) + " " + record.shortId();
        return TradeMessages.text("[").append(actionName(action)).append("]").setStyle(Style.EMPTY
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.text(command))));
    }

    private static String playerNames(EscrowRecord record) {
        StringBuilder names = new StringBuilder();
        for (EscrowRecord.Side side : record.sides) {
            if (names.length() > 0) {
                names.append(" / ");
            }
            names.append(side.name);
        }
        return names.toString();
    }

    private static MutableText reason(EscrowRecord record) {
        String key = record.quarantineReason == null ? "unknown" : record.quarantineReason.name().toLowerCase(Locale.ROOT);
        return TradeMessages.trans("securetrade.reason." + key);
    }

    private static Text sideState(EscrowOutcome outcome) {
        String key = outcome == null ? "pending" : outcome.name().toLowerCase(Locale.ROOT);
        return TradeMessages.trans("securetrade.side." + key);
    }

    private static Text actionName(ResolutionAction action) {
        return TradeMessages.trans("securetrade.action." + action.name().toLowerCase(Locale.ROOT));
    }

    private static void send(ServerCommandSource source, Text message, Formatting color) {
        sendLine(source, TradeMessages.format(message, color));
    }

    private static void sendLine(ServerCommandSource source, Text message) {
        source.sendFeedback(message, false);
    }
}
