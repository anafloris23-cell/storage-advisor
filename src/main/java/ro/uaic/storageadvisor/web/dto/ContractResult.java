package ro.uaic.storageadvisor.web.dto;

import ro.uaic.storageadvisor.model.AnalysisReport;

import java.util.List;

/**
 * Rezultatul complet pentru un contract: raportul de analiză (logica validată)
 * plus layout-ul curent pe sloturi, construit în stratul web pentru diagrama "before".
 *
 * @param report      raportul produs de AnalysisService (sloturi recomandate, issues etc.)
 * @param currentSlots layout-ul curent pe sloturi (diagrama "before")
 */
public record ContractResult(
        AnalysisReport report,
        List<CurrentSlotView> currentSlots
) {}
