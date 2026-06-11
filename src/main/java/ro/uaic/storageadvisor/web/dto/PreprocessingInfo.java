package ro.uaic.storageadvisor.web.dto;

import java.util.List;

/**
 * Informații despre preprocesarea aplicată sursei înainte de compilare
 * (imports comentate, moșteniri eliminate etc.).
 *
 * @param modified true dacă sursa a fost modificată pentru analiza izolată
 * @param warnings mesajele explicative despre transformările aplicate
 */
public record PreprocessingInfo(
        boolean modified,
        List<String> warnings
) {}
