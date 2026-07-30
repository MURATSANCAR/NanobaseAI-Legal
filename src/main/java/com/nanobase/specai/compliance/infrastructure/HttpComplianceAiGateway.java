package com.nanobase.specai.compliance.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceAiGateway;
import com.nanobase.specai.compliance.application.SemanticEvaluationException;
import com.nanobase.specai.compliance.application.SemanticEvaluationFailureCode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
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
 * <p>Must not inherit {@code DOCUMENT_PROVIDER_READ_TIMEOUT} (PT180S). Backend read timeout
 * must exceed orchestrator queueWait + generation + network margin.
 */
@Component
public class HttpComplianceAiGateway implements ComplianceAiGateway {
    private static final Logger log = LoggerFactory.getLogger(HttpComplianceAiGateway.class);

    private final RestClient client;
    private final ObjectMapper mapper;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration globalDeadline;
    private final int retryAttempts;
    private final Duration retryBackoff;
    private final String endpointAlias;

    public HttpComplianceAiGateway(
        ObjectMapper mapper,
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8092}") String baseUrl,
        @Value("${specai.ai-orchestrator.connect-timeout:PT5S}") Duration connectTimeout,
        @Value("${specai.ai-orchestrator.compliance-read-timeout:PT780S}") Duration readTimeout,
        @Value("${specai.ai-orchestrator.compliance-global-deadline:PT820S}")
            Duration globalDeadline,
        @Value("${specai.ai-orchestrator.compliance-retry-attempts:1}") int retryAttempts,
        @Value("${specai.ai-orchestrator.compliance-retry-backoff:PT2S}") Duration retryBackoff
    ) {
        this.mapper = mapper;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.globalDeadline = globalDeadline == null ? readTimeout.plusSeconds(40) : globalDeadline;
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
                + "globalDeadline={} retryAttempts={} retryBackoff={} endpoint={}",
            connectTimeout, readTimeout, this.globalDeadline, this.retryAttempts,
            this.retryBackoff, baseUrl);
    }

    @Override
    public SemanticResponse evaluate(SemanticRequest request) {
        Instant deadline = Instant.now().plus(globalDeadline);
        String idempotencyKey = idempotencyKey(request);
        int attempt = 0;
        SemanticEvaluationException lastFailure = null;
        long totalStarted = System.nanoTime();
        while (attempt <= retryAttempts) {
            if (Instant.now().isAfter(deadline)) {
                throw new SemanticEvaluationException(
                    SemanticEvaluationFailureCode.LLM_GENERATION_TIMEOUT,
                    "Compliance evaluation exceeded global request deadline", attempt);
            }
            long attemptStarted = System.nanoTime();
            try {
                GatewayResponse response = client.post()
                    .uri("/v1/compliance-evaluations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", request.correlationId().toString())
                    .header("Idempotency-Key", idempotencyKey)
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
                    response.inputTokens(), response.outputTokens(), response.latencyMs(),
                    false, false);
                return new SemanticResponse(
                    response.modelRunId() == null ? UUID.randomUUID() : response.modelRunId(),
                    response.output(), response.latencyMs(), response.inputTokens(),
                    response.outputTokens());
            } catch (SemanticEvaluationException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                SemanticEvaluationFailureCode code = classify(failure);
                boolean cancellationCompleted = responseSignalsCancellation(failure);
                lastFailure = new SemanticEvaluationException(code,
                    truncate(failure.getMessage()), failure, attempt);
                trace(request, attempt, elapsedMs(totalStarted), elapsedMs(attemptStarted),
                    "ERROR", code.name(), 0, 0, null,
                    code == SemanticEvaluationFailureCode.LLM_CANCELLED
                        || code == SemanticEvaluationFailureCode.LLM_GENERATION_TIMEOUT,
                    cancellationCompleted);
                if (!retryable(code, cancellationCompleted, Instant.now().isBefore(deadline))
                    || attempt >= retryAttempts) {
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
        String body = responseBody(failure);
        String message = (failure.getMessage() == null ? "" : failure.getMessage())
            + " " + root.getClass().getSimpleName() + " "
            + (root.getMessage() == null ? "" : root.getMessage())
            + " " + body;
        String lower = message.toLowerCase();
        if (lower.contains("llm_queue_timeout")) {
            return SemanticEvaluationFailureCode.LLM_QUEUE_TIMEOUT;
        }
        if (lower.contains("llm_connect_timeout")) {
            return SemanticEvaluationFailureCode.LLM_CONNECT_TIMEOUT;
        }
        if (lower.contains("llm_generation_timeout")) {
            return SemanticEvaluationFailureCode.LLM_GENERATION_TIMEOUT;
        }
        if (lower.contains("llm_cancelled") || lower.contains("\"code\":\"llm_cancelled\"")) {
            return SemanticEvaluationFailureCode.LLM_CANCELLED;
        }
        if (lower.contains("llm_overloaded")) {
            return SemanticEvaluationFailureCode.LLM_OVERLOADED;
        }
        if (lower.contains("llm_circuit_open") || lower.contains("circuit")) {
            return SemanticEvaluationFailureCode.LLM_CIRCUIT_OPEN;
        }
        if (lower.contains("context_overflow") || lower.contains("context overflow")
            || lower.contains("llm_context_overflow")) {
            return SemanticEvaluationFailureCode.LLM_CONTEXT_OVERFLOW;
        }
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
            return SemanticEvaluationFailureCode.LLM_CONTEXT_OVERFLOW;
        }
        if (failure instanceof RestClientResponseException response
            && response.getStatusCode().is4xxClientError()) {
            return SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE;
        }
        if (lower.contains("connection reset")
            || lower.contains("connection refused")
            || lower.contains("unavailable")
            || lower.contains("model busy")) {
            return SemanticEvaluationFailureCode.LLM_UNAVAILABLE;
        }
        return SemanticEvaluationFailureCode.EVALUATION_ERROR;
    }

    static boolean retryable(SemanticEvaluationFailureCode code) {
        return retryable(code, false, true);
    }

    static boolean retryable(SemanticEvaluationFailureCode code, boolean cancellationCompleted,
                             boolean withinGlobalDeadline) {
        if (!withinGlobalDeadline) {
            return false;
        }
        return switch (code) {
            case LLM_UNAVAILABLE, LLM_CONNECT_TIMEOUT -> true;
            // Retry transient overload / queue wait only when the prior call released its slot.
            case LLM_OVERLOADED, LLM_TIMEOUT, LLM_QUEUE_TIMEOUT -> cancellationCompleted;
            // Never blind-retry a full generation timeout or cancelled call.
            case LLM_GENERATION_TIMEOUT, LLM_CANCELLED, LLM_INVALID_RESPONSE,
                 LLM_CONTEXT_OVERFLOW, LLM_CIRCUIT_OPEN, CONTEXT_OVERFLOW, EVALUATION_ERROR ->
                false;
        };
    }

    private static boolean responseSignalsCancellation(Throwable failure) {
        String body = responseBody(failure).toLowerCase();
        return body.contains("cancellationcompleted\":true")
            || body.contains("llm_cancelled")
            || body.contains("llm_generation_timeout");
    }

    private static String responseBody(Throwable failure) {
        if (failure instanceof RestClientResponseException response) {
            String body = response.getResponseBodyAsString();
            return body == null ? "" : body;
        }
        return "";
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
                       Long modelLatencyMs, boolean cancellationRequested,
                       boolean cancellationCompleted) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("requirementId", request.requirement() == null
            ? null : request.requirement().get("id"));
        trace.put("taskType", "MULTI_EVIDENCE_COMPLIANCE_FINAL");
        trace.put("requestedProfile", request.modelProfile());
        trace.put("resolvedProfile", request.modelProfile());
        trace.put("deploymentAlias", deploymentAlias(request.modelProfile()));
        trace.put("candidateCount", request.evidence() == null ? 0 : request.evidence().size());
        trace.put("promptTokenEstimate", estimateTokens(request));
        trace.put("queueWaitMs", null);
        trace.put("slotAcquireMs", null);
        trace.put("connectMs", connectTimeout.toMillis());
        trace.put("timeToFirstTokenMs", null);
        trace.put("generationMs", generationMs);
        trace.put("totalDurationMs", totalMs);
        trace.put("inputTokens", inputTokens);
        trace.put("outputTokens", outputTokens);
        trace.put("modelLatencyMs", modelLatencyMs);
        trace.put("retryAttempt", attempt);
        trace.put("cancellationRequested", cancellationRequested);
        trace.put("cancellationCompleted", cancellationCompleted);
        trace.put("llmEndpoint", endpointAlias);
        // External alias only — never emit product model names.
        trace.put("llmModelAlias", deploymentAlias(request.modelProfile()));
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

    static String deploymentAlias(String profile) {
        if (profile == null || profile.isBlank()) {
            return "nanobase-balanced";
        }
        return switch (profile.trim().toUpperCase()) {
            case "FAST" -> "nanobase-fast";
            case "BALANCED" -> "nanobase-balanced";
            default -> "nanobase-" + profile.trim().toLowerCase();
        };
    }

    private static String idempotencyKey(SemanticRequest request) {
        Object requirementId = request.requirement() == null
            ? null : request.requirement().get("id");
        Object evaluationVersion = request.requirement() == null
            ? null : request.requirement().get("evaluationVersion");
        // complianceRunId is carried as correlationId for the analysis job.
        return String.valueOf(request.correlationId()) + ":"
            + String.valueOf(requirementId) + ":"
            + String.valueOf(evaluationVersion == null ? "v1" : evaluationVersion);
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
