package com.nanobase.specai.operations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Emits a single structured {@code production_runtime_policy} JSON line at startup
 * and optionally refuses non-baseline routing in production.
 */
@Component
public class TenderIntelligenceStartupReporter {
    private static final Logger log = LoggerFactory.getLogger(TenderIntelligenceStartupReporter.class);

    private final FeatureFlagService flags;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final String routingMode;
    private final boolean fastEnabled;
    private final boolean shadowEnabled;
    private final int evaluationParallelism;
    private final boolean enforce;

    public TenderIntelligenceStartupReporter(
        FeatureFlagService flags,
        ObjectMapper objectMapper,
        Environment environment,
        @Value("${specai.compliance.routing.mode:BALANCED_ONLY}") String routingMode,
        @Value("${specai.compliance.routing.fast-enabled:false}") boolean fastEnabled,
        @Value("${specai.compliance.routing.shadow-enabled:false}") boolean shadowEnabled,
        @Value("${specai.compliance.evaluation-parallelism:1}") int evaluationParallelism,
        @Value("${specai.production-runtime-policy.enforce:false}") boolean enforce
    ) {
        this.flags = flags;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.routingMode = routingMode;
        this.fastEnabled = fastEnabled;
        this.shadowEnabled = shadowEnabled;
        this.evaluationParallelism = evaluationParallelism;
        this.enforce = enforce;
    }

    @PostConstruct
    void report() {
        Map<String, Boolean> env = flags.tenderIntelligenceEnvironmentSnapshot();
        Map<String, Object> intelligenceEnv = new LinkedHashMap<>();
        intelligenceEnv.put("tenderDomainV2", env.get(TenderIntelligenceFlags.TENDER_DOMAIN_V2));
        intelligenceEnv.put("requirementClassification",
            env.get(TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION));
        intelligenceEnv.put("companyCapabilityRegistry",
            env.get(TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY));
        intelligenceEnv.put("deterministicEvaluation",
            env.get(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION));
        intelligenceEnv.put("gapAnalysis", env.get(TenderIntelligenceFlags.GAP_ANALYSIS));
        intelligenceEnv.put("clarificationManagement",
            env.get(TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT));
        intelligenceEnv.put("riskEngine", env.get(TenderIntelligenceFlags.RISK_ENGINE));
        intelligenceEnv.put("bidDecision", env.get(TenderIntelligenceFlags.BID_DECISION));
        intelligenceEnv.put("obligationManagement",
            env.get(TenderIntelligenceFlags.OBLIGATION_MANAGEMENT));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "production_runtime_policy");
        payload.put("modelRouting", routingMode);
        payload.put("evaluationParallelism", evaluationParallelism);
        payload.put("fastEnabled", fastEnabled);
        payload.put("shadowEnabled", shadowEnabled);
        payload.put("intelligenceEnv", intelligenceEnv);

        try {
            log.info("{}", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize production_runtime_policy", ex);
        }

        boolean baselineOk = "BALANCED_ONLY".equalsIgnoreCase(routingMode)
            && !fastEnabled
            && !shadowEnabled
            && evaluationParallelism == 1;
        if (!baselineOk) {
            String message = String.format(Locale.ROOT,
                "Invalid production runtime policy: modelRouting=%s evaluationParallelism=%d "
                    + "fastEnabled=%s shadowEnabled=%s",
                routingMode, evaluationParallelism, fastEnabled, shadowEnabled);
            if (shouldEnforce()) {
                throw new IllegalStateException(message);
            }
            log.warn(message);
        }
    }

    private boolean shouldEnforce() {
        if (enforce) {
            return true;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
