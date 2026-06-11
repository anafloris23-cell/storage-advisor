package ro.uaic.storageadvisor.web.dto;

import java.util.List;

/**
 * Răspunsul de succes al endpoint-ului /api/analyze.
 *
 * @param preprocessing detalii despre preprocesarea aplicată sursei
 * @param contracts     câte un rezultat (raport + layout curent) pentru fiecare contract găsit
 */
public record AnalyzeResponse(
        PreprocessingInfo preprocessing,
        List<ContractResult> contracts
) {}
