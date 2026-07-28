package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityContext;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityFactor;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableAmbiguityAnalysisEngine implements AmbiguityAnalysisEngine {
    @Override
    public AmbiguityResult analyze(AmbiguityContext context, VersionedPolicy policy) {
        JsonNode configuration = policy.configuration();
        List<AmbiguityFactor> factors = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        double score = 0;
        double totalWeight = 0;
        var fields = configuration.path("features").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            double value = ConfigurableRiskSignalEngine.clamp(
                context.features().getOrDefault(field.getKey(), 0d));
            double weight = Math.max(0, field.getValue().asDouble());
            score += value * weight;
            totalWeight += weight;
            factors.add(new AmbiguityFactor(field.getKey(), value, weight, value * weight));
            if (value > 0) {
                missingFields.add(field.getKey());
            }
        }
        double confidence = totalWeight == 0 ? 0
            : ConfigurableRiskSignalEngine.clamp(score / totalWeight);
        JsonNode selected = selectConcept(configuration, context);
        UUID conceptId = selected.hasNonNull("conceptId")
            ? UUID.fromString(selected.path("conceptId").asText()) : null;
        return new AmbiguityResult(
            confidence >= configuration.path("findingThreshold").asDouble(1),
            conceptId, selected.path("conceptCode").asText(null), confidence,
            List.copyOf(factors), List.copyOf(missingFields));
    }

    private JsonNode selectConcept(JsonNode configuration, AmbiguityContext context) {
        for (JsonNode mapping : configuration.path("conceptMappings")) {
            String feature = mapping.path("feature").asText();
            if (context.features().getOrDefault(feature, 0d)
                >= mapping.path("minimum").asDouble(0)) {
                return mapping;
            }
        }
        return configuration.path("fallbackConcept");
    }
}
