package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceFactor;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PolicyComplianceConfidenceEngine {
    public ConfidenceResult evaluate(ConfidenceContext context) {
        JsonNode weights = context.policy().path("weights");
        List<ConfidenceFactor> factors = new ArrayList<>();
        double score = 0;
        double totalWeight = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = weights.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            double input = clamp(context.signals().getOrDefault(field.getKey(), 0d));
            double weight = field.getValue().asDouble();
            double effect = input * weight;
            factors.add(new ConfidenceFactor(field.getKey(), input, weight, effect));
            score += effect;
            totalWeight += Math.abs(weight);
        }
        score = totalWeight == 0 ? 0 : score / totalWeight;
        JsonNode penalties = context.policy().path("penalties");
        if (context.missingEvidence()) {
            double effect = -penalties.path("missingEvidence").asDouble(0);
            score += effect;
            factors.add(new ConfidenceFactor("MISSING_EVIDENCE", 1, 1, effect));
        }
        if (context.contradiction()) {
            double effect = -penalties.path("contradiction").asDouble(0);
            score += effect;
            factors.add(new ConfidenceFactor("CONTRADICTION", 1, 1, effect));
        }
        score = clamp(score);
        double reviewBelow = context.policy().path("reviewBelow").asDouble(1);
        return new ConfidenceResult(score, level(score, context.policy().path("levels")),
            score < reviewBelow || context.missingEvidence() || context.contradiction(),
            List.copyOf(factors));
    }

    private String level(double score, JsonNode levels) {
        if (levels.isArray()) {
            for (JsonNode level : levels) {
                if (score >= level.path("minimum").asDouble()) {
                    return level.path("concept").asText(level.path("code").asText("UNSPECIFIED"));
                }
            }
        }
        return "UNSPECIFIED";
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
