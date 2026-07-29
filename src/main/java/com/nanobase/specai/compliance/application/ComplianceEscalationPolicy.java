package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Escalation rules for FAST → BALANCED compliance evaluation.
 *
 * <p>Matches the production routing contract: keep simple 1–2 evidence cases on FAST;
 * escalate multi-evidence, contradiction, low confidence, or explicit review flags.
 */
public final class ComplianceEscalationPolicy {
    private final double confidenceBelow;
    private final int evidenceAbove;

    public ComplianceEscalationPolicy(double confidenceBelow, int evidenceAbove) {
        this.confidenceBelow = confidenceBelow;
        this.evidenceAbove = Math.max(0, evidenceAbove);
    }

    public String reason(JsonNode output, int evidenceCount, boolean contradiction) {
        if (contradiction) {
            return "CONTRADICTION";
        }
        if (evidenceCount > evidenceAbove) {
            return "MULTI_EVIDENCE";
        }
        if (output != null && output.path("requiresManualReview").asBoolean(false)) {
            return "MANUAL_REVIEW_FLAG";
        }
        double confidence = output == null ? 0d : output.path("confidence").asDouble(0d);
        if (confidence < confidenceBelow) {
            return "LOW_CONFIDENCE";
        }
        return null;
    }

    public boolean shouldEscalate(JsonNode output, int evidenceCount, boolean contradiction) {
        return reason(output, evidenceCount, contradiction) != null;
    }

    public double confidenceBelow() {
        return confidenceBelow;
    }

    public int evidenceAbove() {
        return evidenceAbove;
    }
}
