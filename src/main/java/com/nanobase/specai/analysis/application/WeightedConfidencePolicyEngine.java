package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceFactor;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceResult;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WeightedConfidencePolicyEngine implements ConfidencePolicyEngine {
    @Override
    public ConfidenceResult calculate(ConfidenceContext context, PolicyDocument policy) {
        PolicyConfiguration configuration = new PolicyConfiguration(policy.configuration());
        Map<String, Double> weights = configuration.requiredWeights("weights");
        List<ConfidenceFactor> factors = new ArrayList<>();
        double score = 0;
        double appliedWeight = 0;
        for (Map.Entry<String, Double> configured : weights.entrySet()) {
            Double input = context.factors().get(configured.getKey());
            if (input == null) {
                continue;
            }
            double bounded = Math.max(0, Math.min(1, input));
            double effect = bounded * configured.getValue();
            score += effect;
            appliedWeight += Math.abs(configured.getValue());
            factors.add(new ConfidenceFactor(configured.getKey(), effect, bounded,
                configured.getValue()));
        }
        score = appliedWeight == 0 ? 0 : Math.max(0, Math.min(1, score / appliedWeight));
        String level = level(policy.configuration().path("levels"), score);
        boolean review = score < configuration.requiredNumber("reviewBelow");
        return new ConfidenceResult(score, level, List.copyOf(factors), review);
    }

    private String level(JsonNode levels, double score) {
        if (!levels.isArray() || levels.isEmpty()) {
            throw new IllegalStateException("Confidence levels are not configured");
        }
        for (JsonNode level : levels) {
            if (level.path("code").isTextual() && level.path("minimum").isNumber()
                && score >= level.path("minimum").doubleValue()) {
                return level.path("code").textValue();
            }
        }
        throw new IllegalStateException("Confidence level configuration has a gap");
    }
}
