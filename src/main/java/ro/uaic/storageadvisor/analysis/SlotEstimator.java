package ro.uaic.storageadvisor.analysis;

import ro.uaic.storageadvisor.model.RecommendedVariable;
import ro.uaic.storageadvisor.model.StorageEntry;

import java.util.List;

public class SlotEstimator {

    private static final int SLOT_SIZE = 32;

    /**
     * Numărul total de sloturi ocupate de layout-ul curent.
     *
     * O variabilă inplace cu numberOfBytes > 32 (ex: array fix `uint256[50]`)
     * ocupă ceil(numberOfBytes / 32) sloturi consecutive începând cu slot-ul
     * declarat. Calculul ia max(slot + slotsTaken) peste toate variabilele.
     */
    public int estimateSlotsForCurrentLayout(List<StorageEntry> entries) {
        int maxSlotEnd = 0;
        for (StorageEntry e : entries) {
            int slotStart = e.slot().intValue();
            int slotsTaken = slotsNeeded(e.numberOfBytes());
            int slotEnd = slotStart + slotsTaken;
            if (slotEnd > maxSlotEnd) maxSlotEnd = slotEnd;
        }
        return maxSlotEnd;
    }

    /**
     * Bytes irosiți în layout-ul curent. Pentru fiecare slot de start unic,
     * grupează variabilele care încep la acel slot și calculează cât din
     * sloturile ocupate de acel grup rămâne neutilizat:
     *   slotsOccupied * SLOT_SIZE − sum(numberOfBytes).
     */
    public int estimateWastedBytesForCurrentLayout(List<StorageEntry> entries) {
        return entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(StorageEntry::slot))
                .values()
                .stream()
                .mapToInt(group -> {
                    int sum = group.stream().mapToInt(StorageEntry::numberOfBytes).sum();
                    int slotsOccupied = slotsNeeded(sum);
                    return slotsOccupied * SLOT_SIZE - sum;
                })
                .sum();
    }

    public Estimate estimateForRecommendedOrder(List<RecommendedVariable> recommended) {
        int slots = 0;
        int usedInCurrentSlot = 0;
        int wasted = 0;

        for (RecommendedVariable variable : recommended) {
            int size = variable.sizeBytes();

            if (size >= SLOT_SIZE) {
                // Variabilă care ocupă unul sau mai multe sloturi întregi (ex: uint256 sau uint256[50]).
                if (usedInCurrentSlot > 0) {
                    wasted += SLOT_SIZE - usedInCurrentSlot;
                    slots++;
                    usedInCurrentSlot = 0;
                }
                int slotsTaken = slotsNeeded(size);
                slots += slotsTaken;
                // Ultimul slot al variabilei mari poate avea reziduu, dar pentru array-uri uniforme
                // (`uint256[N]`) e fix multiplu de 32 — wasted aici e 0.
                int remainder = size % SLOT_SIZE;
                if (remainder != 0) {
                    wasted += SLOT_SIZE - remainder;
                }
                continue;
            }

            if (usedInCurrentSlot + size > SLOT_SIZE) {
                wasted += SLOT_SIZE - usedInCurrentSlot;
                slots++;
                usedInCurrentSlot = 0;
            }

            usedInCurrentSlot += size;
            if (usedInCurrentSlot == SLOT_SIZE) {
                slots++;
                usedInCurrentSlot = 0;
            }
        }

        if (usedInCurrentSlot > 0) {
            wasted += SLOT_SIZE - usedInCurrentSlot;
            slots++;
        }

        return new Estimate(slots, wasted);
    }

    private static int slotsNeeded(int numberOfBytes) {
        if (numberOfBytes <= 0) return 1;
        return (numberOfBytes + SLOT_SIZE - 1) / SLOT_SIZE;
    }

    public record Estimate(int slots, int wastedBytes) {}
}
