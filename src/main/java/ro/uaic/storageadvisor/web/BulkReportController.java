package ro.uaic.storageadvisor.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.uaic.storageadvisor.web.dto.BulkContractRow;
import ro.uaic.storageadvisor.web.dto.BulkReport;
import ro.uaic.storageadvisor.web.dto.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Expune raportul agregat al analizei pe tot dataset-ul.
 *
 * <ul>
 *   <li>{@code GET /api/bulk-report} — citește {@code reports/bulk-report.csv}, agregă statisticile
 *       (cu separarea real/sintetic) și întoarce top-N câștigători.</li>
 *   <li>{@code GET /api/dataset-source?path=...} — întoarce sursa unui contract din dataset,
 *       pentru a-l deschide în Analizor la click pe un rând din tabel.</li>
 * </ul>
 *
 * Citește CSV-ul „înghețat" produs offline de {@code cli.BulkAnalyzer}; nu re-analizează la cerere.
 */
@RestController
@RequestMapping("/api")
public class BulkReportController {

    /** Câte contracte afișăm în tabelul „top câștigători". */
    private static final int TOP_N = 30;

    private final Path csvPath;
    private final Path datasetDir;

    public BulkReportController(
            @Value("${storageadvisor.bulk-report.path:reports/bulk-report.csv}") String csvPath,
            @Value("${storageadvisor.dataset.dir:dataset}") String datasetDir) {
        this.csvPath = Path.of(csvPath);
        this.datasetDir = Path.of(datasetDir);
    }

    @GetMapping("/bulk-report")
    public ResponseEntity<?> bulkReport() {
        if (!Files.isRegularFile(csvPath)) {
            return ResponseEntity.ok(BulkReport.notGenerated());
        }
        try {
            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            return ResponseEntity.ok(aggregate(lines));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Nu am putut citi raportul: " + e.getMessage()));
        }
    }

    @GetMapping("/dataset-source")
    public ResponseEntity<?> datasetSource(@RequestParam String path) {
        Path base = datasetDir.toAbsolutePath().normalize();
        Path target = base.resolve(path).normalize();
        // Protecție împotriva path traversal: ținta trebuie să rămână în interiorul dataset-ului.
        if (!target.startsWith(base)) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Cale invalidă."));
        }
        if (!Files.isRegularFile(target)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("Fișier inexistent: " + path));
        }
        try {
            String source = Files.readString(target, StandardCharsets.UTF_8);
            return ResponseEntity.ok(new SourceResponse(source));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Eroare la citirea fișierului: " + e.getMessage()));
        }
    }

    private record SourceResponse(String source) {}

    // ──────────────────────────────────────────────────────────────────
    // Agregare CSV
    // ──────────────────────────────────────────────────────────────────

    private static BulkReport aggregate(List<String> lines) {
        int total = 0, ok = 0, err = 0;
        int realCount = 0, synthCount = 0;
        int savedSlots = 0, savedBytes = 0;
        int realSaved = 0, synthSaved = 0;
        int dp = 0, ffd = 0;
        int structsImproved = 0, structSaved = 0;
        int winners = 0;
        List<BulkContractRow> all = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) { // sari peste header
            String line = lines.get(i);
            if (line.isBlank()) continue;
            List<String> f = parseCsvLine(line);
            if (f.size() < 18) continue;

            String file = f.get(0);
            String contract = f.get(1);
            String status = f.get(2);
            int sSlots = parseInt(f.get(6));
            int sBytes = parseInt(f.get(9));
            String strategy = f.get(10);
            int structImp = parseInt(f.get(16));
            int structSlots = parseInt(f.get(17));

            total++;
            if (!"OK".equals(status)) {
                if ("ERROR".equals(status)) err++;
                continue;
            }
            ok++;

            String kind = kindOf(file);
            int rowTotal = sSlots + structSlots;
            savedSlots += rowTotal;
            savedBytes += sBytes;
            structsImproved += structImp;
            structSaved += structSlots;
            if (strategy != null && strategy.startsWith("First")) ffd++;
            else dp++;

            if ("real".equals(kind)) { realCount++; realSaved += rowTotal; }
            else if ("synthetic".equals(kind)) { synthCount++; synthSaved += rowTotal; }

            if (rowTotal > 0) {
                winners++;
                all.add(new BulkContractRow(file, contract,
                        strategy != null && strategy.startsWith("First") ? "FFD" : "DP-bitmask",
                        kind, sSlots, sBytes, structSlots));
            }
        }

        all.sort(Comparator
                .comparingInt((BulkContractRow r) -> r.savedSlots() + r.structSavedSlots()).reversed()
                .thenComparing(Comparator.comparingInt(BulkContractRow::savedBytes).reversed()));
        List<BulkContractRow> top = all.size() > TOP_N ? all.subList(0, TOP_N) : all;

        return new BulkReport(true, total, ok, err, realCount, synthCount,
                savedSlots, savedBytes, realSaved, synthSaved, dp, ffd,
                structsImproved, structSaved, winners, List.copyOf(top));
    }

    private static String kindOf(String file) {
        if (file == null) return "other";
        String f = file.replace('\\', '/');
        if (f.startsWith("real/")) return "real";
        if (f.startsWith("synthetic/")) return "synthetic";
        return "other";
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Parser CSV minimal: tratează câmpuri între ghilimele și ghilimelele dublate. */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
