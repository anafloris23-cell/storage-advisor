package ro.uaic.storageadvisor.model;

import java.util.List;

/**
 * Rezultatul optimizării layout-ului intern al unui struct.
 */
public record StructOptimization(
        String typeId,
        String label,
        int currentSlots,
        int optimalSlots,
        int directInstances,
        List<RecommendedVariable> recommendedFieldOrder
) {
    public int savedSlotsPerInstance() {
        return currentSlots - optimalSlots;
    }

    public int totalSavedSlots() {
        return savedSlotsPerInstance() * Math.max(directInstances, 0);
    }

    public boolean hasImprovement() {
        return savedSlotsPerInstance() > 0;
    }
}
