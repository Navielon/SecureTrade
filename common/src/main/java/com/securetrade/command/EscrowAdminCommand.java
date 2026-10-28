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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.Style;

import java.util.List;
import java.util.Locale;

/**
 * {@code /trade admin ...}: inspection and resolution of quarantined escrow transactions.
 */
final class EscrowAdminCommand {
    private EscrowAdminCommand() {
    }

    static LiteralArgumentBuilder<CommandSource> build() {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("quarantine")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("info")
                        .then(transactionArgument()
                                .executes(context -> info(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("refund")
                        .then(transactionArgument()
                                .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), ResolutionAction.REFUND))))
                .then(Commands.literal("complete")
                        .then(transactionArgument()
                                .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), ResolutionAction.COMPLETE))))
                .then(Commands.literal("dismiss")
                        .then(transactionArgument()
                                .executes(context -> askDismissConfirmation(context.getSource(), StringArgumentType.getString(context, "id")))
                                .then(Commands.literal("confirm")
                                        .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), ResolutionAction.DISMISS)))));
    }

    private static RequiredArgumentBuilder<CommandSource, String> transactionArgument() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> ISuggestionProvider.suggest(
                        TradeEscrow.quarantined().stream().map(EscrowRecord::shortId),
                        builder
                ));
    }

    private static int list(CommandSource source) {
        List<EscrowRecord> quarantined = TradeEscrow.quarantined();
        if (quarantined.isEmpty()) {
            send(source, TradeMessages.trans("securetrade.admin.list_empty"), TextFormatting.GRAY);
            return 1;
        }

        send(source, TradeMessages.trans("securetrade.admin.list_title", quarantined.size()), TextFormatting.GOLD);
        for (EscrowRecord record : quarantined) {
            sendLine(source, TradeMessages.empty()
                    .append(TradeMessages.text("- ").withStyle(TextFormatting.DARK_GRAY))
                    .append(transactionLink(record))
                    .append(TradeMessages.text(" " + playerNames(record) + " - ").withStyle(TextFormatting.GRAY))
                    .append(reason(record).withStyle(TextFormatting.YELLOW)));
        }
        return 1;
    }

    private static int info(CommandSource source, String id) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }

        send(source, TradeMessages.trans("securetrade.admin.info_title", record.id, record.phase.name()), TextFormatting.GOLD);
        if (record.quarantineReason != null) {
            sendLine(source, TradeMessages.trans("securetrade.admin.info_reason",
                    reason(record), record.quarantineDetail == null ? "" : record.quarantineDetail).withStyle(TextFormatting.YELLOW));
        }
        if (record.quarantineReason == QuarantineReason.JOURNAL_MISSING || record.quarantineReason == QuarantineReason.UNREADABLE_ITEMS) {
            sendLine(source, TradeMessages.trans("securetrade.admin.journal_missing_warning").withStyle(TextFormatting.RED));
        }
        for (TradeEscrow.OfferView offer : TradeEscrow.describe(record)) {
            ITextComponent items = offer.items() == null
                    ? TradeMessages.trans("securetrade.admin.items_unreadable").withStyle(TextFormatting.RED)
                    : TradeHistoryManager.formatItemsAndXP(offer.items(), offer.xp());
            sendLine(source, TradeMessages.trans("securetrade.admin.info_side",
                    TradeMessages.text(offer.playerName()).withStyle(TextFormatting.AQUA),
                    sideState(offer.outcome()),
                    items
            ).withStyle(TextFormatting.GRAY));
        }

        if (record.phase == EscrowPhase.QUARANTINED) {
            sendLine(source, TradeMessages.empty()
                    .append(actionButton(record, ResolutionAction.REFUND, TextFormatting.GREEN))
                    .append(" ")
                    .append(actionButton(record, ResolutionAction.COMPLETE, TextFormatting.AQUA))
                    .append(" ")
                    .append(actionButton(record, ResolutionAction.DISMISS, TextFormatting.RED)));
        }
        return 1;
    }

    private static int askDismissConfirmation(CommandSource source, String id) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }
        send(source, TradeMessages.trans("securetrade.admin.dismiss_confirm", record.shortId(), record.shortId()), TextFormatting.YELLOW);
        return 1;
    }

    private static int resolve(CommandSource source, String id, ResolutionAction action) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }

        TradeEscrow.ResolveResult result = TradeEscrow.resolve(record, action, source.getTextName());
        if (result.status() == TradeEscrow.ResolveStatus.RESOLVED) {
            send(source, TradeMessages.trans("securetrade.admin.resolved",
                    record.shortId(), actionName(action), result.appliedNow(), result.pending()), TextFormatting.GREEN);
            return 1;
        }
        send(source, TradeMessages.trans(failureKey(result.status()), record.shortId()), TextFormatting.RED);
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

    private static EscrowRecord find(CommandSource source, String id) {
        List<EscrowRecord> matches = TradeEscrow.findByIdPrefix(id);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        send(source, TradeMessages.trans(matches.isEmpty() ? "securetrade.admin.not_found" : "securetrade.admin.ambiguous", id), TextFormatting.RED);
        return null;
    }

    private static ITextComponent transactionLink(EscrowRecord record) {
        return TradeMessages.text(record.shortId()).withStyle(Style.EMPTY
                .withColor(TextFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade admin info " + record.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.text(record.id))));
    }

    private static ITextComponent actionButton(EscrowRecord record, ResolutionAction action, TextFormatting color) {
        String command = "/trade admin " + action.name().toLowerCase(Locale.ROOT) + " " + record.shortId();
        return TradeMessages.text("[").append(actionName(action)).append("]").withStyle(Style.EMPTY
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

    private static IFormattableTextComponent reason(EscrowRecord record) {
        String key = record.quarantineReason == null ? "unknown" : record.quarantineReason.name().toLowerCase(Locale.ROOT);
        return TradeMessages.trans("securetrade.reason." + key);
    }

    private static ITextComponent sideState(EscrowOutcome outcome) {
        String key = outcome == null ? "pending" : outcome.name().toLowerCase(Locale.ROOT);
        return TradeMessages.trans("securetrade.side." + key);
    }

    private static ITextComponent actionName(ResolutionAction action) {
        return TradeMessages.trans("securetrade.action." + action.name().toLowerCase(Locale.ROOT));
    }

    private static void send(CommandSource source, ITextComponent message, TextFormatting color) {
        sendLine(source, TradeMessages.format(message, color));
    }

    private static void sendLine(CommandSource source, ITextComponent message) {
        source.sendSuccess(message, false);
    }
}
