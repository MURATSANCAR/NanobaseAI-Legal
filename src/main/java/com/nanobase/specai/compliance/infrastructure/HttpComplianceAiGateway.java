package com.nanobase.specai.compliance.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.compliance.application.ComplianceAiGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpComplianceAiGateway implements ComplianceAiGateway {
    private final RestClient client;

    public HttpComplianceAiGateway(
        RestClient.Builder builder,
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8092}") String baseUrl
    ) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public SemanticResponse evaluate(SemanticRequest request) {
        GatewayResponse response = client.post()
            .uri("/v1/compliance-evaluations")
            .header("X-Correlation-ID", request.correlationId().toString())
            .body(new GatewayRequest(request.logicalModel(), request.modelProfile(),
                request.promptComponents(), request.outputSchema(), request.requirement(),
                request.ontologyConcepts(), request.evidence(),
                request.allowedDecisionConcepts(), request.maximumOutputTokens()))
            .retrieve()
            .body(GatewayResponse.class);
        if (response == null || response.output() == null) {
            throw new IllegalStateException("AI orchestrator returned an empty compliance response");
        }
        return new SemanticResponse(
            response.modelRunId() == null ? UUID.randomUUID() : response.modelRunId(),
            response.output(), response.latencyMs(), response.inputTokens(),
            response.outputTokens());
    }

    private record GatewayRequest(
        String model,
        String profile,
        List<String> promptComponents,
        JsonNode outputSchema,
        Map<String, Object> requirement,
        List<Map<String, Object>> ontologyConcepts,
        List<Map<String, Object>> evidence,
        List<String> allowedDecisionConcepts,
        int maximumOutputTokens
    ) {
    }

    private record GatewayResponse(UUID modelRunId, JsonNode output, long latencyMs,
                                   int inputTokens, int outputTokens) {
    }
}
