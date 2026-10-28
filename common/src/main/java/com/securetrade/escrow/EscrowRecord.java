package com.securetrade.escrow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Journal entry for one trade. Serialized with Gson, so fields stay public and mutable.
 */
public final class EscrowRecord {
    public static final int SCHEMA_VERSION = 1;

    public int schema = SCHEMA_VERSION;
    public String id;
    public EscrowPhase phase;
    public int revision;
    public long createdAt;
    public long updatedAt;
    /** Minecraft data version the items were serialized with, so they can be upgraded after an update. */
    public int dataVersion;
    public List<Side> sides = new ArrayList<>();
    public QuarantineReason quarantineReason;
    /** Phase the record was in when it was quarantined; null if it never had a journal entry. */
    public EscrowPhase quarantinedFrom;
    public String quarantineDetail;
    public long quarantinedAt;
    public Resolution resolution;

    public static final class Side {
        public String uuid;
        public String name;
        public long xp;
        public List<StoredItem> items = new ArrayList<>();
        public EscrowOutcome outcome;

        public Side() {
        }

        public Side(UUID uuid, String name, long xp, List<StoredItem> items) {
            this.uuid = uuid.toString();
            this.name = name;
            this.xp = xp;
            this.items = new ArrayList<>(items);
        }

        public UUID playerId() {
            return UUID.fromString(uuid);
        }

        public boolean isDone() {
            return outcome != null;
        }
    }

    public static final class StoredItem {
        public int slot;
        public String data;

        public StoredItem() {
        }

        public StoredItem(int slot, String data) {
            this.slot = slot;
            this.data = data;
        }
    }

    public static final class Resolution {
        public ResolutionAction action;
        public String by;
        public long at;

        public Resolution() {
        }

        public Resolution(ResolutionAction action, String by, long at) {
            this.action = action;
            this.by = by;
            this.at = at;
        }
    }

    public EscrowRecord() {
    }

    public EscrowRecord(UUID id, EscrowPhase phase, long now) {
        this.id = id.toString();
        this.phase = phase;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID transactionId() {
        return UUID.fromString(id);
    }

    public Side side(UUID playerId) {
        String key = playerId.toString();
        for (Side side : sides) {
            if (key.equals(side.uuid)) {
                return side;
            }
        }
        return null;
    }

    public Side counterpart(UUID playerId) {
        String key = playerId.toString();
        for (Side side : sides) {
            if (!key.equals(side.uuid)) {
                return side;
            }
        }
        return null;
    }

    public boolean involves(UUID playerId) {
        return side(playerId) != null;
    }

    public boolean allSidesDone() {
        for (Side side : sides) {
            if (!side.isDone()) {
                return false;
            }
        }
        return true;
    }

    public boolean anySideDelivered() {
        for (Side side : sides) {
            if (side.outcome == EscrowOutcome.DELIVERED) {
                return true;
            }
        }
        return false;
    }

    /**
     * A refund is only safe while nobody can have received the other side's items: the trade never
     * committed and no side is known to be delivered.
     */
    public boolean canBeRefunded() {
        if (anySideDelivered()) {
            return false;
        }
        return quarantinedFrom != EscrowPhase.COMMITTING
                && quarantinedFrom != EscrowPhase.COMPLETED
                && quarantinedFrom != EscrowPhase.RESOLVING
                && quarantinedFrom != EscrowPhase.RESOLVED;
    }

    public String shortId() {
        return id.substring(0, 8);
    }

    boolean isStructurallyValid() {
        if (schema != SCHEMA_VERSION || id == null || phase == null || sides == null || sides.isEmpty() || sides.size() > 2) {
            return false;
        }
        try {
            UUID.fromString(id);
            for (Side side : sides) {
                if (side == null || side.uuid == null || side.items == null) {
                    return false;
                }
                UUID.fromString(side.uuid);
                for (StoredItem item : side.items) {
                    if (item == null || item.data == null) {
                        return false;
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }
}
