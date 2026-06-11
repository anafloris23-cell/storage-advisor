package ro.uaic.storageadvisor.web.dto;

/**
 * Corpul cererii POST /api/analyze.
 *
 * @param source   codul sursă Solidity de analizat (obligatoriu)
 * @param filename numele de fișier folosit la compilare (opțional; implicit "Contract.sol").
 *                 Apare ca {@code sourceUnit} în rezultate.
 */
public record AnalyzeRequest(
        String source,
        String filename
) {}
