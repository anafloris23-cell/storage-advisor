package ro.uaic.storageadvisor.model;

import java.util.List;

/**
 * Definiția unui struct extras din `storageLayout.types`.
 * Câmpurile (members) pot fi reordonate pentru optimizarea layout-ului intern.
 */
public record StructDefinition(
        String typeId,
        String label,
        int numberOfBytes,
        List<StructField> members
) {
    /** Numărul de sloturi ocupate de struct în layout-ul curent. */
    public int currentSlots() {
        return (numberOfBytes + 31) / 32;
    }
}
