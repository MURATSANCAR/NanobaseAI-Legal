package com.nanobase.specai.knowledge.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.knowledge.application.KnowledgeAiGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpKnowledgeAiGateway implements KnowledgeAiGateway {
    private final RestClient client;

    public HttpKnowledgeAiGateway(
        RestClient.Builder builder,
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8092}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public KnowledgeResponse extract(KnowledgeRequest request) {
        GatewayResponse response = client.post()
            .uri("/v1/knowledge-extractions")
            .header("X-Correlation-ID", request.correlationId().toString())
            .body(new GatewayRequest(request.logicalModel(), request.modelProfile(),
                request.promptComponents(), request.outputSchema(),
                request.ontologyConcepts(), request.evidenceFragments(),
                request.maximumOutputTokens()))
            .retrieve()
            .body(GatewayResponse.class);
        if (response == null || response.output() == null) {
            throw new IllegalStateException("AI orchestrator returned an empty knowledge response");
        }
        return new KnowledgeResponse(
            response.modelRunId() == null ? UUID.randomUUID() : response.modelRunId(),
            response.output(), response.latencyMs(), response.inputTokens(),
            response.outputTokens());
    }

    private record GatewayRequest(
        String model,
        String profile,
        List<String> promptComponents,
        JsonNode outputSchema,
        List<Map<String, Object>> ontologyConcepts,
        List<Map<String, Object>> evidenceFragments,
        int maximumOutputTokens
    ) {
    }

    private record GatewayResponse(UUID modelRunId, JsonNode output, long latencyMs,
                                   int inputTokens, int outputTokens) {
    }
}
