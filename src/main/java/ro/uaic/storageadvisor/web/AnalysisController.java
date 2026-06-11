package ro.uaic.storageadvisor.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.uaic.storageadvisor.analysis.AnalysisService;
import ro.uaic.storageadvisor.compiler.SolcRunner;
import ro.uaic.storageadvisor.compiler.SolidityPreprocessor;
import ro.uaic.storageadvisor.model.AnalysisReport;
import ro.uaic.storageadvisor.model.ContractLayout;
import ro.uaic.storageadvisor.parser.StorageLayoutParser;
import ro.uaic.storageadvisor.web.dto.AnalyzeRequest;
import ro.uaic.storageadvisor.web.dto.AnalyzeResponse;
import ro.uaic.storageadvisor.web.dto.ContractResult;
import ro.uaic.storageadvisor.web.dto.ErrorResponse;
import ro.uaic.storageadvisor.web.dto.PreprocessingInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Expune pipeline-ul de analiză StorageAdvisor prin HTTP.
 */
@RestController
@RequestMapping("/api")
public class AnalysisController {

    private static final Pattern UNSAFE_FILENAME = Pattern.compile("[^A-Za-z0-9._-]");

    private final StorageLayoutParser parser = new StorageLayoutParser();
    private final AnalysisService analysisService = new AnalysisService();

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody AnalyzeRequest request) {
        if (request == null || request.source() == null || request.source().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Sursa Solidity este goală."));
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("storageadvisor-");
            Path solFile = workDir.resolve(safeFilename(request.filename()));
            Files.writeString(solFile, request.source(), StandardCharsets.UTF_8);

            SolcRunner runner = new SolcRunner();
            JsonNode compilerOutput = runner.compile(solFile);

            SolidityPreprocessor.Result pre = runner.lastPreprocessResult();
            PreprocessingInfo preInfo = new PreprocessingInfo(
                    pre != null && pre.modified(),
                    pre != null ? pre.warnings() : List.of()
            );

            Map<String, ContractLayout> layouts = parser.parseAll(compilerOutput);

            List<ContractResult> contracts = new ArrayList<>();
            for (ContractLayout layout : layouts.values()) {
                AnalysisReport report = analysisService.analyze(layout);
                contracts.add(new ContractResult(report, CurrentLayoutBuilder.build(layout)));
            }

            return ResponseEntity.ok(new AnalyzeResponse(preInfo, contracts));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Eroare neașteptată la analiză: " + e.getMessage()));
        } finally {
            deleteRecursively(workDir);
        }
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "Contract.sol";
        }
        String base = Path.of(filename).getFileName().toString();
        String cleaned = UNSAFE_FILENAME.matcher(base).replaceAll("_");
        if (!cleaned.endsWith(".sol")) {
            cleaned = cleaned + ".sol";
        }
        return cleaned;
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
