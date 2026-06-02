package ro.uaic.storageadvisor.model;

import java.math.BigInteger;

public record StorageEntry(
        int astId,
        String contract,
        String label,
        int offset,
        BigInteger slot,
        String typeId,
        int numberOfBytes,
        String typeLabel,
        String encoding
) {
    public boolean isInplace() {
        return "inplace".equalsIgnoreCase(encoding);
    }

    public boolean isPackable() {
        return isInplace() && numberOfBytes > 0 && numberOfBytes < 32;
    }
}
