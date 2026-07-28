package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.ExposureFactor;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.ScoredDimension;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WeightedExposureMethodProvider implements RiskExposureMethodProvider {
    @Override
    public boolean supports(String method) {
        return "WEIGHTED_SUM".equals(method) || "HYBRID".equals(method);
    }

    @Override
    public ScoredDimension score(String dimension, RiskExposureContext context,
                                 JsonNode configuration) {
        JsonNode weights = configuration.path(dimension + "Weights");
        List<ExposureFactor> factors = new ArrayList<>();
        double weighted = 0;
        double totalWeight = 0;
        var fields = weights.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            double input = ConfigurableRiskSignalEngine.clamp(
                context.signals().getOrDefault(field.getKey(), 0d));
            double weight = Math.max(0, field.getValue().asDouble());
            double effect = input * weight;
            factors.add(new ExposureFactor(field.getKey(), input, weight, effect));
            weighted += effect;
            totalWeight += weight;
        }
        return new ScoredDimension(totalWeight == 0 ? 0
            : ConfigurableRiskSignalEngine.clamp(weighted / totalWeight), List.copyOf(factors));
    }

    @Override
    public double combine(ScoredDimension probability, ScoredDimension impact,
                          JsonNode configuration) {
        JsonNode weights = configuration.path("exposureWeights");
        double probabilityWeight = Math.max(0, weights.path("probability").asDouble(.5));
        double impactWeight = Math.max(0, weights.path("impact").asDouble(.5));
        double divisor = probabilityWeight + impactWeight;
        return divisor == 0 ? 0 : ConfigurableRiskSignalEngine.clamp(
            (probability.score() * probabilityWeight + impact.score() * impactWeight) / divisor);
    }
}
