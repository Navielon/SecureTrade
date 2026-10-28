package com.securetrade.escrow;

import com.mojang.datafixers.DataFixer;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Escrow state that lives inside the player's own save file, next to the inventory. Vanilla writes
 * the whole file at once, so an offer, a marker and the inventory can never disagree on disk.
 */
public final class PlayerEscrowData {
    public static final String KEY = "securetrade_escrow";
    private static final int SCHEMA_VERSION = 1;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT = 3;

    public static final class Offer {
        private final UUID transactionId;
        private final boolean prepared;
        private final long xp;
        private final List<EscrowStack> items;
        private final int dataVersion;
        private final List<NbtCompound> unreadableItems;

        /**
         * @param unreadableItems the original item tags when at least one of them can no longer be
         *                        decoded; they are written back untouched instead of being dropped
         */
        public Offer(UUID transactionId, boolean prepared, long xp, List<EscrowStack> items,
                     int dataVersion, List<NbtCompound> unreadableItems) {
            this.transactionId = transactionId;
            this.prepared = prepared;
            this.xp = xp;
            this.items = items;
            this.dataVersion = dataVersion;
            this.unreadableItems = unreadableItems;
        }

        public static Offer of(UUID transactionId, boolean prepared, long xp, Inventory container) {
            return new Offer(transactionId, prepared, xp, EscrowItems.fromContainer(container), EscrowItems.currentDataVersion(), null);
        }

        public UUID transactionId() {
            return transactionId;
        }

        public boolean prepared() {
            return prepared;
        }

        public long xp() {
            return xp;
        }

        public List<EscrowStack> items() {
            return items;
        }

        public int dataVersion() {
            return dataVersion;
        }

        public List<NbtCompound> unreadableItems() {
            return unreadableItems;
        }

        public boolean isReadable() {
            return unreadableItems == null;
        }
    }

    public static final class Marker {
        private final EscrowOutcome outcome;
        private final long timestamp;

        public Marker(EscrowOutcome outcome, long timestamp) {
            this.outcome = outcome;
            this.timestamp = timestamp;
        }

        public EscrowOutcome outcome() {
            return outcome;
        }

        public long timestamp() {
            return timestamp;
        }
    }

    private final List<Offer> offers = new ArrayList<>();
    private final Map<UUID, Marker> markers = new LinkedHashMap<>();
    // Data written by a newer version of the mod is carried through untouched instead of being lost.
    private NbtCompound unsupported;

    public static PlayerEscrowData read(NbtCompound playerTag, DataFixer fixer) {
        if (!playerTag.contains(KEY, TAG_COMPOUND)) {
            return null;
        }

        NbtCompound root = playerTag.getCompound(KEY);
        PlayerEscrowData data = new PlayerEscrowData();
        if (root.getInt("schema") != SCHEMA_VERSION) {
            data.unsupported = root.copy();
            return data;
        }

        NbtList offerList = root.getList("offers", TAG_COMPOUND);
        for (int i = 0; i < offerList.size(); i++) {
            NbtCompound entry = offerList.getCompound(i);
            UUID transactionId = parseUuid(entry.getString("tx"));
            if (transactionId == null) {
                continue;
            }
            int dataVersion = entry.contains("dataVersion", TAG_INT) ? entry.getInt("dataVersion") : EscrowItems.currentDataVersion();
            List<NbtCompound> rawItems = new ArrayList<>();
            NbtList itemList = entry.getList("items", TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++) {
                rawItems.add(itemList.getCompound(j));
            }
            List<EscrowStack> items = decode(rawItems, dataVersion, fixer);
            data.offers.add(new Offer(
                    transactionId,
                    entry.getBoolean("prepared"),
                    entry.getLong("xp"),
                    items == null ? Collections.<EscrowStack>emptyList() : items,
                    items == null ? dataVersion : EscrowItems.currentDataVersion(),
                    items == null ? rawItems : null
            ));
        }

        NbtList markerList = root.getList("markers", TAG_COMPOUND);
        for (int i = 0; i < markerList.size(); i++) {
            NbtCompound entry = markerList.getCompound(i);
            UUID transactionId = parseUuid(entry.getString("tx"));
            EscrowOutcome outcome = parseOutcome(entry.getString("outcome"));
            if (transactionId != null && outcome != null) {
                data.markers.put(transactionId, new Marker(outcome, entry.getLong("at")));
            }
        }
        return data;
    }

