package ro.uaic.storageadvisor.cli;

import com.fasterxml.jackson.databind.JsonNode;
import ro.uaic.storageadvisor.analysis.AnalysisService;
import ro.uaic.storageadvisor.compiler.SolcRunner;
import ro.uaic.storageadvisor.compiler.SolidityPreprocessor;
import ro.uaic.storageadvisor.model.AnalysisReport;
import ro.uaic.storageadvisor.model.ContractLayout;
import ro.uaic.storageadvisor.parser.StorageLayoutParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Rulează StorageAdvisor recursiv pe toate fișierele `.sol` dintr-un director
 * și exportă rezultatele într-un CSV.
 *
 * Usage: mvn exec:java
 *     -Dexec.mainClass=ro.uaic.storageadvisor.cli.BulkAnalyzer
 *     -Dexec.args="examples/corpus/openzeppelin bulk-report.csv"
 */
public final class BulkAnalyzer {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: BulkAnalyzer <input-dir> [output.csv]");
            System.exit(1);
        }

        Path root = Path.of(args[0]);
        Path csvOut = Path.of(args.length > 1 ? args[1] : "bulk-report.csv");

        if (!Files.isDirectory(root)) {
            System.err.println("Nu e director: " + root);
            System.exit(2);
        }

        List<Row> rows = new ArrayList<>();
        int fileCount = 0;
        int contractCount = 0;
        int fileErrors = 0;

        List<Path> solFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            solFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sol"))
                    .filter(p -> !p.getFileName().toString().endsWith(".preprocessed.sol"))
                    .sorted()
                    .toList();
        }

        System.out.println("Found " + solFiles.size() + " .sol files in " + root);
        System.out.println();

        for (Path file : solFiles) {
            fileCount++;
            String rel = root.relativize(file).toString().replace('\\', '/');
            System.out.printf("[%d/%d] %s%n", fileCount, solFiles.size(), rel);
            try {
                List<Row> fileRows = analyzeFile(file, rel);
                rows.addAll(fileRows);
                contractCount += fileRows.size();
            } catch (Throwable t) {
                fileErrors++;
                rows.add(Row.error(rel, "", shortError(t)));
            }
        }

        writeCsv(csvOut, rows);
        printSummary(rows, fileCount, fileErrors);
        System.out.println();
        System.out.println("CSV scris în: " + csvOut.toAbsolutePath());
    }

    private static List<Row> analyzeFile(Path file, String relPath) throws Exception {
        SolcRunner runner = new SolcRunner();
        JsonNode out = runner.compile(file);
        SolidityPreprocessor.Result pre = runner.lastPreprocessResult();

        StorageLayoutParser parser = new StorageLayoutParser();
        Map<String, ContractLayout> layouts = parser.parseAll(out);

        AnalysisService svc = new AnalysisService();
        List<Row> rows = new ArrayList<>();
        for (ContractLayout layout : layouts.values()) {
            AnalysisReport rep = svc.analyze(layout);
            rows.add(Row.fromReport(relPath, rep, pre));
        }
        if (rows.isEmpty()) {
            // Niciun contract cu storage (poate doar interface/library)
            rows.add(Row.empty(relPath, pre));
        }
        return rows;
    }

    private static String shortError(Throwable t) {
        String msg = t.getMessage();
        if (msg == null) msg = t.getClass().getSimpleName();
        // Compactez: prima linie din mesaj, max 240 chars.
        int nl = msg.indexOf('\n');
        if (nl > 0) msg = msg.substring(0, nl);
        if (msg.length() > 240) msg = msg.substring(0, 237) + "...";
        return msg;
    }

    private static void writeCsv(Path csvOut, List<Row> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("file,contract,status,error,current_slots,recommended_slots,saved_slots,"
                + "current_wasted,recommended_wasted,saved_wasted,strategy,"
                + "imports_removed,functions_removed,modifiers_removed,types_replaced,"
                + "structs_total,structs_improved,struct_saved_slots\n");
        for (Row r : rows) {
            sb.append(csv(r.file)).append(',')
                    .append(csv(r.contract)).append(',')
                    .append(r.status).append(',')
                    .append(csv(r.error)).append(',')
                    .append(r.currentSlots).append(',')
                    .append(r.recommendedSlots).append(',')
                    .append(r.savedSlots()).append(',')
                    .append(r.currentWasted).append(',')
                    .append(r.recommendedWasted).append(',')
                    .append(r.savedWasted()).append(',')
                    .append(csv(r.strategy)).append(',')
                    .append(r.importsRemoved).append(',')
                    .append(r.functionsRemoved).append(',')
                    .append(r.modifiersRemoved).append(',')
                    .append(r.typesReplaced).append(',')
                    .append(r.structsTotal).append(',')
                    .append(r.structsImproved).append(',')
                    .append(r.structSavedSlots)
                    .append('\n');
        }
        Files.writeString(csvOut, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String csv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static void printSummary(List<Row> rows, int fileCount, int fileErrors) {
        int ok = 0, err = 0, empty = 0, totalSaved = 0, totalSavedBytes = 0;
        int dp = 0, ffd = 0;
        int structsTotal = 0, structsImproved = 0, structSavedSlots = 0;
        List<Row> winners = new ArrayList<>();
        for (Row r : rows) {
            switch (r.status) {
                case "OK" -> {
                    ok++;
                    totalSaved += r.savedSlots();
                    totalSavedBytes += r.savedWasted();
                    structsTotal += r.structsTotal;
                    structsImproved += r.structsImproved;
                    structSavedSlots += r.structSavedSlots;
                    if (r.strategy != null && r.strategy.startsWith("DP")) dp++;
                    else if (r.strategy != null && r.strategy.startsWith("First")) ffd++;
                    if (r.savedSlots() > 0 || r.structSavedSlots > 0) winners.add(r);
                }
                case "ERROR" -> err++;
                case "EMPTY" -> empty++;
            }
        }
        winners.sort(Comparator.<Row>comparingInt(r -> -r.totalSavedSlots())
                .thenComparingInt(r -> -r.savedWasted()));

        System.out.println();
        System.out.println("─────────────────────────────────────");
        System.out.println("SUMMARY");
        System.out.println("─────────────────────────────────────");
        System.out.println("Fișiere procesate:       " + fileCount + " (erori: " + fileErrors + ")");
        System.out.println("Contracte analizate OK:  " + ok);
        System.out.println("Contracte cu eroare:     " + err);
        System.out.println("Contracte fără storage:  " + empty);
        System.out.println("Strategie DP-bitmask:    " + dp);
        System.out.println("Strategie FFD fallback:  " + ffd);
        System.out.println("Sloturi economisite tot: " + totalSaved);
        System.out.println("Bytes irosiți recuperați: " + totalSavedBytes);
        System.out.println("Struct-uri analizate:    " + structsTotal);
        System.out.println("Struct-uri optimizabile: " + structsImproved);
        System.out.println("Sloturi struct salvate:  " + structSavedSlots + " (× instanțe directe)");
        System.out.println();
        if (!winners.isEmpty()) {
            System.out.println("Top 10 contracte cu cel mai mare câștig (state vars + struct):");
            int show = Math.min(10, winners.size());
            for (int i = 0; i < show; i++) {
                Row r = winners.get(i);
                String detail;
                if (r.structSavedSlots > 0 && r.savedSlots() > 0) {
                    detail = String.format("−%d sloturi (%d state + %d struct)",
                            r.totalSavedSlots(), r.savedSlots(), r.structSavedSlots);
                } else if (r.structSavedSlots > 0) {
                    detail = String.format("−%d sloturi (struct intern)", r.structSavedSlots);
                } else {
                    detail = String.format("−%d sloturi, −%d bytes", r.savedSlots(), r.savedWasted());
                }
                System.out.printf("  %-50s %s: %s%n",
                        truncate(r.file + ":" + r.contract, 50),
                        r.strategy != null && r.strategy.startsWith("First") ? "[FFD]" : "[DP] ",
                        detail);
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    // ──────────────────────────────────────────────────────────────────

    private static final class Row {
        String file;
        String contract;
        String status;       // OK / ERROR / EMPTY
        String error = "";
        int currentSlots;
        int recommendedSlots;
        int currentWasted;
        int recommendedWasted;
        String strategy = "";
        int importsRemoved;
        int functionsRemoved;
        int modifiersRemoved;
        int typesReplaced;
        int structsTotal;
        int structsImproved;
        int structSavedSlots;

        int savedSlots() { return currentSlots - recommendedSlots; }
        int savedWasted() { return currentWasted - recommendedWasted; }
        int totalSavedSlots() { return savedSlots() + structSavedSlots; }

        static Row fromReport(String file, AnalysisReport rep, SolidityPreprocessor.Result pre) {
            Row r = new Row();
            r.file = file;
            r.contract = rep.contractName();
            r.status = "OK";
            r.currentSlots = rep.currentEstimatedSlots();
            r.recommendedSlots = rep.recommendedEstimatedSlots();
            r.currentWasted = rep.currentWastedBytes();
            r.recommendedWasted = rep.recommendedWastedBytes();
            r.strategy = rep.packingStrategy();
            if (pre != null) {
                r.importsRemoved = pre.importsCommented();
                r.functionsRemoved = pre.bodiesStripped();
                r.typesReplaced = pre.stubbedTypes() == null ? 0 : pre.stubbedTypes().size();
            }
            if (rep.structOptimizations() != null) {
                r.structsTotal = rep.structOptimizations().size();
                for (var so : rep.structOptimizations()) {
                    if (so.hasImprovement()) {
                        r.structsImproved++;
                        r.structSavedSlots += so.totalSavedSlots();
                    }
                }
            }
            return r;
        }

        static Row error(String file, String contract, String error) {
            Row r = new Row();
            r.file = file;
            r.contract = contract;
            r.status = "ERROR";
            r.error = error;
            return r;
        }

        static Row empty(String file, SolidityPreprocessor.Result pre) {
            Row r = new Row();
            r.file = file;
            r.contract = "";
            r.status = "EMPTY";
            if (pre != null) {
                r.importsRemoved = pre.importsCommented();
                r.functionsRemoved = pre.bodiesStripped();
                r.typesReplaced = pre.stubbedTypes() == null ? 0 : pre.stubbedTypes().size();
            }
            return r;
        }
    }
}
