package com.nanobase.specai.compliance.application;

/**
 * Technical failure codes for semantic evaluation. Distinct from compliance decisions.
 */
public enum SemanticEvaluationFailureCode {
    LLM_TIMEOUT,
    LLM_UNAVAILABLE,
    LLM_INVALID_RESPONSE,
    CONTEXT_OVERFLOW,
    EVALUATION_ERROR
}
