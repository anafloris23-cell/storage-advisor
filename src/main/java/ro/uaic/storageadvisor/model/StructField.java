package ro.uaic.storageadvisor.model;

/**
 * Un câmp din interiorul unei definiții de struct.
 * Oglindește o intrare din `storageLayout.types[structType].members`.
 */
public record StructField(
        String label,
        String typeId,
        String typeLabel,
        int numberOfBytes,
        String encoding,
        int slot,
        int offset
) {
    public boolean isInplace() {
        return "inplace".equalsIgnoreCase(encoding);
    }

    public boolean isPackable() {
        return isInplace() && numberOfBytes > 0 && numberOfBytes < 32;
    }
}
