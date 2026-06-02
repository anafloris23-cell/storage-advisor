package ro.uaic.storageadvisor.model;

public record Issue(
        String code,
        String severity,
        String variable,
        String message,
        String recommendation
) {}
