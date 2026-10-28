package com.securetrade.escrow;

import com.mojang.datafixers.DataFixer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

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
    public record Offer(UUID transactionId, boolean prepared, long xp, List<EscrowStack> items,
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

    public static PlayerEscrowData read(CompoundTag playerTag, DataFixer fixer, HolderLookup.Provider registries) {
        if (!playerTag.contains(KEY, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag root = playerTag.getCompound(KEY);
        PlayerEscrowData data = new PlayerEscrowData();
        if (root.getInt("schema") != SCHEMA_VERSION) {
            data.unsupported = root.copy();
            return data;
        }

        ListTag offerList = root.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offerList.size(); i++) {
            CompoundTag entry = offerList.getCompound(i);
            UUID transactionId = parseUuid(entry.getString("tx"));
            if (transactionId == null) {
                continue;
            }
            int dataVersion = entry.contains("dataVersion", Tag.TAG_INT) ? entry.getInt("dataVersion") : EscrowItems.currentDataVersion();
            List<CompoundTag> rawItems = new ArrayList<>();
            ListTag itemList = entry.getList("items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++) {
                rawItems.add(itemList.getCompound(j));
            }
            List<EscrowStack> items = decode(rawItems, dataVersion, fixer, registries);
            data.offers.add(new Offer(
                    transactionId,
                    entry.getBoolean("prepared"),
                    entry.getLong("xp"),
                    items == null ? List.of() : items,
                    items == null ? dataVersion : EscrowItems.currentDataVersion(),
                    items == null ? rawItems : null
            ));
        }

        ListTag markerList = root.getList("markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < markerList.size(); i++) {
            CompoundTag entry = markerList.getCompound(i);
            UUID transactionId = parseUuid(entry.getString("tx"));
            EscrowOutcome outcome = parseOutcome(entry.getString("outcome"));
            if (transactionId != null && outcome != null) {
                data.markers.put(transactionId, new Marker(outcome, entry.getLong("at")));
            }
        }
        return data;
    }

    private static List<EscrowStack> decode(List<CompoundTag> rawItems, int dataVersion, DataFixer fixer,
                                            HolderLookup.Provider registries) {
        List<EscrowStack> items = new ArrayList<>();
        for (CompoundTag raw : rawItems) {
            try {
                CompoundTag upgraded = EscrowItems.upgrade(fixer, raw.copy(), dataVersion);
                Optional<ItemStack> stack = ItemStack.parse(registries, upgraded);
                if (stack.isEmpty() || stack.get().isEmpty()) {
                    return null;
                }
                items.add(new EscrowStack(upgraded.getByte("Slot") & 255, stack.get()));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return items;
    }

    public void write(CompoundTag playerTag, Offer liveOffer, HolderLookup.Provider registries) {
        if (unsupported != null) {
            playerTag.put(KEY, unsupported.copy());
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt("schema", SCHEMA_VERSION);
        ListTag offerList = new ListTag();
        for (Offer offer : offers) {
            offerList.add(writeOffer(offer, registries));
        }
        if (liveOffer != null) {
            offerList.add(writeOffer(liveOffer, registries));
        }
        root.put("offers", offerList);

        ListTag markerList = new ListTag();
        for (Map.Entry<UUID, Marker> marker : markers.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("tx", marker.getKey().toString());
            entry.putString("outcome", marker.getValue().outcome().name());
            entry.putLong("at", marker.getValue().timestamp());
            markerList.add(entry);
        }
        root.put("markers", markerList);
        playerTag.put(KEY, root);
    }

    private static CompoundTag writeOffer(Offer offer, HolderLookup.Provider registries) {
        CompoundTag entry = new CompoundTag();
        entry.putString("tx", offer.transactionId().toString());
        entry.putBoolean("prepared", offer.prepared());
        entry.putLong("xp", offer.xp());
        entry.putInt("dataVersion", offer.dataVersion());
        ListTag items = new ListTag();
        if (offer.isReadable()) {
            for (EscrowStack item : offer.items()) {
                CompoundTag tag = new CompoundTag();
                tag.putByte("Slot", (byte) item.slot());
                items.add(item.stack().save(registries, tag));
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
        return anyEntry(playerFile, "offers", transactionId, entry -> entry.getBoolean("prepared"));
    }

    public static boolean fileHasOffer(CompoundTag playerFile, UUID transactionId) {
        return anyEntry(playerFile, "offers", transactionId, entry -> true);
    }

    public static boolean fileHasMarker(CompoundTag playerFile, UUID transactionId) {
        return anyEntry(playerFile, "markers", transactionId, entry -> true);
    }

    private static boolean anyEntry(CompoundTag playerFile, String list, UUID transactionId, Predicate<CompoundTag> condition) {
        String id = transactionId.toString();
        ListTag entries = playerFile.getCompound(KEY).getList(list, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
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
