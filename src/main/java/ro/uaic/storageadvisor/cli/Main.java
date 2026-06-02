package ro.uaic.storageadvisor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import ro.uaic.storageadvisor.analysis.AnalysisService;
import ro.uaic.storageadvisor.compiler.SolcRunner;
import ro.uaic.storageadvisor.model.AnalysisReport;
import ro.uaic.storageadvisor.model.ContractLayout;
import ro.uaic.storageadvisor.model.RecommendedSlot;
import ro.uaic.storageadvisor.model.RecommendedVariable;
import ro.uaic.storageadvisor.parser.StorageLayoutParser;
import ro.uaic.storageadvisor.report.ReportWriter;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  mvn exec:java -Dexec.args=\"path/to/Contract.sol\"            # single file");
            System.out.println("  mvn exec:java -Dexec.args=\"path/to/dir [output.csv]\"        # bulk mode");
            return;
        }

        Path target = Path.of(args[0]);
        if (java.nio.file.Files.isDirectory(target)) {
            BulkAnalyzer.main(args);
            return;
        }

        Path solidityFile = target;

        SolcRunner runner = new SolcRunner();
        JsonNode compilerOutput = runner.compile(solidityFile);

        var preResult = runner.lastPreprocessResult();
        if (preResult != null && preResult.modified()) {
            System.out.println("[Preprocesare]");
            for (String w : preResult.warnings()) {
                System.out.println("  " + w);
            }
            System.out.println();
        }

        StorageLayoutParser parser = new StorageLayoutParser();
        Map<String, ContractLayout> layouts = parser.parseAll(compilerOutput);

        AnalysisService analysisService = new AnalysisService();
        ReportWriter writer = new ReportWriter();

        System.out.println("Contracte analizate: " + layouts.size());

        for (ContractLayout layout : layouts.values()) {
            AnalysisReport report = analysisService.analyze(layout);

            String safeName = report.contractName().replaceAll("[^A-Za-z0-9_-]", "_");
            Path jsonPath = Path.of("analysis-report-" + safeName + ".json");
            Path mdPath = Path.of("analysis-report-" + safeName + ".md");

            writer.writeJson(report, jsonPath);
            writer.writeMarkdown(report, mdPath);

            System.out.println();
            System.out.println("=== " + report.contractName() + " (" + report.sourceUnit() + ") ===");
            System.out.println("  Variabile declarate:    " + report.declaredVariables());
            System.out.println("  Sloturi curente:        " + report.currentEstimatedSlots());
            System.out.println("  Sloturi recomandate:    " + report.recommendedEstimatedSlots());
            System.out.println("  Bytes irosiți curent:   " + report.currentWastedBytes());
            System.out.println("  Bytes irosiți optim:    " + report.recommendedWastedBytes());
            System.out.println("  Issues detectate:       " + report.issues().size());
            System.out.println("  Strategie packing:      " + report.packingStrategy());

            if (!report.issues().isEmpty()) {
                System.out.println();
                System.out.println("  Probleme detectate:");
                for (var issue : report.issues()) {
                    System.out.println("    [" + issue.severity() + "] "
                            + issue.code() + " — " + issue.message());
                }
            }

            System.out.println();
            System.out.println("  Layout recomandat per slot:");
            for (RecommendedSlot slot : report.recommendedSlots()) {
                String varList = slot.variables().stream()
                        .map(v -> v.label() + " (" + v.typeLabel() + ", " + v.sizeBytes() + byteWord(v.sizeBytes()) + ")")
                        .collect(Collectors.joining(" + "));
                System.out.printf("    Slot %2d: %-50s [%2d/32 B, %2d irosiți]%n",
                        slot.slotIndex(), varList, slot.usedBytes(), slot.wastedBytes());
            }

            System.out.println();
            System.out.println("  Ordine recomandată în Solidity:");
            for (RecommendedVariable v : report.recommendedOrder()) {
                System.out.printf("    %s %s;  // %d%s%n",
                        v.typeLabel(), v.label(), v.sizeBytes(), byteWord(v.sizeBytes()));
            }

            if (!report.structOptimizations().isEmpty()) {
                System.out.println();
                System.out.println("  Optimizări struct:");
                for (var so : report.structOptimizations()) {
                    String marker = so.hasImprovement() ? "↓" : "=";
                    System.out.printf("    %s %s: %d → %d sloturi/instanță",
                            marker, so.label(), so.currentSlots(), so.optimalSlots());
                    if (so.hasImprovement()) {
                        System.out.printf("  (−%d/instanță; %d instanțe directe → −%d sloturi)",
                                so.savedSlotsPerInstance(), so.directInstances(), so.totalSavedSlots());
                    }
                    System.out.println();
                    if (so.hasImprovement()) {
                        String order = so.recommendedFieldOrder().stream()
                                .map(v -> v.typeLabel() + " " + v.label())
                                .collect(Collectors.joining("; "));
                        System.out.println("        Reordonare câmpuri: " + order);
                    }
                }
            }

            System.out.println();
        }
    }

    private static String byteWord(int n) {
        return n == 1 ? " byte" : " bytes";
    }
}
