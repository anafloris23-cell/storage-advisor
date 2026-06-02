package ro.uaic.storageadvisor.analysis;

import ro.uaic.storageadvisor.model.AnalysisReport;
import ro.uaic.storageadvisor.model.ContractLayout;
import ro.uaic.storageadvisor.model.Issue;
import ro.uaic.storageadvisor.model.RecommendedSlot;
import ro.uaic.storageadvisor.model.RecommendedVariable;
import ro.uaic.storageadvisor.model.StructOptimization;

import java.util.ArrayList;
import java.util.List;

public class AnalysisService {
    private final InefficiencyDetector detector = new InefficiencyDetector();
    private final PackingHeuristic heuristic = new PackingHeuristic();
    private final SlotEstimator estimator = new SlotEstimator();
    private final StructOptimizer structOptimizer = new StructOptimizer();

    public AnalysisReport analyze(ContractLayout layout) {
        List<Issue> issues = detector.detect(layout.entries());

        List<RecommendedSlot> recommendedSlots = heuristic.recommendSlotLayout(layout.entries());
        List<RecommendedVariable> recommendation = new ArrayList<>();
        for (RecommendedSlot s : recommendedSlots) {
            recommendation.addAll(s.variables());
        }

        int currentSlots = estimator.estimateSlotsForCurrentLayout(layout.entries());
        int currentWastedBytes = estimator.estimateWastedBytesForCurrentLayout(layout.entries());
        SlotEstimator.Estimate estimatedRecommendation = estimator.estimateForRecommendedOrder(recommendation);

        List<StructOptimization> structOptimizations =
                structOptimizer.optimizeAll(layout.structs(), layout.entries());

        return new AnalysisReport(
                layout.sourceUnit(),
                layout.contractName(),
                layout.entries().size(),
                currentSlots,
                estimatedRecommendation.slots(),
                currentWastedBytes,
                estimatedRecommendation.wastedBytes(),
                issues,
                recommendation,
                recommendedSlots,
                heuristic.lastStrategyUsed().label(),
                structOptimizations
        );
    }
}
