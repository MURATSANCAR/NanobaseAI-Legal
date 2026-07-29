package com.nanobase.specai.compliance.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceAiGateway;
import com.nanobase.specai.compliance.application.SemanticEvaluationException;
import com.nanobase.specai.compliance.application.SemanticEvaluationFailureCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Compliance semantic evaluation HTTP client with dedicated timeouts.
 *
 * <p>Must not inherit {@code DOCUMENT_PROVIDER_READ_TIMEOUT} (PT180S). Host llama-server is
 * single-slot and often needs the same budget as knowledge extraction (PT600S).
 */
@Component
public class HttpComplianceAiGateway implements ComplianceAiGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpComplianceAiGateway.class);

    private final RestClient client;
    private final ObjectMapper mapper;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final int retryAttempts;
    private final Duration retryBackoff;
    private final String endpointAlias;

    public HttpComplianceAiGateway(
        ObjectMapper mapper,
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8092}") String baseUrl,
        @Value("${specai.ai-orchestrator.connect-timeout:PT5S}") Duration connectTimeout,
        @Value("${specai.ai-orchestrator.compliance-read-timeout:PT600S}") Duration readTimeout,
        @Value("${specai.ai-orchestrator.compliance-retry-attempts:1}") int retryAttempts,
        @Value("${specai.ai-orchestrator.compliance-retry-backoff:PT2S}") Duration retryBackoff
    ) {
        this.mapper = mapper;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.retryAttempts = Math.max(0, retryAttempts);
        this.retryBackoff = retryBackoff == null ? Duration.ofSeconds(2) : retryBackoff;
        this.endpointAlias = "ai-orchestrator/compliance-evaluations";
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
            "compliance_llm_timeout_policy connectTimeout={} readTimeout={} "
                + "retryAttempts={} retryBackoff={} endpoint={}",
            connectTimeout, readTimeout, this.retryAttempts, this.retryBackoff, baseUrl);
    }

    @Override
    public SemanticResponse evaluate(SemanticRequest request) {
        int attempt = 0;
        SemanticEvaluationException lastFailure = null;
        long totalStarted = System.nanoTime();
        while (attempt <= retryAttempts) {
            long attemptStarted = System.nanoTime();
            try {
                GatewayResponse response = client.post()
                    .uri("/v1/compliance-evaluations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", request.correlationId().toString())
                    .body(new GatewayRequest(request.logicalModel(), request.modelProfile(),
                        request.promptComponents(), request.outputSchema(), request.requirement(),
                        request.ontologyConcepts(), request.evidence(),
                        request.allowedDecisionConcepts(), request.maximumOutputTokens()))
                    .retrieve()
                    .body(GatewayResponse.class);
                long totalMs = elapsedMs(totalStarted);
                if (response == null || response.output() == null) {
                    throw new SemanticEvaluationException(
                        SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                        "AI orchestrator returned an empty compliance response", attempt);
                }
                trace(request, attempt, totalMs, elapsedMs(attemptStarted), "COMPLETED", null,
                    response.inputTokens(), response.outputTokens(), response.latencyMs());
                return new SemanticResponse(
                    response.modelRunId() == null ? UUID.randomUUID() : response.modelRunId(),
                    response.output(), response.latencyMs(), response.inputTokens(),
                    response.outputTokens());
            } catch (SemanticEvaluationException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                SemanticEvaluationFailureCode code = classify(failure);
                lastFailure = new SemanticEvaluationException(code,
                    truncate(failure.getMessage()), failure, attempt);
                trace(request, attempt, elapsedMs(totalStarted), elapsedMs(attemptStarted),
                    "ERROR", code.name(), 0, 0, null);
                if (!retryable(code) || attempt >= retryAttempts) {
                    throw lastFailure;
                }
                sleepBackoff(attempt);
                attempt++;
            }
        }
        throw lastFailure == null
            ? new SemanticEvaluationException(SemanticEvaluationFailureCode.EVALUATION_ERROR,
                "Compliance semantic evaluation failed", 0)
            : lastFailure;
    }

    static SemanticEvaluationFailureCode classify(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = (failure.getMessage() == null ? "" : failure.getMessage())
            + " " + root.getClass().getSimpleName() + " "
            + (root.getMessage() == null ? "" : root.getMessage());
        String lower = message.toLowerCase();
        if (failure instanceof ResourceAccessException
            || lower.contains("timed out")
            || lower.contains("timeout")
            || lower.contains("httptimeoutexception")) {
            return SemanticEvaluationFailureCode.LLM_TIMEOUT;
        }
        if (failure instanceof HttpServerErrorException server
            && (server.getStatusCode().value() == 502
                || server.getStatusCode().value() == 503
                || server.getStatusCode().value() == 504)) {
            return SemanticEvaluationFailureCode.LLM_UNAVAILABLE;
        }
        if (failure instanceof RestClientResponseException response
            && response.getStatusCode().value() == 422
            && lower.contains("context")) {
            return SemanticEvaluationFailureCode.CONTEXT_OVERFLOW;
        }
        if (failure instanceof RestClientResponseException response
            && response.getStatusCode().is4xxClientError()) {
            return SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE;
        }
        if (lower.contains("connection reset")
            || lower.contains("connection refused")
            || lower.contains("unavailable")) {
            return SemanticEvaluationFailureCode.LLM_UNAVAILABLE;
        }
        return SemanticEvaluationFailureCode.EVALUATION_ERROR;
    }

    static boolean retryable(SemanticEvaluationFailureCode code) {
        return code == SemanticEvaluationFailureCode.LLM_TIMEOUT
            || code == SemanticEvaluationFailureCode.LLM_UNAVAILABLE;
    }

    private void sleepBackoff(int attempt) {
        long base = Math.max(100L, retryBackoff.toMillis());
        long delay = base * (1L << Math.min(attempt, 4));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, base / 2));
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SemanticEvaluationException(SemanticEvaluationFailureCode.EVALUATION_ERROR,
                "Compliance semantic evaluation interrupted during retry backoff", attempt);
        }
    }

    private void trace(SemanticRequest request, int attempt, long totalMs, long generationMs,
                       String result, String failureCode, int inputTokens, int outputTokens,
                       Long modelLatencyMs) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("requirementId", request.requirement() == null
            ? null : request.requirement().get("id"));
        trace.put("candidateCount", request.evidence() == null ? 0 : request.evidence().size());
        trace.put("promptTokenEstimate", estimateTokens(request));
        trace.put("queueWaitMs", null);
        trace.put("connectMs", connectTimeout.toMillis());
        trace.put("timeToFirstTokenMs", null);
        trace.put("generationMs", generationMs);
        trace.put("totalDurationMs", totalMs);
        trace.put("inputTokens", inputTokens);
        trace.put("outputTokens", outputTokens);
        trace.put("modelLatencyMs", modelLatencyMs);
        trace.put("retryAttempt", attempt);
        trace.put("llmEndpoint", endpointAlias);
        trace.put("llmModelAlias", request.modelProfile());
        trace.put("streaming", false);
        trace.put("readTimeoutMs", readTimeout.toMillis());
        trace.put("result", result);
        trace.put("failureCode", failureCode);
        try {
            log.info("semantic_evaluation {}", mapper.writeValueAsString(trace));
        } catch (Exception ignored) {
            log.info("semantic_evaluation {}", trace);
        }
    }

    private static int estimateTokens(SemanticRequest request) {
        int chars = 0;
        if (request.promptComponents() != null) {
            for (String component : request.promptComponents()) {
                chars += component == null ? 0 : component.length();
            }
        }
        chars += stringify(request.requirement()).length();
        chars += stringify(request.ontologyConcepts()).length();
        chars += stringify(request.evidence()).length();
        chars += stringify(request.outputSchema()).length();
        return Math.max(1, chars / 4);
    }

    private static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Compliance semantic evaluation failed";
        }
        return message.substring(0, Math.min(500, message.length()));
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
