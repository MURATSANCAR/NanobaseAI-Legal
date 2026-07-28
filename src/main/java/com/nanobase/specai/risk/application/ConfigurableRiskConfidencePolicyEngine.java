package com.nanobase.specai.risk.application;

import com.nanobase.specai.risk.application.RiskModels.RiskConfidenceFactor;
import com.nanobase.specai.risk.application.RiskModels.RiskConfidenceResult;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableRiskConfidencePolicyEngine
    implements RiskConfidencePolicyEngine {

    @Override
    public RiskConfidenceResult calculate(Map<String, Double> inputs,
                                          VersionedPolicy policy) {
        List<RiskConfidenceFactor> factors = new ArrayList<>();
        double weighted = 0;
        double totalWeight = 0;
        var fields = policy.configuration().path("weights").fields();
        while (fields.hasNext()) {
            var field = fields.next();
            double weight = Math.max(0, field.getValue().asDouble());
            double input = ConfigurableRiskSignalEngine.clamp(
                inputs.getOrDefault(field.getKey(), 0d));
            double effect = input * weight;
            factors.add(new RiskConfidenceFactor(field.getKey(), input, weight, effect));
            weighted += effect;
            totalWeight += weight;
        }
        double score = totalWeight == 0 ? 0
            : ConfigurableRiskSignalEngine.clamp(weighted / totalWeight);
        return new RiskConfidenceResult(score, List.copyOf(factors),
            score < policy.configuration().path("reviewBelow").asDouble(1));
    }
}
