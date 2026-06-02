package ro.uaic.storageadvisor.model;

public record RecommendedVariable(
        String label,
        String typeLabel,
        int sizeBytes,
        String note
) {}
