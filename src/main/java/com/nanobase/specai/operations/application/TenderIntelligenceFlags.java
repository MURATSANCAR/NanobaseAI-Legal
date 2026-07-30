package com.nanobase.specai.operations.application;

/**
 * Tender intelligence feature flag codes. Defaults are FALSE in V19 seed.
 */
public final class TenderIntelligenceFlags {
    public static final String TENDER_DOMAIN_V2 = "TENDER_DOMAIN_V2_ENABLED";
    public static final String REQUIREMENT_CLASSIFICATION = "REQUIREMENT_CLASSIFICATION_ENABLED";
    public static final String COMPANY_CAPABILITY_REGISTRY = "COMPANY_CAPABILITY_REGISTRY_ENABLED";
    public static final String DETERMINISTIC_EVALUATION = "DETERMINISTIC_EVALUATION_ENABLED";
    public static final String GAP_ANALYSIS = "GAP_ANALYSIS_ENABLED";
    public static final String CLARIFICATION_MANAGEMENT = "CLARIFICATION_MANAGEMENT_ENABLED";
    public static final String RISK_ENGINE = "RISK_ENGINE_ENABLED";
    public static final String BID_DECISION = "BID_DECISION_ENABLED";
    public static final String OBLIGATION_MANAGEMENT = "OBLIGATION_MANAGEMENT_ENABLED";

    private TenderIntelligenceFlags() {
    }
}
