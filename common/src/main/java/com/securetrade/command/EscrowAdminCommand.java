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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;

/**
 * {@code /trade admin ...}: inspection and resolution of quarantined escrow transactions.
 */
final class EscrowAdminCommand {
    private EscrowAdminCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
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

    private static RequiredArgumentBuilder<CommandSourceStack, String> transactionArgument() {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        TradeEscrow.quarantined().stream().map(EscrowRecord::shortId),
                        builder
                ));
    }

    private static int list(CommandSourceStack source) {
        List<EscrowRecord> quarantined = TradeEscrow.quarantined();
        if (quarantined.isEmpty()) {
            send(source, TradeMessages.trans("securetrade.admin.list_empty"), ChatFormatting.GRAY);
            return 1;
        }

        send(source, TradeMessages.trans("securetrade.admin.list_title", quarantined.size()), ChatFormatting.GOLD);
        for (EscrowRecord record : quarantined) {
            sendLine(source, TradeMessages.empty()
                    .append(TradeMessages.text("- ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(transactionLink(record))
                    .append(TradeMessages.text(" " + playerNames(record) + " - ").withStyle(ChatFormatting.GRAY))
                    .append(reason(record).withStyle(ChatFormatting.YELLOW)));
        }
        return 1;
    }

    private static int info(CommandSourceStack source, String id) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }

        send(source, TradeMessages.trans("securetrade.admin.info_title", record.id, record.phase.name()), ChatFormatting.GOLD);
        if (record.quarantineReason != null) {
            sendLine(source, TradeMessages.trans("securetrade.admin.info_reason",
                    reason(record), record.quarantineDetail == null ? "" : record.quarantineDetail).withStyle(ChatFormatting.YELLOW));
        }
        if (record.quarantineReason == QuarantineReason.JOURNAL_MISSING || record.quarantineReason == QuarantineReason.UNREADABLE_ITEMS) {
            sendLine(source, TradeMessages.trans("securetrade.admin.journal_missing_warning").withStyle(ChatFormatting.RED));
        }
        for (TradeEscrow.OfferView offer : TradeEscrow.describe(record)) {
            Component items = offer.items() == null
                    ? TradeMessages.trans("securetrade.admin.items_unreadable").withStyle(ChatFormatting.RED)
                    : TradeHistoryManager.formatItemsAndXP(offer.items(), offer.xp());
            sendLine(source, TradeMessages.trans("securetrade.admin.info_side",
                    TradeMessages.text(offer.playerName()).withStyle(ChatFormatting.AQUA),
                    sideState(offer.outcome()),
                    items
            ).withStyle(ChatFormatting.GRAY));
        }

        if (record.phase == EscrowPhase.QUARANTINED) {
            sendLine(source, TradeMessages.empty()
                    .append(actionButton(record, ResolutionAction.REFUND, ChatFormatting.GREEN))
                    .append(" ")
                    .append(actionButton(record, ResolutionAction.COMPLETE, ChatFormatting.AQUA))
                    .append(" ")
                    .append(actionButton(record, ResolutionAction.DISMISS, ChatFormatting.RED)));
        }
        return 1;
    }

    private static int askDismissConfirmation(CommandSourceStack source, String id) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }
        send(source, TradeMessages.trans("securetrade.admin.dismiss_confirm", record.shortId(), record.shortId()), ChatFormatting.YELLOW);
        return 1;
    }

    private static int resolve(CommandSourceStack source, String id, ResolutionAction action) {
        EscrowRecord record = find(source, id);
        if (record == null) {
            return 0;
        }

        TradeEscrow.ResolveResult result = TradeEscrow.resolve(record, action, source.getTextName());
        switch (result.status()) {
            case RESOLVED -> send(source, TradeMessages.trans("securetrade.admin.resolved",
                    record.shortId(), actionName(action), result.appliedNow(), result.pending()), ChatFormatting.GREEN);
            case NOT_QUARANTINED -> send(source, TradeMessages.trans("securetrade.admin.not_quarantined", record.shortId()), ChatFormatting.RED);
            case REFUND_AFTER_DELIVERY -> send(source, TradeMessages.trans("securetrade.admin.refund_after_delivery", record.shortId()), ChatFormatting.RED);
            case REFUND_AFTER_COMMIT -> send(source, TradeMessages.trans("securetrade.admin.refund_after_commit", record.shortId()), ChatFormatting.RED);
            case COUNTERPART_UNKNOWN -> send(source, TradeMessages.trans("securetrade.admin.counterpart_unknown", record.shortId()), ChatFormatting.RED);
            case SAVE_FAILED -> send(source, TradeMessages.trans("securetrade.admin.save_failed", record.shortId()), ChatFormatting.RED);
        }
        return result.status() == TradeEscrow.ResolveStatus.RESOLVED ? 1 : 0;
    }

    private static EscrowRecord find(CommandSourceStack source, String id) {
        List<EscrowRecord> matches = TradeEscrow.findByIdPrefix(id);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        send(source, TradeMessages.trans(matches.isEmpty() ? "securetrade.admin.not_found" : "securetrade.admin.ambiguous", id), ChatFormatting.RED);
        return null;
    }

    private static Component transactionLink(EscrowRecord record) {
        return TradeMessages.text(record.shortId()).withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade admin info " + record.id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TradeMessages.text(record.id))));
    }

    private static Component actionButton(EscrowRecord record, ResolutionAction action, ChatFormatting color) {
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
            if (!names.isEmpty()) {
                names.append(" / ");
            }
            names.append(side.name);
        }
        return names.toString();
    }

    private static MutableComponent reason(EscrowRecord record) {
        String key = record.quarantineReason == null ? "unknown" : record.quarantineReason.name().toLowerCase(Locale.ROOT);
        return TradeMessages.trans("securetrade.reason." + key);
    }

    private static Component sideState(EscrowOutcome outcome) {
        String key = outcome == null ? "pending" : outcome.name().toLowerCase(Locale.ROOT);
        return TradeMessages.trans("securetrade.side." + key);
    }

    private static Component actionName(ResolutionAction action) {
        return TradeMessages.trans("securetrade.action." + action.name().toLowerCase(Locale.ROOT));
    }

    private static void send(CommandSourceStack source, Component message, ChatFormatting color) {
        sendLine(source, TradeMessages.format(message, color));
    }

    private static void sendLine(CommandSourceStack source, Component message) {
        source.sendSuccess(message, false);
    }
}
