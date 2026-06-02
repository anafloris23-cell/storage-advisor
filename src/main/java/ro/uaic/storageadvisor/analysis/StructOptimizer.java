package ro.uaic.storageadvisor.analysis;

import ro.uaic.storageadvisor.model.RecommendedVariable;
import ro.uaic.storageadvisor.model.StorageEntry;
import ro.uaic.storageadvisor.model.StructDefinition;
import ro.uaic.storageadvisor.model.StructField;
import ro.uaic.storageadvisor.model.StructOptimization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optimizează layout-ul intern al fiecărui struct, aplicând același algoritm de
 * bin-packing (DP-bitmask, cu fallback FFD) ca pentru variabilele de stare ale
 * contractului — vezi {@link BitmaskBinPacker}.
 *
 * Câmpurile struct-ului se împart în:
 *   - fullSlot: numberOfBytes ≥ 32 (ocupă ceil(nb/32) sloturi consecutive)
 *   - packable: numberOfBytes < 32 inplace (grupabile)
 *   - dynamic:  encoding ne-inplace (mapping/array dinamic — slot handle propriu)
 */
public class StructOptimizer {

    private static final int SLOT_SIZE = BitmaskBinPacker.SLOT_SIZE;

    private final BitmaskBinPacker dpPacker = new BitmaskBinPacker();
    private final FirstFitBinPacker ffdPacker = new FirstFitBinPacker();

    public List<StructOptimization> optimizeAll(
            List<StructDefinition> structs, List<StorageEntry> stateEntries
    ) {
        List<StructOptimization> results = new ArrayList<>();
        for (StructDefinition def : structs) {
            results.add(optimize(def, countDirectInstances(def, stateEntries)));
        }
        return results;
    }

    private int countDirectInstances(StructDefinition def, List<StorageEntry> stateEntries) {
        int count = 0;
        for (StorageEntry e : stateEntries) {
            if (def.typeId().equals(e.typeId())) {
                count++;
            }
        }
        return count;
    }

    public StructOptimization optimize(StructDefinition def, int directInstances) {
        List<StructField> fullSlot = new ArrayList<>();
        List<StructField> packable = new ArrayList<>();
        List<StructField> dynamic = new ArrayList<>();

        for (StructField f : def.members()) {
            if (!f.isInplace()) {
                dynamic.add(f);
            } else if (f.numberOfBytes() >= SLOT_SIZE) {
                fullSlot.add(f);
            } else {
                packable.add(f);
            }
        }

        List<List<StructField>> packedGroups;
        if (packable.size() > BitmaskBinPacker.MAX_ITEMS) {
            packedGroups = ffdPacker.pack(packable, StructField::numberOfBytes);
        } else {
            packedGroups = dpPacker.pack(packable, StructField::numberOfBytes);
        }
        for (List<StructField> group : packedGroups) {
            group.sort(Comparator.comparingInt(f -> originalIndex(def, f)));
        }
        packedGroups.sort(Comparator.comparingInt(g -> originalIndex(def, g.get(0))));

        int optimalSlots = 0;
        List<RecommendedVariable> order = new ArrayList<>();

        for (StructField f : fullSlot) {
            optimalSlots += (f.numberOfBytes() + SLOT_SIZE - 1) / SLOT_SIZE;
            order.add(toRecommended(f, "Ocupă slot(uri) proprii (≥ 32 bytes)"));
        }
        for (List<StructField> group : packedGroups) {
            int used = group.stream().mapToInt(StructField::numberOfBytes).sum();
            String note = used == SLOT_SIZE
                    ? "Câmpuri grupate (slot plin)"
                    : "Câmpuri grupate (" + used + "/" + SLOT_SIZE + " bytes)";
            for (StructField f : group) {
                order.add(toRecommended(f, note));
            }
            optimalSlots++;
        }
        for (StructField f : dynamic) {
            optimalSlots++;
            order.add(toRecommended(f, "Tip dinamic (" + f.encoding() + ") — slot handle propriu"));
        }

        return new StructOptimization(
                def.typeId(),
                def.label(),
                def.currentSlots(),
                optimalSlots,
                directInstances,
                order
        );
    }

    private int originalIndex(StructDefinition def, StructField field) {
        return def.members().indexOf(field);
    }

    private RecommendedVariable toRecommended(StructField f, String note) {
        return new RecommendedVariable(f.label(), f.typeLabel(), f.numberOfBytes(), note);
    }
}
