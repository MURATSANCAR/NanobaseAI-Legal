package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup / release guardrails for the compliance orchestration v1.0 production baseline.
 *
 * <p>Validated live rule:
 * {@code databasePoolSize >= workerConcurrency + operationalHeadroom}.
 *
 * <p>{@code operationalHeadroom} covers API polling, heartbeat, claim/persist, reclaim,
 * and outbox — configured per deployment profile, not a hardcoded Java constant.
 */
@Component
public class ComplianceDeploymentGuardrails {
    private static final Logger log = LoggerFactory.getLogger(ComplianceDeploymentGuardrails.class);

    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final int databasePoolSize;
    private final int workerConcurrency;
    private final int workerMaxConcurrency;
    private final int operationalHeadroom;
    private final boolean faultInjectionEnabled;
    private final long reclaimIntervalMs;
    private final String leaseDuration;
    private final String orchestratorBaseUrl;
    private final boolean enforce;
    private final boolean requireRedisCapacity;

    public ComplianceDeploymentGuardrails(
        ObjectMapper objectMapper,
        Environment environment,
        @Value("${spring.datasource.hikari.maximum-pool-size:20}") int databasePoolSize,
        @Value("${spring.rabbitmq.listener.simple.concurrency:1}") int workerConcurrency,
        @Value("${spring.rabbitmq.listener.simple.max-concurrency:1}") int workerMaxConcurrency,
        @Value("${specai.compliance.deployment.operational-headroom:2}") int operationalHeadroom,
        @Value("${specai.compliance.fault-injection.enabled:false}") boolean faultInjectionEnabled,
        @Value("${specai.compliance.reclaim-interval-ms:30000}") long reclaimIntervalMs,
        @Value("${specai.compliance.lease-duration:PT15M}") String leaseDuration,
        @Value("${specai.ai-orchestrator.base-url:http://localhost:8090}") String orchestratorBaseUrl,
        @Value("${specai.compliance.deployment.enforce:false}") boolean enforce,
        @Value("${specai.compliance.deployment.require-redis-capacity:false}")
        boolean requireRedisCapacity
    ) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.databasePoolSize = databasePoolSize;
        this.workerConcurrency = Math.max(1, workerConcurrency);
        this.workerMaxConcurrency = Math.max(this.workerConcurrency, workerMaxConcurrency);
        this.operationalHeadroom = Math.max(0, operationalHeadroom);
        this.faultInjectionEnabled = faultInjectionEnabled;
        this.reclaimIntervalMs = reclaimIntervalMs;
        this.leaseDuration = leaseDuration;
        this.orchestratorBaseUrl = orchestratorBaseUrl;
        this.enforce = enforce;
        this.requireRedisCapacity = requireRedisCapacity;
    }

    @PostConstruct
    void validate() {
        ValidationResult result = evaluate();
        Map<String, Object> payload = result.asLogPayload(
            databasePoolSize, workerConcurrency, workerMaxConcurrency, operationalHeadroom);
        try {
            log.info("{}", objectMapper.writeValueAsString(payload));
        } catch (Exception serializationFailure) {
            log.info("event=COMPLIANCE_DEPLOYMENT_GUARDRAILS ok={} reason={}",
                result.ok(), result.reason());
        }
        if (!result.ok()) {
            String message = "Compliance deployment guardrail failed: " + result.reason();
            if (shouldEnforce()) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }

    ValidationResult evaluate() {
        String poolReason = poolCapacityReason(
            databasePoolSize, workerMaxConcurrency, operationalHeadroom);
        if (poolReason != null) {
            return ValidationResult.fail(poolReason);
        }
        if (workerMaxConcurrency < workerConcurrency) {
            return ValidationResult.fail(String.format(Locale.ROOT,
                "worker max-concurrency=%d < concurrency=%d",
                workerMaxConcurrency, workerConcurrency));
        }
        if (isProductionEnvironment() && faultInjectionEnabled) {
            return ValidationResult.fail(
                "fault injection must be disabled in production");
        }
        if (reclaimIntervalMs <= 0) {
            return ValidationResult.fail("reclaim scheduler interval must be > 0");
        }
        if (leaseDuration == null || leaseDuration.isBlank()) {
            return ValidationResult.fail("compliance lease-duration must be configured");
        }
        // Production-only Redis capacity probe (opt-in via require-redis-capacity=true).
        if (requireRedisCapacity && shouldEnforce()) {
            String capacityReason = probeOrchestratorCapacityReady();
            if (capacityReason != null) {
                return ValidationResult.fail(capacityReason);
            }
        }
        return ValidationResult.pass(workerMaxConcurrency + operationalHeadroom);
    }

    /**
     * Policy: {@code databasePoolSize >= workerConcurrency + operationalHeadroom}.
     *
     * @return failure reason or {@code null} when OK
     */
    static String poolCapacityReason(int databasePoolSize, int workerConcurrency,
                                     int operationalHeadroom) {
        int workers = Math.max(1, workerConcurrency);
        int headroom = Math.max(0, operationalHeadroom);
        int required = workers + headroom;
        if (databasePoolSize < required) {
            return String.format(Locale.ROOT,
                "databasePoolSize=%d < workerConcurrency=%d + operationalHeadroom=%d (required=%d)",
                databasePoolSize, workers, headroom, required);
        }
        return null;
    }

    /**
     * Requires orchestrator readiness fields (not Redis PING alone):
     * capacityProvider=redis, failurePolicy=FAIL_CLOSED,
     * providerReachable=true, leaseOperationsHealthy=true.
     *
     * @return failure reason or {@code null} when OK
     */
    private String probeOrchestratorCapacityReady() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(orchestratorBaseUrl) + "/health/ready"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "AI orchestrator /health/ready returned HTTP " + response.statusCode();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
            String provider = stringField(body, "capacityProvider");
            String failurePolicy = stringField(body, "failurePolicy");
            if (provider == null
                || !provider.toLowerCase(Locale.ROOT).contains("redis")
                || provider.toLowerCase(Locale.ROOT).contains("unavailable")) {
                return "AI orchestrator capacityProvider must be redis; found=" + provider;
            }
            if (!"FAIL_CLOSED".equalsIgnoreCase(failurePolicy)) {
                return "AI orchestrator failurePolicy must be FAIL_CLOSED; found="
                    + failurePolicy;
            }
            if (!booleanField(body, "providerReachable")) {
                return "AI orchestrator providerReachable must be true";
            }
            if (!booleanField(body, "leaseOperationsHealthy")) {
                return "AI orchestrator leaseOperationsHealthy must be true "
                    + "(health-profile acquire/release probe)";
            }
            return null;
        } catch (Exception probeFailure) {
            log.warn("event=COMPLIANCE_CAPACITY_PROVIDER_PROBE_FAILED error={}",
                probeFailure.toString());
            return "unable to verify AI orchestrator Redis capacity readiness at startup: "
                + probeFailure;
        }
    }

    private static String stringField(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanField(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean shouldEnforce() {
        return enforce || isProductionEnvironment();
    }

    private boolean isProductionEnvironment() {
        String environmentName = environment.getProperty("specai.environment", "");
        if ("production".equalsIgnoreCase(environmentName.trim())) {
            return true;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8090";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record ValidationResult(boolean ok, String reason, int requiredPoolSize) {
        static ValidationResult pass(int requiredPoolSize) {
            return new ValidationResult(true, "ok", requiredPoolSize);
        }

        static ValidationResult fail(String reason) {
            return new ValidationResult(false, reason, -1);
        }

        Map<String, Object> asLogPayload(int databasePoolSize, int workerConcurrency,
                                         int workerMaxConcurrency, int operationalHeadroom) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "COMPLIANCE_DEPLOYMENT_GUARDRAILS");
            payload.put("ok", ok);
            payload.put("reason", reason);
            payload.put("databasePoolSize", databasePoolSize);
            payload.put("workerConcurrency", workerConcurrency);
            payload.put("workerMaxConcurrency", workerMaxConcurrency);
            payload.put("operationalHeadroom", operationalHeadroom);
            payload.put("requiredPoolSize", requiredPoolSize > 0
                ? requiredPoolSize
                : workerMaxConcurrency + operationalHeadroom);
            payload.put("baseline", "compliance-orchestration-v1.0");
            return payload;
        }
    }
}
