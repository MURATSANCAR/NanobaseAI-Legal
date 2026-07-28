package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ComplianceAiGateway {
    SemanticResponse evaluate(SemanticRequest request);

    record SemanticRequest(
        UUID organizationId,
        String logicalModel,
        String modelProfile,
        List<String> promptComponents,
        JsonNode outputSchema,
        Map<String, Object> requirement,
        List<Map<String, Object>> ontologyConcepts,
        List<Map<String, Object>> evidence,
        List<String> allowedDecisionConcepts,
        int maximumOutputTokens,
        UUID correlationId
    ) {
    }

    record SemanticResponse(UUID modelRunId, JsonNode output, long latencyMs,
                            int inputTokens, int outputTokens) {
    }
}