    private static List<EscrowStack> decode(List<NbtCompound> rawItems, int dataVersion, DataFixer fixer) {
        List<EscrowStack> items = new ArrayList<>();
        for (NbtCompound raw : rawItems) {
            try {
                NbtCompound upgraded = EscrowItems.upgrade(fixer, raw.copy(), dataVersion);
                ItemStack stack = ItemStack.fromNbt(upgraded);
                if (stack.isEmpty()) {
                    return null;
                }
                items.add(new EscrowStack(upgraded.getByte("Slot") & 255, stack));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return items;
    }

    public void write(NbtCompound playerTag, Offer liveOffer) {
        if (unsupported != null) {
            playerTag.put(KEY, unsupported.copy());
            return;
        }

        NbtCompound root = new NbtCompound();
        root.putInt("schema", SCHEMA_VERSION);
        NbtList offerList = new NbtList();
        for (Offer offer : offers) {
            offerList.add(writeOffer(offer));
        }
        if (liveOffer != null) {
            offerList.add(writeOffer(liveOffer));
        }
        root.put("offers", offerList);

        NbtList markerList = new NbtList();
        for (Map.Entry<UUID, Marker> marker : markers.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("tx", marker.getKey().toString());
            entry.putString("outcome", marker.getValue().outcome().name());
            entry.putLong("at", marker.getValue().timestamp());
            markerList.add(entry);
        }
        root.put("markers", markerList);
        playerTag.put(KEY, root);
    }

    private static NbtCompound writeOffer(Offer offer) {
        NbtCompound entry = new NbtCompound();
        entry.putString("tx", offer.transactionId().toString());
        entry.putBoolean("prepared", offer.prepared());
        entry.putLong("xp", offer.xp());
        entry.putInt("dataVersion", offer.dataVersion());
        NbtList items = new NbtList();
        if (offer.isReadable()) {
            for (EscrowStack item : offer.items()) {
                NbtCompound tag = new NbtCompound();
                tag.putByte("Slot", (byte) item.slot());
                items.add(item.stack().writeNbt(tag));
            }
        } else {
            items.addAll(offer.unreadableItems());
        }
        entry.put("items", items);
        return entry;
    }

    public boolean isEmpty() {
        return unsupported == null && offers.isEmpty() && markers.isEmpty();
    }

    public boolean isUnsupported() {
        return unsupported != null;
    }

    public Offer offer(UUID transactionId) {
        for (Offer offer : offers) {
            if (offer.transactionId().equals(transactionId)) {
                return offer;
            }
        }
        return null;
    }

    public List<Offer> offers() {
        return Collections.unmodifiableList(new ArrayList<>(offers));
    }

    public void removeOffer(Offer offer) {
        offers.remove(offer);
    }

    public Marker marker(UUID transactionId) {
        return markers.get(transactionId);
    }

    public Map<UUID, Marker> markers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(markers));
    }

    public void putMarker(UUID transactionId, EscrowOutcome outcome, long timestamp) {
        markers.put(transactionId, new Marker(outcome, timestamp));
    }

    /**
     * Markers are only needed while the journal still knows the transaction; once it is gone
     * and the marker is old, nothing can ask about it anymore.
     */
    public void pruneMarkers(long olderThan, Predicate<UUID> stillJournaled) {
        markers.entrySet().removeIf(entry -> entry.getValue().timestamp() < olderThan && !stillJournaled.test(entry.getKey()));
    }

    /**
     * Checks a freshly written player file, so a commit step only proceeds once its state is on disk.
     */
    public static boolean fileHasPreparedOffer(NbtCompound playerFile, UUID transactionId) {
        return anyEntry(playerFile, "offers", transactionId, entry -> entry.getBoolean("prepared"));
    }

    public static boolean fileHasOffer(NbtCompound playerFile, UUID transactionId) {
        return anyEntry(playerFile, "offers", transactionId, entry -> true);
    }

    public static boolean fileHasMarker(NbtCompound playerFile, UUID transactionId) {
        return anyEntry(playerFile, "markers", transactionId, entry -> true);
    }

    private static boolean anyEntry(NbtCompound playerFile, String list, UUID transactionId, Predicate<NbtCompound> condition) {
        String id = transactionId.toString();
        NbtList entries = playerFile.getCompound(KEY).getList(list, TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            NbtCompound entry = entries.getCompound(i);
            if (id.equals(entry.getString("tx")) && condition.test(entry)) {
                return true;
            }
        }
        return false;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static EscrowOutcome parseOutcome(String value) {
        try {
            return EscrowOutcome.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
