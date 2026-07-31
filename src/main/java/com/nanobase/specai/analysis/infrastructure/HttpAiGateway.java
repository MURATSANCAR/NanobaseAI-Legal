package com.nanobase.specai.analysis.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.analysis.application.AiGateway;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionRequest;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Requirement extraction HTTP client with dedicated timeouts.
 * Must not inherit an unbounded RestClient.Builder read timeout.
 */
@Component
public class HttpAiGateway implements AiGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpAiGateway.class);

    private final RestClient client;

    public HttpAiGateway(
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8090}") String baseUrl,
        @Value("${specai.ai-orchestrator.connect-timeout:PT5S}") Duration connectTimeout,
        @Value("${specai.ai-orchestrator.extraction-read-timeout:PT300S}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(connectTimeout)
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
        log.info(
            "extraction_llm_timeout_policy connectTimeout={} readTimeout={} endpoint={}",
            connectTimeout, readTimeout, baseUrl);
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
            String message = String.valueOf(exception.getMessage()).toLowerCase();
            if (message.contains("timed out") || message.contains("timeout")) {
                throw new IllegalStateException(
                    "AI orchestrator extraction timed out", exception);
            }
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
