package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.CandidateRiskConcept;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalContext;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalResult;
import com.nanobase.specai.risk.application.RiskModels.SignalFactor;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableRiskSignalEngine implements RiskSignalEngine {
    @Override
    public RiskSignalResult evaluate(RiskSignalContext context, VersionedPolicy policy) {
        JsonNode configuration = policy.configuration();
        JsonNode weights = configuration.path("weights");
        List<SignalFactor> factors = new ArrayList<>();
        double weighted = 0;
        double totalWeight = 0;
        var fields = weights.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            double weight = Math.max(0, field.getValue().asDouble());
            double value = clamp(context.signals().getOrDefault(field.getKey(), 0d));
            double effect = value * weight;
            weighted += effect;
            totalWeight += weight;
            factors.add(new SignalFactor(field.getKey(), value, weight, effect));
        }
        double score = totalWeight == 0 ? 0 : clamp(weighted / totalWeight);
        List<CandidateRiskConcept> concepts = mappedConcepts(configuration, context, score);
        double threshold = clamp(configuration.path("detailedAnalysisThreshold").asDouble(1));
        return new RiskSignalResult(score, concepts, List.copyOf(factors),
            score >= threshold && !concepts.isEmpty());
    }

    private List<CandidateRiskConcept> mappedConcepts(JsonNode configuration,
                                                       RiskSignalContext context,
                                                       double score) {
        List<CandidateRiskConcept> result = new ArrayList<>();
        for (JsonNode mapping : configuration.path("conceptMappings")) {
            String signal = mapping.path("signal").asText();
            double minimum = mapping.path("minimum").asDouble(0);
            double value = context.signals().getOrDefault(signal, 0d);
            if (value < minimum || !mapping.hasNonNull("conceptId")) {
                continue;
            }
            result.add(new CandidateRiskConcept(
                UUID.fromString(mapping.path("conceptId").asText()),
                mapping.path("conceptCode").asText(),
                clamp(value * mapping.path("scoreMultiplier").asDouble(1)),
                textList(mapping.path("reasonCodes"))));
        }
        JsonNode fallback = configuration.path("fallbackConcept");
        if (result.isEmpty() && fallback.hasNonNull("conceptId")) {
            result.add(new CandidateRiskConcept(
                UUID.fromString(fallback.path("conceptId").asText()),
                fallback.path("conceptCode").asText(),
                score, textList(fallback.path("reasonCodes"))));
        }
        result.sort(Comparator.comparingDouble(CandidateRiskConcept::score).reversed());
        return List.copyOf(result);
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }

    static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
