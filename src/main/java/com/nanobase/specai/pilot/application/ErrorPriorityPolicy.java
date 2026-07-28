package com.nanobase.specai.pilot.application;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ErrorPriorityPolicy {
    private static final Set<String> NON_DEFERRABLE_CAUSES = Set.of(
        "AUTHORIZATION", "SECURITY_SCAN", "INFRASTRUCTURE");

    public PriorityDecision evaluate(
        double impact,
        double frequency,
        String severity,
        String rootCause,
        boolean tenantIsolationRisk,
        boolean dataLossRisk,
        boolean auditIntegrityRisk
    ) {
        validateScore(impact, "impact");
        validateScore(frequency, "frequency");
        boolean securityInvariant = tenantIsolationRisk || dataLossRisk || auditIntegrityRisk;
        boolean blocker = securityInvariant
            || "CRITICAL".equals(severity)
            || (NON_DEFERRABLE_CAUSES.contains(rootCause) && impact >= 80);
        double priority = Math.min(100,
            impact * 0.60 + frequency * 0.30 + (blocker ? 10 : 0));
        return new PriorityDecision(round(priority), blocker,
            blocker ? "Release blocker policy matched" : "Weighted priority policy");
    }

    private void validateScore(double score, String field) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(field + " score must be between 0 and 100");
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record PriorityDecision(double priorityScore, boolean releaseBlocker, String reason) {
    }
}
