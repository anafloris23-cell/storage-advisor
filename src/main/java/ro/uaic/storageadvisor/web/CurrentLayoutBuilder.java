package ro.uaic.storageadvisor.web;

import ro.uaic.storageadvisor.model.ContractLayout;
import ro.uaic.storageadvisor.model.StorageEntry;
import ro.uaic.storageadvisor.web.dto.CurrentSlotView;
import ro.uaic.storageadvisor.web.dto.CurrentVariableView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Construiește layout-ul curent pe sloturi (diagrama "before") din intrările de storage
 * raportate de solc. Gruparea după slotul de start și calculul bytes-ilor irosiți
 * oglindesc {@code SlotEstimator}, astfel încât cifrele să coincidă cu raportul de analiză.
 *
 * Nu modifică logica de analiză — este doar o proiecție de prezentare a datelor existente.
 */
public final class CurrentLayoutBuilder {

    private static final int SLOT_SIZE = 32;

    private CurrentLayoutBuilder() {}

    public static List<CurrentSlotView> build(ContractLayout layout) {
        // Grupare după slotul de start, păstrând ordinea crescătoare a sloturilor.
        Map<Integer, List<StorageEntry>> bySlot = new TreeMap<>();
        for (StorageEntry e : layout.entries()) {
            bySlot.computeIfAbsent(e.slot().intValue(), k -> new ArrayList<>()).add(e);
        }

        List<CurrentSlotView> slots = new ArrayList<>();
        for (Map.Entry<Integer, List<StorageEntry>> group : bySlot.entrySet()) {
            int slotIndex = group.getKey();
            List<StorageEntry> entries = group.getValue();
            entries.sort(Comparator.comparingInt(StorageEntry::offset));

            int usedBytes = entries.stream().mapToInt(StorageEntry::numberOfBytes).sum();
            int slotsSpanned = slotsNeeded(usedBytes);
            int wastedBytes = slotsSpanned * SLOT_SIZE - usedBytes;

            List<CurrentVariableView> vars = new ArrayList<>();
            for (StorageEntry e : entries) {
                vars.add(new CurrentVariableView(
                        e.label(),
                        e.typeLabel(),
                        e.numberOfBytes(),
                        e.offset()
                ));
            }

            slots.add(new CurrentSlotView(slotIndex, vars, usedBytes, wastedBytes, slotsSpanned));
        }

        return slots;
    }

    private static int slotsNeeded(int numberOfBytes) {
        if (numberOfBytes <= 0) return 1;
        return (numberOfBytes + SLOT_SIZE - 1) / SLOT_SIZE;
    }
}
