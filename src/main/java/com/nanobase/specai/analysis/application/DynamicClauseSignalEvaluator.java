package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignal;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DynamicClauseSignalEvaluator implements ClauseSignalEvaluator {
    private final AnalysisCatalogPort catalog;

    public DynamicClauseSignalEvaluator(AnalysisCatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public ClauseSignalResult evaluate(ClauseSignalContext context) {
        PolicyConfiguration configuration = new PolicyConfiguration(
            catalog.policy(context.organizationId(), context.policyVersionId()).configuration());
        Map<String, Double> weights = configuration.requiredWeights("signalWeights");
        List<ClauseSignal> signals = new ArrayList<>();
        double weighted = 0;
        double appliedWeight = 0;
        for (Map.Entry<String, Double> configured : weights.entrySet()) {
            Double value = context.signalValues().get(configured.getKey());
            if (value == null) {
                continue;
            }
            double bounded = Math.max(0, Math.min(1, value));
            weighted += bounded * configured.getValue();
            appliedWeight += Math.abs(configured.getValue());
            signals.add(new ClauseSignal(configured.getKey(), bounded,
                context.metadata().getOrDefault(configured.getKey() + "Reason", "POLICY_SIGNAL")
                    .toString(),
                Map.of("weight", configured.getValue())));
        }
        double score = appliedWeight == 0 ? 0 : weighted / appliedWeight;
        double extract = configuration.requiredNumber("decisionThresholds.extract");
        double review = configuration.requiredNumber("decisionThresholds.manualReview");
        String action = score >= extract ? "EXTRACT"
            : score >= review ? "MANUAL_REVIEW" : "SKIP";
        return new ClauseSignalResult(score, action, List.copyOf(signals),
            "MANUAL_REVIEW".equals(action));
    }
}
