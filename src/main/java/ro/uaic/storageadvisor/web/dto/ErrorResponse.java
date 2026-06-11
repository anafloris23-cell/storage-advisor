package ro.uaic.storageadvisor.web.dto;

/**
 * Răspuns standard de eroare (ex: solc lipsă, erori de compilare, sursă goală).
 *
 * @param error mesajul de eroare destinat afișării în interfață
 */
public record ErrorResponse(String error) {}
