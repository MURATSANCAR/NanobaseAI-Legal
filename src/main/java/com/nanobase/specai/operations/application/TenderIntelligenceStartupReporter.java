package com.nanobase.specai.operations.application;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emits the production baseline and tender-intelligence kill-switch state at startup
 * so operators can verify runbook section B without digging through config files.
 */
@Component
public class TenderIntelligenceStartupReporter {
    private static final Logger log = LoggerFactory.getLogger(TenderIntelligenceStartupReporter.class);

    private final FeatureFlagService flags;
    private final String routingMode;
    private final boolean fastEnabled;
    private final boolean shadowEnabled;
    private final int evaluationParallelism;

    public TenderIntelligenceStartupReporter(
        FeatureFlagService flags,
        @Value("${specai.compliance.routing.mode:BALANCED_ONLY}") String routingMode,
        @Value("${specai.compliance.routing.fast-enabled:false}") boolean fastEnabled,
        @Value("${specai.compliance.routing.shadow-enabled:false}") boolean shadowEnabled,
        @Value("${specai.compliance.evaluation-parallelism:1}") int evaluationParallelism
    ) {
        this.flags = flags;
        this.routingMode = routingMode;
        this.fastEnabled = fastEnabled;
        this.shadowEnabled = shadowEnabled;
        this.evaluationParallelism = evaluationParallelism;
    }

    @PostConstruct
    void report() {
        Map<String, Boolean> snapshot = flags.tenderIntelligenceEnvironmentSnapshot();
        boolean anyIntelligence = snapshot.values().stream().anyMatch(Boolean.TRUE::equals);
        log.info(
            "production_baseline routingMode={} evaluationParallelism={} fastEnabled={} "
                + "shadowEnabled={} tenderIntelligenceEnvAllow={}",
            routingMode, evaluationParallelism, fastEnabled, shadowEnabled, anyIntelligence);
        log.info("tender_intelligence_flags_disabled={}", !anyIntelligence);
        log.info("tender_intelligence_env_snapshot {}",
            snapshot.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(" ")));
        if (fastEnabled || shadowEnabled) {
            log.warn("FAST/SHADOW env flags are enabled; production V1 expects both false");
        }
        if (evaluationParallelism != 1) {
            log.warn("compliance evaluationParallelism={} — production V1 enforces sequential=1",
                evaluationParallelism);
        }
    }
}
