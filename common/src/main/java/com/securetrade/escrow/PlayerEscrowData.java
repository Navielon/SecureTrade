package com.securetrade.escrow;

import com.mojang.datafixers.DataFixer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Escrow state that lives inside the player's own save file, next to the inventory. Vanilla writes
 * the whole file at once, so an offer, a marker and the inventory can never disagree on disk.
 */
public final class PlayerEscrowData {
    public static final String KEY = "securetrade_escrow";
    private static final int SCHEMA_VERSION = 1;

    /**
     * @param unreadableItems the original item tags when at least one of them can no longer be
     *                        decoded; they are written back untouched instead of being dropped
     */
    public record Offer(UUID transactionId, boolean prepared, long xp, List<ItemStackWithSlot> items,
                        int dataVersion, List<CompoundTag> unreadableItems) {
        public static Offer of(UUID transactionId, boolean prepared, long xp, Container container) {
            return new Offer(transactionId, prepared, xp, EscrowItems.fromContainer(container), EscrowItems.currentDataVersion(), null);
        }

        public boolean isReadable() {
            return unreadableItems == null;
        }
    }

    public record Marker(EscrowOutcome outcome, long timestamp) {
    }

    private final List<Offer> offers = new ArrayList<>();
    private final Map<UUID, Marker> markers = new LinkedHashMap<>();
    // Data written by a newer version of the mod is carried through untouched instead of being lost.
    private CompoundTag unsupported;

    public static PlayerEscrowData read(ValueInput input, DataFixer fixer, HolderLookup.Provider registries) {
        ValueInput root = input.child(KEY).orElse(null);
        if (root == null) {
            return null;
        }

        PlayerEscrowData data = new PlayerEscrowData();
        if (root.getIntOr("schema", 0) != SCHEMA_VERSION) {
            data.unsupported = input.read(KEY, CompoundTag.CODEC).orElse(null);
            return data;
        }

        for (ValueInput entry : root.childrenListOrEmpty("offers")) {
            UUID transactionId = parseUuid(entry.getStringOr("tx", ""));
            if (transactionId == null) {
                continue;
            }
            int dataVersion = entry.getIntOr("dataVersion", EscrowItems.currentDataVersion());
            List<CompoundTag> rawItems = new ArrayList<>();
            entry.listOrEmpty("items", CompoundTag.CODEC).forEach(rawItems::add);
            List<ItemStackWithSlot> items = decode(rawItems, dataVersion, fixer, registries);
            data.offers.add(new Offer(
                    transactionId,
                    entry.getBooleanOr("prepared", false),
                    entry.getLongOr("xp", 0L),
                    items == null ? List.of() : items,
                    items == null ? dataVersion : EscrowItems.currentDataVersion(),
                    items == null ? rawItems : null
            ));
        }
        for (ValueInput entry : root.childrenListOrEmpty("markers")) {
            UUID transactionId = parseUuid(entry.getStringOr("tx", ""));
            EscrowOutcome outcome = parseOutcome(entry.getStringOr("outcome", ""));
            if (transactionId != null && outcome != null) {
                data.markers.put(transactionId, new Marker(outcome, entry.getLongOr("at", 0L)));
            }
        }
        return data;
    }

    private static List<ItemStackWithSlot> decode(List<CompoundTag> rawItems, int dataVersion, DataFixer fixer,
                                                  HolderLookup.Provider registries) {
        List<ItemStackWithSlot> items = new ArrayList<>();
        for (CompoundTag raw : rawItems) {
            try {
                CompoundTag upgraded = EscrowItems.upgrade(fixer, raw.copy(), dataVersion);
                Optional<ItemStackWithSlot> item = ItemStackWithSlot.CODEC
                        .parse(registries.createSerializationContext(NbtOps.INSTANCE), upgraded)
                        .result();
                if (item.isEmpty() || item.get().stack().isEmpty()) {
                    return null;
                }
                items.add(item.get());
            } catch (RuntimeException e) {
                return null;
            }
        }
        return items;
    }

    public void write(ValueOutput output, Offer liveOffer) {
        if (unsupported != null) {
            output.store(KEY, CompoundTag.CODEC, unsupported);
            return;
        }

        ValueOutput root = output.child(KEY);
        root.putInt("schema", SCHEMA_VERSION);
        ValueOutput.ValueOutputList offerList = root.childrenList("offers");
        for (Offer offer : offers) {
            writeOffer(offerList.addChild(), offer);
        }
        if (liveOffer != null) {
            writeOffer(offerList.addChild(), liveOffer);
        }
        ValueOutput.ValueOutputList markerList = root.childrenList("markers");
        for (Map.Entry<UUID, Marker> marker : markers.entrySet()) {
            ValueOutput entry = markerList.addChild();
            entry.putString("tx", marker.getKey().toString());
            entry.putString("outcome", marker.getValue().outcome().name());
            entry.putLong("at", marker.getValue().timestamp());
        }
    }

    private static void writeOffer(ValueOutput entry, Offer offer) {
        entry.putString("tx", offer.transactionId().toString());
        entry.putBoolean("prepared", offer.prepared());
        entry.putLong("xp", offer.xp());
        entry.putInt("dataVersion", offer.dataVersion());
        if (offer.isReadable()) {
            ValueOutput.TypedOutputList<ItemStackWithSlot> items = entry.list("items", ItemStackWithSlot.CODEC);
            for (ItemStackWithSlot item : offer.items()) {
                items.add(item);
            }
        } else {
            ValueOutput.TypedOutputList<CompoundTag> items = entry.list("items", CompoundTag.CODEC);
            for (CompoundTag item : offer.unreadableItems()) {
                items.add(item);
            }
        }
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
        return List.copyOf(offers);
    }

    public void removeOffer(Offer offer) {
        offers.remove(offer);
    }

    public Marker marker(UUID transactionId) {
        return markers.get(transactionId);
    }

    public Map<UUID, Marker> markers() {
        return Map.copyOf(markers);
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
    public static boolean fileHasPreparedOffer(CompoundTag playerFile, UUID transactionId) {
        String id = transactionId.toString();
        return playerFile.getCompoundOrEmpty(KEY).getListOrEmpty("offers").compoundStream()
                .anyMatch(offer -> id.equals(offer.getStringOr("tx", "")) && offer.getBooleanOr("prepared", false));
    }

    public static boolean fileHasOffer(CompoundTag playerFile, UUID transactionId) {
        String id = transactionId.toString();
        return playerFile.getCompoundOrEmpty(KEY).getListOrEmpty("offers").compoundStream()
                .anyMatch(offer -> id.equals(offer.getStringOr("tx", "")));
    }

    public static boolean fileHasMarker(CompoundTag playerFile, UUID transactionId) {
        String id = transactionId.toString();
        return playerFile.getCompoundOrEmpty(KEY).getListOrEmpty("markers").compoundStream()
                .anyMatch(marker -> id.equals(marker.getStringOr("tx", "")));
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
