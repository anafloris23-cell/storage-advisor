package ro.uaic.storageadvisor.analysis;

import ro.uaic.storageadvisor.model.Issue;
import ro.uaic.storageadvisor.model.StorageEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InefficiencyDetector {

    public List<Issue> detect(List<StorageEntry> entries) {
        List<Issue> issues = new ArrayList<>();
        issues.addAll(detectPartiallyWastedSlots(entries));
        issues.addAll(detectSmallTypesSeparatedByLargeType(entries));
        return issues;
    }

    private List<Issue> detectPartiallyWastedSlots(List<StorageEntry> entries) {
        Map<java.math.BigInteger, List<StorageEntry>> bySlot = entries.stream()
                .collect(Collectors.groupingBy(StorageEntry::slot));

        List<Issue> issues = new ArrayList<>();
        for (Map.Entry<java.math.BigInteger, List<StorageEntry>> slotEntry : bySlot.entrySet()) {
            int used = slotEntry.getValue().stream().mapToInt(StorageEntry::numberOfBytes).sum();
            if (used < 32) {
                String variables = slotEntry.getValue().stream()
                        .map(StorageEntry::label)
                        .sorted()
                        .collect(Collectors.joining(", "));
                issues.add(new Issue(
                        "PARTIAL_SLOT_WASTE",
                        used <= 16 ? "MEDIUM" : "LOW",
                        variables,
                        "Slot " + slotEntry.getKey() + " uses only " + used + " of 32 bytes.",
                        "Check whether the small variables can be packed more efficiently."
                ));
            }
        }
        return issues;
    }

    private List<Issue> detectSmallTypesSeparatedByLargeType(List<StorageEntry> entries) {
        List<StorageEntry> sorted = entries.stream()
                .sorted(Comparator.comparing(StorageEntry::slot).thenComparing(StorageEntry::offset))
                .toList();

        List<Issue> issues = new ArrayList<>();
        for (int i = 1; i < sorted.size() - 1; i++) {
            StorageEntry prev = sorted.get(i - 1);
            StorageEntry curr = sorted.get(i);
            StorageEntry next = sorted.get(i + 1);

            if (prev.isPackable() && next.isPackable() && curr.numberOfBytes() >= 32) {
                issues.add(new Issue(
                        "SUBOPTIMAL_ORDER",
                        "MEDIUM",
                        curr.label(),
                        "The large type '" + curr.label() + "' separates two small variables that could be grouped more efficiently.",
                        "Consider reordering variables " + prev.label() + ", " + next.label() + " before " + curr.label() + "."
                ));
            }
        }
        return issues;
    }
}
