package ro.uaic.storageadvisor.model;

import java.util.List;

public record ContractLayout(
        String sourceUnit,
        String contractName,
        List<StorageEntry> entries,
        List<StructDefinition> structs
) {}
