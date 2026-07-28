package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Evaluates source trust from versioned policy/profile JSON. Source types and issuers
 * remain catalog/entity data; this evaluator does not encode a trust ranking.
 */
@Service
public class PolicySourceAuthorityEvaluator {
    public double evaluate(String sourceTypeCode, UUID issuerEntityId,
                           JsonNode policy, JsonNode profile) {
        double score = policy.path("defaultScore").asDouble(0);
        if (sourceTypeCode != null) {
            score = policy.path("sourceScores").path(sourceTypeCode).asDouble(score);
        }
        if (issuerEntityId != null) {
            score = policy.path("issuerOverrides")
                .path(issuerEntityId.toString()).asDouble(score);
        }
        if (profile != null && !profile.isMissingNode() && !profile.isNull()) {
            score = profile.path("score").asDouble(score);
        }
        return clamp(score);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
