package com.nanobase.specai.operations.application;

/**
 * Explicit feature-disabled signal for API callers. Never return empty success.
 */
public class FeatureDisabledException extends RuntimeException {
    private final String featureCode;

    public FeatureDisabledException(String featureCode) {
        super("FEATURE_DISABLED:" + featureCode);
        this.featureCode = featureCode;
    }

    public String featureCode() {
        return featureCode;
    }

    public String errorCode() {
        return "FEATURE_DISABLED";
    }
}
