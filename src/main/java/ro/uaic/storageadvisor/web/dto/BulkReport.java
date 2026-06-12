package ro.uaic.storageadvisor.web.dto;

import java.util.List;

/**
 * Raportul agregat al analizei pe tot dataset-ul (citit din {@code reports/bulk-report.csv}),
 * expus prin {@code GET /api/bulk-report} și afișat în pagina „Raport dataset".
 *
 * @param generated         false dacă raportul nu a fost generat încă (CSV inexistent)
 * @param contractsTotal    total contracte (OK + ERROR + EMPTY)
 * @param contractsOk       contracte analizate cu succes
 * @param contractsError    contracte care au dat eroare
 * @param realCount         contracte OK din subsetul real
 * @param syntheticCount    contracte OK din subsetul sintetic
 * @param totalSavedSlots   sloturi economisite total (state vars + struct)
 * @param totalSavedBytes   bytes irosiți recuperați total
 * @param realSavedSlots    sloturi economisite în subsetul real
 * @param syntheticSavedSlots sloturi economisite în subsetul sintetic
 * @param dpCount           contracte rezolvate cu DP-bitmask (optim absolut)
 * @param ffdCount          contracte rezolvate cu euristica FFD (fallback)
 * @param structsImproved   structuri cu îmbunătățire
 * @param structSavedSlots  sloturi economisite din structuri
 * @param winnersCount      contracte cu vreun câștig (saved_slots > 0 sau struct > 0)
 * @param top               primele N contracte cu cel mai mare câștig
 */
public record BulkReport(
        boolean generated,
        int contractsTotal,
        int contractsOk,
        int contractsError,
        int realCount,
        int syntheticCount,
        int totalSavedSlots,
        int totalSavedBytes,
        int realSavedSlots,
        int syntheticSavedSlots,
        int dpCount,
        int ffdCount,
        int structsImproved,
        int structSavedSlots,
        int winnersCount,
        List<BulkContractRow> top
) {
    /** Raport „gol" pentru cazul în care CSV-ul nu a fost încă generat. */
    public static BulkReport notGenerated() {
        return new BulkReport(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
