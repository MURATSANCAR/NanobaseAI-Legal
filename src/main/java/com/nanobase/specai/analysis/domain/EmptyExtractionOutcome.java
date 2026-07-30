package com.nanobase.specai.analysis.domain;

/**
 * Taxonomy for empty requirement extraction outcomes.
 * Codes are versioned concepts — not a closed Java switch contract for all callers.
 */
public final class EmptyExtractionOutcome {
    public static final String VALID_EMPTY = "VALID_EMPTY";
    public static final String SUSPICIOUS_EMPTY = "SUSPICIOUS_EMPTY";
    public static final String LOW_SIGNAL_EMPTY = "LOW_SIGNAL_EMPTY";
    public static final String MODEL_FAILURE = "MODEL_FAILURE";
    public static final String SCHEMA_FAILURE = "SCHEMA_FAILURE";
    public static final String TIMEOUT_EMPTY = "TIMEOUT_EMPTY";

    private EmptyExtractionOutcome() {
    }

    public static String classify(double signalScore, boolean timedOut, boolean schemaFailed,
                                  boolean modelFailed, int extracted) {
        if (extracted > 0) {
            return null;
        }
        if (timedOut) {
            return TIMEOUT_EMPTY;
        }
        if (schemaFailed) {
            return SCHEMA_FAILURE;
        }
        if (modelFailed) {
            return MODEL_FAILURE;
        }
        if (signalScore >= 0.7d) {
            return SUSPICIOUS_EMPTY;
        }
        if (signalScore < 0.35d) {
            return LOW_SIGNAL_EMPTY;
        }
        return VALID_EMPTY;
    }
}
