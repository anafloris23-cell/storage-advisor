package ro.uaic.storageadvisor.web.dto;

/**
 * O variabilă în layout-ul curent (așa cum a fost dispusă de solc), pentru diagrama "before".
 *
 * @param label     numele variabilei
 * @param typeLabel tipul declarat (ex: "uint128")
 * @param sizeBytes dimensiunea în bytes
 * @param offset    poziția în cadrul slotului de start (bytes de la dreapta)
 */
public record CurrentVariableView(
        String label,
        String typeLabel,
        int sizeBytes,
        int offset
) {}
