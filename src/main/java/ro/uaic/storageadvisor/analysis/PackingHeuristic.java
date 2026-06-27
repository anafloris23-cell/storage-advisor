package ro.uaic.storageadvisor.analysis;

import ro.uaic.storageadvisor.model.RecommendedSlot;
import ro.uaic.storageadvisor.model.RecommendedVariable;
import ro.uaic.storageadvisor.model.StorageEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Construiește layout-ul recomandat per slot.
 *
 * Variabilele sunt împărțite în trei categorii:
 *   - fullSlot: ocupă singure cel puțin un slot (numberOfBytes ≥ 32, inplace)
 *   - dynamic:  tipuri ne-inplace (mapping, array dinamic, string, bytes) — slot handle propriu
 *   - packable: variabile inplace cu numberOfBytes < 32 care pot fi grupate într-un slot
 *
 * Pentru variabilele packable se folosește un algoritm de programare dinamică pe
 * submulțimi (vezi {@link BitmaskBinPacker}) care găsește partiționarea cu număr
 * minim de sloturi — optimul absolut. Permutările sunt libere.
 */
public class PackingHeuristic {

    private static final int SLOT_SIZE = BitmaskBinPacker.SLOT_SIZE;

    private final BitmaskBinPacker dpPacker = new BitmaskBinPacker();
    private final FirstFitBinPacker ffdPacker = new FirstFitBinPacker();

    public enum Strategy {
        DP_BITMASK("DP-bitmask (exact optimum)"),
        FIRST_FIT_DECREASING("First-Fit Decreasing (heuristic, fallback for n > " + BitmaskBinPacker.MAX_ITEMS + ")");

        private final String label;
        Strategy(String label) { this.label = label; }
        public String label() { return label; }
    }

    private Strategy lastStrategyUsed = Strategy.DP_BITMASK;

    public Strategy lastStrategyUsed() {
        return lastStrategyUsed;
    }

    public List<RecommendedSlot> recommendSlotLayout(List<StorageEntry> entries) {
        List<IndexedEntry> indexed = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            indexed.add(new IndexedEntry(entries.get(i), i));
        }

        List<IndexedEntry> fullSlot = new ArrayList<>();
        List<IndexedEntry> packable = new ArrayList<>();
        List<IndexedEntry> dynamic = new ArrayList<>();

        for (IndexedEntry ie : indexed) {
            StorageEntry e = ie.entry;
            if (!e.isInplace()) {
                dynamic.add(ie);
            } else if (e.numberOfBytes() >= SLOT_SIZE) {
                fullSlot.add(ie);
            } else {
                packable.add(ie);
            }
        }

        fullSlot.sort(Comparator.comparingInt(ie -> ie.originalIndex));
        dynamic.sort(Comparator.comparingInt(ie -> ie.originalIndex));

        List<List<IndexedEntry>> packedGroups;
        if (packable.size() > BitmaskBinPacker.MAX_ITEMS) {
            lastStrategyUsed = Strategy.FIRST_FIT_DECREASING;
            packedGroups = ffdPacker.pack(packable, ie -> ie.entry.numberOfBytes());
        } else {
            lastStrategyUsed = Strategy.DP_BITMASK;
            packedGroups = dpPacker.pack(packable, ie -> ie.entry.numberOfBytes());
        }

        // Stabilitate vizuală: în fiecare grup și între grupuri ordonăm după indexul original.
        for (List<IndexedEntry> group : packedGroups) {
            group.sort(Comparator.comparingInt(ie -> ie.originalIndex));
        }
        packedGroups.sort(Comparator.comparingInt(g -> g.get(0).originalIndex));

        List<RecommendedSlot> slots = new ArrayList<>();
        int slotIndex = 0;

        for (IndexedEntry ie : fullSlot) {
            String note = "Ocupă singur slotul (≥ 32 bytes)";
            slots.add(new RecommendedSlot(
                    slotIndex++,
                    List.of(toRecommended(ie.entry, note)),
                    SLOT_SIZE,
                    0
            ));
        }

        String strategyTag = lastStrategyUsed == Strategy.DP_BITMASK ? "DP-bitmask" : "FFD";
        for (List<IndexedEntry> group : packedGroups) {
            int used = group.stream().mapToInt(ie -> ie.entry.numberOfBytes()).sum();
            int wasted = SLOT_SIZE - used;
            String note = used == SLOT_SIZE
                    ? "Slot complet ocupat prin grupare (" + strategyTag + ")"
                    : "Grupare " + strategyTag + " (" + used + "/" + SLOT_SIZE + " bytes folosiți)";
            List<RecommendedVariable> vars = new ArrayList<>();
            for (IndexedEntry ie : group) {
                vars.add(toRecommended(ie.entry, note));
            }
            slots.add(new RecommendedSlot(slotIndex++, vars, used, wasted));
        }

        for (IndexedEntry ie : dynamic) {
            String note = "Tip dinamic (" + ie.entry.encoding() + ") — slot handle propriu";
            slots.add(new RecommendedSlot(
                    slotIndex++,
                    List.of(toRecommended(ie.entry, note)),
                    SLOT_SIZE,
                    0
            ));
        }

        return slots;
    }

    public List<RecommendedVariable> recommendOrder(List<StorageEntry> entries) {
        List<RecommendedVariable> flat = new ArrayList<>();
        for (RecommendedSlot s : recommendSlotLayout(entries)) {
            flat.addAll(s.variables());
        }
        return flat;
    }

    private RecommendedVariable toRecommended(StorageEntry entry, String note) {
        return new RecommendedVariable(
                entry.label(),
                entry.typeLabel(),
                entry.numberOfBytes(),
                note
        );
    }

    private record IndexedEntry(StorageEntry entry, int originalIndex) {}
}
