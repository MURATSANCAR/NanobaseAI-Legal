package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureResult;
import com.nanobase.specai.risk.application.RiskModels.ScoredDimension;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableRiskExposurePolicyEngine implements RiskExposurePolicyEngine {
    private final List<RiskExposureMethodProvider> providers;

    public ConfigurableRiskExposurePolicyEngine(List<RiskExposureMethodProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public RiskExposureResult calculate(RiskExposureContext context, VersionedPolicy policy) {
        JsonNode configuration = policy.configuration();
        String method = configuration.path("exposureMethod").asText();
        RiskExposureMethodProvider provider = providers.stream()
            .filter(candidate -> candidate.supports(method))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No exposure provider registered for policy method " + method));
        ScoredDimension probability = provider.score("probability", context, configuration);
        ScoredDimension impact = provider.score("impact", context, configuration);
        double exposure = provider.combine(probability, impact, configuration);
        JsonNode level = severityLevel(configuration.path("severityLevels"), exposure);
        UUID conceptId = level.hasNonNull("conceptId")
            ? UUID.fromString(level.path("conceptId").asText()) : null;
        return new RiskExposureResult(probability, impact, exposure, conceptId,
            level.path("conceptCode").asText(null), method);
    }

    private JsonNode severityLevel(JsonNode levels, double exposure) {
        return java.util.stream.StreamSupport.stream(levels.spliterator(), false)
            .filter(level -> exposure >= level.path("minimum").asDouble())
            .max(Comparator.comparingDouble(level -> level.path("minimum").asDouble()))
            .orElseGet(() -> levels.isArray() && !levels.isEmpty()
                ? levels.get(0) : com.fasterxml.jackson.databind.node.MissingNode.getInstance());
    }
}
