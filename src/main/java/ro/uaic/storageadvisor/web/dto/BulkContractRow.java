package ro.uaic.storageadvisor.web.dto;

/**
 * O linie din raportul bulk, expusă în tabelul „top contracte" din pagina de raport.
 *
 * @param file            calea relativă a fișierului în dataset (folosită la click → încărcare)
 * @param contract        numele contractului
 * @param strategy        strategia de packing folosită (DP-bitmask / FFD)
 * @param kind            proveniența: "real", "synthetic" sau "other" (derivată din cale)
 * @param savedSlots      sloturi economisite la nivel de state vars
 * @param savedBytes      bytes irosiți recuperați
 * @param structSavedSlots sloturi economisite din optimizarea structurilor
 */
public record BulkContractRow(
        String file,
        String contract,
        String strategy,
        String kind,
        int savedSlots,
        int savedBytes,
        int structSavedSlots
) {}
