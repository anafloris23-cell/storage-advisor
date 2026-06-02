package ro.uaic.storageadvisor.parser;

import com.fasterxml.jackson.databind.JsonNode;
import ro.uaic.storageadvisor.model.ContractLayout;
import ro.uaic.storageadvisor.model.StorageEntry;
import ro.uaic.storageadvisor.model.StructDefinition;
import ro.uaic.storageadvisor.model.StructField;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StorageLayoutParser {

    public Map<String, ContractLayout> parseAll(JsonNode compilerOutput) {
        JsonNode contractsRoot = compilerOutput.path("contracts");
        if (contractsRoot.isMissingNode() || contractsRoot.isEmpty()) {
            throw new IllegalStateException("Niciun contract returnat de solc.");
        }

        Map<String, ContractLayout> result = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> sources = contractsRoot.fields();
        while (sources.hasNext()) {
            Map.Entry<String, JsonNode> sourceEntry = sources.next();
            String sourceUnit = sourceEntry.getKey();
            JsonNode contractsByName = sourceEntry.getValue();

            Iterator<Map.Entry<String, JsonNode>> contracts = contractsByName.fields();
            while (contracts.hasNext()) {
                Map.Entry<String, JsonNode> contractEntry = contracts.next();
                String contractName = contractEntry.getKey();
                JsonNode storageLayout = contractEntry.getValue().path("storageLayout");
                if (storageLayout.isMissingNode()) {
                    continue;
                }
                String key = sourceUnit + ":" + contractName;
                result.put(key, new ContractLayout(
                        sourceUnit,
                        contractName,
                        parseEntries(storageLayout),
                        parseStructs(storageLayout)
                ));
            }
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("Niciun storageLayout în output-ul solc.");
        }

        return result;
    }

    public ContractLayout extractFirstContractLayout(JsonNode compilerOutput) {
        return parseAll(compilerOutput).values().iterator().next();
    }

    private List<StorageEntry> parseEntries(JsonNode storageLayout) {
        JsonNode storage = storageLayout.path("storage");
        JsonNode types = storageLayout.path("types");

        List<StorageEntry> entries = new ArrayList<>();
        for (JsonNode entry : storage) {
            String typeId = entry.path("type").asText(); //ex. "t_uint256"
            JsonNode typeNode = types.path(typeId); //metadat despre tip

            entries.add(new StorageEntry(
                    entry.path("astId").asInt(),
                    entry.path("contract").asText(),
                    entry.path("label").asText(),
                    entry.path("offset").asInt(),
                    new BigInteger(entry.path("slot").asText("0")),
                    typeId,
                    safeParseInt(typeNode.path("numberOfBytes").asText("0")),  // DIMENSIUNEA: 1, 32, 20, ...
                    typeNode.path("label").asText(typeId),
                    typeNode.path("encoding").asText("unknown") //inplace, mapping...
            ));
        }

        return entries;
    }

    /**
     * Extrage definițiile struct-urilor din `storageLayout.types`.
     * Sunt incluse doar struct-urile cu encoding `inplace` și cu `members`
     * (struct-urile referite de variabilele contractului, direct sau prin mapping/array).
     */
    private List<StructDefinition> parseStructs(JsonNode storageLayout) {
        JsonNode types = storageLayout.path("types");
        if (types.isMissingNode() || types.isEmpty()) {
            return List.of();
        }

        List<StructDefinition> structs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Iterator<Map.Entry<String, JsonNode>> it = types.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> typeEntry = it.next();
            String typeId = typeEntry.getKey();
            JsonNode typeNode = typeEntry.getValue();

            if (!typeId.contains("t_struct")) {
                continue;
            }
            JsonNode members = typeNode.path("members");
            if (!members.isArray() || members.isEmpty()) {
                continue;
            }
            if (!seen.add(typeId)) {
                continue;
            }

            List<StructField> fields = new ArrayList<>();
            for (JsonNode member : members) {
                String memberTypeId = member.path("type").asText();
                JsonNode memberType = types.path(memberTypeId);
                fields.add(new StructField(
                        member.path("label").asText(),
                        memberTypeId,
                        memberType.path("label").asText(memberTypeId),
                        safeParseInt(memberType.path("numberOfBytes").asText("0")),
                        memberType.path("encoding").asText("unknown"),
                        safeParseInt(member.path("slot").asText("0")),
                        member.path("offset").asInt()
                ));
            }

            structs.add(new StructDefinition(
                    typeId,
                    typeNode.path("label").asText(typeId),
                    safeParseInt(typeNode.path("numberOfBytes").asText("0")),
                    fields
            ));
        }

        return structs;
    }

    private int safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
