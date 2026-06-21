package ro.uaic.storageadvisor.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aduce codul sursă verificat al unui contract de pe Etherscan (Ethereum mainnet),
 * folosind API-ul V2 ({@code getsourcecode}).
 *
 * Răspunsul Etherscan poate conține fie un singur fișier, fie un proiect cu mai
 * multe fișiere (format standard-json). În al doilea caz se selectează fișierul
 * care declară contractul principal; restul dependențelor sunt tratate de
 * preprocesorul existent (analiză izolată).
 */
public class EtherscanClient {

    private static final Pattern ADDRESS = Pattern.compile("0x[a-fA-F0-9]{40}");

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public EtherscanClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /** Extrage o adresă {@code 0x...} dintr-un link sau șir; null dacă nu există. */
    public static String extractAddress(String input) {
        if (input == null) return null;
        Matcher m = ADDRESS.matcher(input);
        return m.find() ? m.group() : null;
    }

    /** Aduce sursa verificată a contractului de la adresa dată. */
    public FetchedContract fetchSource(String address) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "The Etherscan API key is not configured (etherscan.api-key).");
        }
        String url = "https://api.etherscan.io/v2/api?chainid=1&module=contract&action=getsourcecode"
                + "&address=" + address + "&apikey=" + apiKey;

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(resp.body());
        if (!"1".equals(root.path("status").asText())) {
            String msg = root.path("result").asText(root.path("message").asText("unknown error"));
            throw new IllegalStateException("Etherscan: " + msg);
        }

        JsonNode result = root.path("result").path(0);
        String contractName = result.path("ContractName").asText("");
        String sourceCode = result.path("SourceCode").asText("");
        if (sourceCode.isBlank()) {
            throw new IllegalStateException("The contract has no verified source on Etherscan.");
        }
        String solidity = extractSolidity(sourceCode, contractName);
        return new FetchedContract(contractName, solidity);
    }

    /** Extrage codul Solidity din câmpul SourceCode (single-file sau standard-json). */
    private String extractSolidity(String sourceCode, String contractName) throws Exception {
        String trimmed = sourceCode.trim();
        if (!trimmed.startsWith("{")) {
            return sourceCode; // single-file
        }
        // Multi-file: poate fi împachetat în acolade duble {{ ... }}.
        String json = trimmed;
        if (json.startsWith("{{") && json.endsWith("}}")) {
            json = json.substring(1, json.length() - 1);
        }
        JsonNode node = mapper.readTree(json);
        JsonNode sources = node.has("sources") ? node.get("sources") : node;

        String mainContent = null;
        StringBuilder all = new StringBuilder();
        for (Iterator<Map.Entry<String, JsonNode>> it = sources.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String content = e.getValue().path("content").asText("");
            if (content.isEmpty()) continue;
            all.append(content).append('\n');
            if (!contractName.isBlank() && content.contains("contract " + contractName)) {
                mainContent = content;
            }
        }
        return mainContent != null ? mainContent : all.toString();
    }

    /** Rezultatul aducerii: numele contractului principal și codul sursă. */
    public record FetchedContract(String contractName, String source) {}
}
