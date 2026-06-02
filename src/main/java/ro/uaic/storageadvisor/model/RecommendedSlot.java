package ro.uaic.storageadvisor.model;

import java.util.List;

public record RecommendedSlot(
        int slotIndex,
        List<RecommendedVariable> variables,
        int usedBytes,
        int wastedBytes
) {
    public boolean isFull() {
        return usedBytes == 32;
    }
}
