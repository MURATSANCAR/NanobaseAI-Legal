package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface KnowledgeAiGateway {
    KnowledgeResponse extract(KnowledgeRequest request);

    record KnowledgeRequest(
        UUID jobId,
        UUID organizationId,
        String logicalModel,
        String modelProfile,
        List<String> promptComponents,
        JsonNode outputSchema,
        List<Map<String, Object>> ontologyConcepts,
        List<Map<String, Object>> evidenceFragments,
        int maximumOutputTokens,
        UUID correlationId
    ) {
    }

    record KnowledgeResponse(UUID modelRunId, JsonNode output, long latencyMs,
                             int inputTokens, int outputTokens) {
    }
}
