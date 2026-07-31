package com.nanobase.specai.document.capability;

/**
 * Spec Intelligence v1.1 feature flags. Defaults FALSE in V33 seed.
 */
public final class SpecIntelligenceV11Flags {
    public static final String DOCUMENT_CAPABILITY_PROFILE =
        "DOCUMENT_CAPABILITY_PROFILE_ENABLED";
    public static final String OCR_QUALITY_GATES = "OCR_QUALITY_GATES_ENABLED";
    public static final String OCR_NUMERIC_INTEGRITY = "OCR_NUMERIC_INTEGRITY_ENABLED";
    public static final String DOCX_STRUCTURE_PIPELINE = "DOCX_STRUCTURE_PIPELINE_ENABLED";
    public static final String CANONICAL_TABLE_CELLS = "CANONICAL_TABLE_CELLS_ENABLED";
    public static final String TABLE_REQUIREMENT_EXTRACTION =
        "TABLE_REQUIREMENT_EXTRACTION_ENABLED";
    public static final String KNOWLEDGE_CORPUS_V11 = "KNOWLEDGE_CORPUS_V11_ENABLED";
    public static final String REPORT_VISUAL_VALIDATION = "REPORT_VISUAL_VALIDATION_ENABLED";
    public static final String OBJECT_DELIVERY_STRATEGY = "OBJECT_DELIVERY_STRATEGY_ENABLED";
    public static final String REQUIREMENT_EXTRACTION_TIMING =
        "REQUIREMENT_EXTRACTION_TIMING_ENABLED";
    public static final String EXTRACTION_DEPLOYMENT_GUARDRAILS =
        "EXTRACTION_DEPLOYMENT_GUARDRAILS_ENABLED";
    public static final String BROAD_DOCUMENT_CORPUS = "BROAD_DOCUMENT_CORPUS_ENABLED";

    private SpecIntelligenceV11Flags() {
    }
}
