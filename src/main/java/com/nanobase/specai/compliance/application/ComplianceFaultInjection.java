package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Staging/development-only fault injection for concurrency and recovery gates.
 * Hard-disabled when {@code specai.environment=production} regardless of flag.
 */
@Component
public class ComplianceFaultInjection {
    private static final Logger log = LoggerFactory.getLogger(ComplianceFaultInjection.class);

    public static final String PAUSE_AFTER_MODEL_RESPONSE = "PAUSE_AFTER_MODEL_RESPONSE";
    public static final String PAUSE_BEFORE_PERSIST = "PAUSE_BEFORE_PERSIST";
    public static final String PAUSE_AFTER_PREPARE = "PAUSE_AFTER_PREPARE";

    private final boolean enabled;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, RuleState> rules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();

    public ComplianceFaultInjection(
        ObjectMapper mapper,
        @Value("${specai.environment:development}") String environment,
        @Value("${specai.compliance.fault-injection.enabled:false}") boolean enabledFlag
    ) {
        this.mapper = mapper;
        boolean production = "production".equalsIgnoreCase(environment == null ? "" : environment.trim());
        this.enabled = enabledFlag && !production;
        log.info("event=COMPLIANCE_FAULT_INJECTION_INIT enabled={} environment={}",
            this.enabled, environment);
    }

    public boolean enabled() {
        return enabled;
    }

    public synchronized void replaceRules(JsonNode root) {
        if (!enabled) {
            throw new IllegalStateException("Fault injection is disabled");
        }
        rules.clear();
        latches.clear();
        if (root == null || !root.path("enabled").asBoolean(false)) {
            log.info("event=COMPLIANCE_FAULT_INJECTION_CLEARED");
            return;
        }
        JsonNode list = root.path("rules");
        if (!list.isArray()) {
            return;
        }
        for (JsonNode ruleNode : list) {
            String action = ruleNode.path("action").path("type").asText("");
            String key = matchKey(ruleNode.path("match"));
            if (key.isBlank() || action.isBlank()) {
                continue;
            }
            int max = Math.max(1, ruleNode.path("maxExecutions").asInt(1));
            long timeoutMs = ruleNode.path("action").path("timeoutMs").asLong(120_000L);
            rules.put(key + "|" + action, new RuleState(action, max, timeoutMs, new AtomicInteger()));
            if (action.startsWith("PAUSE_")) {
                latches.put(key + "|" + action, new CountDownLatch(1));
            }
            log.info("event=COMPLIANCE_FAULT_INJECTION_RULE_ADDED matchKey={} action={} max={}",
                key, action, max);
        }
    }

    public void releasePause(String matchKey, String action) {
        if (!enabled) {
            return;
        }
        CountDownLatch latch = latches.get(normalize(matchKey) + "|" + action);
        if (latch != null) {
            latch.countDown();
            log.info("event=COMPLIANCE_FAULT_INJECTION_RELEASED matchKey={} action={}",
                matchKey, action);
        }
    }

    public void maybePause(String action, UUID correlationId, UUID jobId, UUID taskId) {
        if (!enabled) {
            return;
        }
        RuleState matched = find(action, correlationId, jobId, taskId);
        if (matched == null) {
            return;
        }
        int used = matched.executions.incrementAndGet();
        if (used > matched.maxExecutions) {
            return;
        }
        String key = matchedKey(action, correlationId, jobId, taskId);
        CountDownLatch latch = latches.computeIfAbsent(key, ignored -> new CountDownLatch(1));
        log.warn("event=COMPLIANCE_FAULT_INJECTION_PAUSE action={} correlationId={} jobId={} "
                + "taskId={} timeoutMs={}",
            action, correlationId, jobId, taskId, matched.timeoutMs);
        try {
            boolean released = latch.await(matched.timeoutMs, TimeUnit.MILLISECONDS);
            log.warn("event=COMPLIANCE_FAULT_INJECTION_RESUME action={} correlationId={} "
                    + "released={}",
                action, correlationId, released);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fault injection pause interrupted", interrupted);
        }
    }

    public Map<String, Object> snapshot() {
        return Map.of(
            "enabled", enabled,
            "ruleCount", rules.size(),
            "pausedKeys", latches.keySet()
        );
    }

    private RuleState find(String action, UUID correlationId, UUID jobId, UUID taskId) {
        String[] keys = {
            "correlationId=" + correlationId,
            "jobId=" + jobId,
            "taskId=" + taskId
        };
        for (String key : keys) {
            RuleState state = rules.get(key + "|" + action);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    private String matchedKey(String action, UUID correlationId, UUID jobId, UUID taskId) {
        String[] keys = {
            "correlationId=" + correlationId,
            "jobId=" + jobId,
            "taskId=" + taskId
        };
        for (String key : keys) {
            if (rules.containsKey(key + "|" + action)) {
                return key + "|" + action;
            }
        }
        return "correlationId=" + correlationId + "|" + action;
    }

    private String matchKey(JsonNode match) {
        Iterator<String> fields = match.fieldNames();
        if (!fields.hasNext()) {
            return "";
        }
        String field = fields.next();
        return field + "=" + normalize(match.path(field).asText());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record RuleState(String action, int maxExecutions, long timeoutMs,
                             AtomicInteger executions) {
    }
}
