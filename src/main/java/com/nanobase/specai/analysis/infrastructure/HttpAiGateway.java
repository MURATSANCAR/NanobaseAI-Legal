package com.nanobase.specai.analysis.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AiGateway;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionRequest;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class HttpAiGateway implements AiGateway {
    private final RestClient client;

    public HttpAiGateway(RestClient.Builder builder,
                         @Value("${specai.ai-orchestrator.base-url:http://localhost:8090}")
                         String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public ExtractionResponse extract(ExtractionRequest request) {
        try {
            GatewayResponse response = client.post()
                .uri("/v1/extractions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-ID", request.correlationId().toString())
                .body(new GatewayRequest(request.logicalModel(), request.modelProfile(),
                    request.promptComponents(), request.outputSchema(), request.context(),
                    request.maximumOutputTokens()))
                .retrieve()
                .body(GatewayResponse.class);
            if (response == null || response.output() == null) {
                throw new IllegalStateException("AI orchestrator returned an empty response");
            }
            return new ExtractionResponse(
                response.modelRunId() == null ? UUID.randomUUID() : response.modelRunId(),
                response.output(), response.latencyMs(), response.inputTokens(),
                response.outputTokens(), Instant.now());
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException(
                "AI orchestrator is busy or unavailable; try again later", exception);
        }
    }

    private record GatewayRequest(String model, String profile, Object promptComponents,
                                  JsonNode outputSchema, Object context,
                                  int maximumOutputTokens) {
    }

    private record GatewayResponse(UUID modelRunId, JsonNode output, long latencyMs,
                                   int inputTokens, int outputTokens) {
    }
}
