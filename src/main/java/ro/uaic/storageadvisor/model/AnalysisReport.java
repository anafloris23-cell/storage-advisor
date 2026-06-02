package ro.uaic.storageadvisor.model;

import java.util.List;

public record AnalysisReport(
        String sourceUnit,
        String contractName,
        int declaredVariables,
        int currentEstimatedSlots,
        int recommendedEstimatedSlots,
        int currentWastedBytes,
        int recommendedWastedBytes,
        List<Issue> issues,
        List<RecommendedVariable> recommendedOrder,
        List<RecommendedSlot> recommendedSlots,
        String packingStrategy,
        List<StructOptimization> structOptimizations
) {}
