package com.nanobase.specai.analysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmptyExtractionOutcomeTest {

    // ── extracted > 0 ────────────────────────────────────────────────────────

    @Test
    void extractedRequirementsReturnNull() {
        assertThat(EmptyExtractionOutcome.classify(0.9, false, false, false, 1)).isNull();
        assertThat(EmptyExtractionOutcome.classify(0.1, true, true, true, 5)).isNull();
    }

    // ── timeout beats everything when extracted == 0 ─────────────────────────

    @Test
    void timeoutEmptyWhenTimedOut() {
        assertThat(EmptyExtractionOutcome.classify(0.9, true, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.TIMEOUT_EMPTY);
    }

    @Test
    void timeoutEmptyPrecedesSchemaFailure() {
        assertThat(EmptyExtractionOutcome.classify(0.5, true, true, false, 0))
            .isEqualTo(EmptyExtractionOutcome.TIMEOUT_EMPTY);
    }

    @Test
    void timeoutEmptyPrecedesModelFailure() {
        assertThat(EmptyExtractionOutcome.classify(0.5, true, false, true, 0))
            .isEqualTo(EmptyExtractionOutcome.TIMEOUT_EMPTY);
    }

    // ── schema failure ────────────────────────────────────────────────────────

    @Test
    void schemaFailureWhenSchemaFailed() {
        assertThat(EmptyExtractionOutcome.classify(0.5, false, true, false, 0))
            .isEqualTo(EmptyExtractionOutcome.SCHEMA_FAILURE);
    }

    @Test
    void schemaFailurePrecedesModelFailure() {
        assertThat(EmptyExtractionOutcome.classify(0.5, false, true, true, 0))
            .isEqualTo(EmptyExtractionOutcome.SCHEMA_FAILURE);
    }

    // ── model failure ─────────────────────────────────────────────────────────

    @Test
    void modelFailureWhenModelFailed() {
        assertThat(EmptyExtractionOutcome.classify(0.5, false, false, true, 0))
            .isEqualTo(EmptyExtractionOutcome.MODEL_FAILURE);
    }

    // ── signal-based outcomes ─────────────────────────────────────────────────

    @Test
    void suspiciousEmptyAtHighSignalScore() {
        assertThat(EmptyExtractionOutcome.classify(0.7, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.SUSPICIOUS_EMPTY);
        assertThat(EmptyExtractionOutcome.classify(1.0, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.SUSPICIOUS_EMPTY);
    }

    @Test
    void lowSignalEmptyBelowThreshold() {
        assertThat(EmptyExtractionOutcome.classify(0.34, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.LOW_SIGNAL_EMPTY);
        assertThat(EmptyExtractionOutcome.classify(0.0, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.LOW_SIGNAL_EMPTY);
    }

    @Test
    void validEmptyInMiddleRange() {
        // 0.35 <= score < 0.7 with no failures
        assertThat(EmptyExtractionOutcome.classify(0.35, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.VALID_EMPTY);
        assertThat(EmptyExtractionOutcome.classify(0.5, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.VALID_EMPTY);
        assertThat(EmptyExtractionOutcome.classify(0.69, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.VALID_EMPTY);
    }

    // ── boundary values ───────────────────────────────────────────────────────

    @Test
    void exactlyAtSuspiciousThreshold() {
        assertThat(EmptyExtractionOutcome.classify(0.7, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.SUSPICIOUS_EMPTY);
    }

    @Test
    void exactlyBelowLowSignalThreshold() {
        // 0.35 is the boundary — 0.349 should be LOW_SIGNAL
        assertThat(EmptyExtractionOutcome.classify(0.349, false, false, false, 0))
            .isEqualTo(EmptyExtractionOutcome.LOW_SIGNAL_EMPTY);
    }

    // ── string constants are stable ───────────────────────────────────────────

    @Test
    void constantsHaveExpectedValues() {
        assertThat(EmptyExtractionOutcome.VALID_EMPTY).isEqualTo("VALID_EMPTY");
        assertThat(EmptyExtractionOutcome.SUSPICIOUS_EMPTY).isEqualTo("SUSPICIOUS_EMPTY");
        assertThat(EmptyExtractionOutcome.LOW_SIGNAL_EMPTY).isEqualTo("LOW_SIGNAL_EMPTY");
        assertThat(EmptyExtractionOutcome.MODEL_FAILURE).isEqualTo("MODEL_FAILURE");
        assertThat(EmptyExtractionOutcome.SCHEMA_FAILURE).isEqualTo("SCHEMA_FAILURE");
        assertThat(EmptyExtractionOutcome.TIMEOUT_EMPTY).isEqualTo("TIMEOUT_EMPTY");
    }
}
