package ro.uaic.storageadvisor.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class SolcInputBuilder {
    private SolcInputBuilder() {}

    public static String buildStandardJson(String fileName, String sourceCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode root = mapper.createObjectNode();
        root.put("language", "Solidity");

        ObjectNode sources = mapper.createObjectNode();
        ObjectNode source = mapper.createObjectNode();
        source.put("content", sourceCode);
        sources.set(fileName, source);
        root.set("sources", sources);

        ObjectNode settings = mapper.createObjectNode();
        ObjectNode outputSelection = mapper.createObjectNode();
        ObjectNode wildcardSource = mapper.createObjectNode();
        wildcardSource.putArray("").add("ast");
        wildcardSource.putArray("*").add("storageLayout");
        outputSelection.set("*", wildcardSource);
        settings.set("outputSelection", outputSelection);
        root.set("settings", settings);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
}
