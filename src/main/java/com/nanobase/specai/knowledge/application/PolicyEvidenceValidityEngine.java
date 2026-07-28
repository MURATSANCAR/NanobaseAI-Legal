package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Policy-driven validity scoring. The returned selector is mapped to an ontology
 * concept by configuration, so adding or renaming validity labels needs no Java change.
 */
@Service
public class PolicyEvidenceValidityEngine {
    public ValidityResult evaluate(ValidityInput input, JsonNode policy) {
        boolean notExpired = input.validUntil() == null
            || input.validUntil().isAfter(input.assessedAt());
        Map<String, Double> values = Map.of(
            "notExpired", notExpired ? 1d : 0d,
            "parserQuality", clamp(input.parserQuality()),
            "ocrQuality", clamp(input.ocrQuality()),
            "verified", input.verified() ? 1d : 0d,
            "authority", clamp(input.authority())
        );
        JsonNode weights = policy.path("factors");
        double weighted = 0;
        double totalWeight = 0;
        List<ValidityFactor> factors = new ArrayList<>();
        for (Map.Entry<String, Double> factor : values.entrySet()) {
            double weight = Math.max(0, weights.path(factor.getKey()).asDouble(0));
            double effect = factor.getValue() * weight;
            weighted += effect;
            totalWeight += weight;
            factors.add(new ValidityFactor(factor.getKey(), factor.getValue(), weight,
                effect));
        }
        double score = totalWeight == 0 ? 0 : clamp(weighted / totalWeight);
        double minimum = policy.path("minimumUsableScore").asDouble(1);
        String selector = !notExpired
            ? policy.path("selectors").path("expired").asText("expired")
            : score >= minimum
                ? policy.path("selectors").path("usable").asText("usable")
                : policy.path("selectors").path("indeterminate").asText("indeterminate");
        return new ValidityResult(selector, score, List.copyOf(factors));
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record ValidityInput(Instant assessedAt, Instant validUntil,
                                double parserQuality, double ocrQuality,
                                boolean verified, double authority) {
    }

    public record ValidityFactor(String factor, double value, double weight,
                                 double effect) {
    }

    public record ValidityResult(String statusSelector, double score,
                                 List<ValidityFactor> factors) {
    }
}
