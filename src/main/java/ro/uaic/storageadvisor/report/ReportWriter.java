package ro.uaic.storageadvisor.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.uaic.storageadvisor.model.AnalysisReport;
import ro.uaic.storageadvisor.model.RecommendedSlot;
import ro.uaic.storageadvisor.model.RecommendedVariable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

public class ReportWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public void writeJson(AnalysisReport report, Path outputFile) throws Exception {
        Files.writeString(outputFile,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);
    }

    public void writeMarkdown(AnalysisReport report, Path outputFile) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# StorageAdvisor report\n\n");
        sb.append("- Contract: ").append(report.contractName()).append("\n");
        sb.append("- Source: ").append(report.sourceUnit()).append("\n");
        sb.append("- Declared variables: ").append(report.declaredVariables()).append("\n");
        sb.append("- Current estimated slots: ").append(report.currentEstimatedSlots()).append("\n");
        sb.append("- Recommended estimated slots: ").append(report.recommendedEstimatedSlots()).append("\n");
        sb.append("- Current wasted bytes: ").append(report.currentWastedBytes()).append("\n");
        sb.append("- Recommended wasted bytes: ").append(report.recommendedWastedBytes()).append("\n");
        sb.append("- Packing strategy: ").append(report.packingStrategy()).append("\n\n");

        sb.append("## Issues\n\n");
        if (report.issues().isEmpty()) {
            sb.append("Nu au fost detectate probleme evidente.\n\n");
        } else {
            for (var issue : report.issues()) {
                sb.append("- [").append(issue.severity()).append("] ")
                        .append(issue.code()).append(" — ")
                        .append(issue.message()).append(" Recomandare: ")
                        .append(issue.recommendation()).append("\n");
            }
            sb.append('\n');
        }

        sb.append("## Layout recomandat per slot\n\n");
        sb.append("| Slot | Variabile | Folosit | Irosit |\n");
        sb.append("|------|-----------|---------|--------|\n");
        for (RecommendedSlot s : report.recommendedSlots()) {
            String varList = s.variables().stream()
                    .map(v -> v.label() + " (" + v.typeLabel() + ", " + v.sizeBytes() + "B)")
                    .collect(Collectors.joining(" + "));
            sb.append("| ").append(s.slotIndex())
                    .append(" | ").append(varList)
                    .append(" | ").append(s.usedBytes()).append("/32 B")
                    .append(" | ").append(s.wastedBytes()).append(" B")
                    .append(" |\n");
        }
        sb.append("\n");

        sb.append("## Recommended order (lineară, pentru rescrierea sursei)\n\n");
        sb.append("```solidity\n");
        for (RecommendedVariable v : report.recommendedOrder()) {
            sb.append("    ").append(v.typeLabel()).append(" ").append(v.label()).append(";");
            sb.append("  // ").append(v.sizeBytes()).append(byteWord(v.sizeBytes()));
            sb.append("\n");
        }
        sb.append("```\n\n");

        if (!report.structOptimizations().isEmpty()) {
            sb.append("## Optimizări struct\n\n");
            sb.append("| Struct | Sloturi (actual → optim) | Câștig/instanță | Instanțe directe | Câștig total |\n");
            sb.append("|--------|--------------------------|-----------------|------------------|-------------|\n");
            for (var so : report.structOptimizations()) {
                sb.append("| ").append(so.label())
                        .append(" | ").append(so.currentSlots()).append(" → ").append(so.optimalSlots())
                        .append(" | ").append(so.savedSlotsPerInstance())
                        .append(" | ").append(so.directInstances())
                        .append(" | ").append(so.totalSavedSlots())
                        .append(" |\n");
            }
            sb.append('\n');
            for (var so : report.structOptimizations()) {
                if (!so.hasImprovement()) continue;
                sb.append("### ").append(so.label()).append(" — reordonare câmpuri\n\n");
                sb.append("```solidity\n");
                for (RecommendedVariable v : so.recommendedFieldOrder()) {
                    sb.append("    ").append(v.typeLabel()).append(' ').append(v.label()).append(';')
                            .append("  // ").append(v.sizeBytes()).append(byteWord(v.sizeBytes()))
                            .append('\n');
                }
                sb.append("```\n\n");
            }
        }

        sb.append("### Detalii pe variabilă\n\n");
        int index = 1;
        for (RecommendedVariable v : report.recommendedOrder()) {
            sb.append(index++)
                    .append(". ")
                    .append(v.label())
                    .append(" : ")
                    .append(v.typeLabel())
                    .append(" (")
                    .append(v.sizeBytes())
                    .append(byteWord(v.sizeBytes()))
                    .append(") — ")
                    .append(v.note())
                    .append("\n");
        }

        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
    }

    private String byteWord(int n) {
        return n == 1 ? " byte" : " bytes";
    }
}
