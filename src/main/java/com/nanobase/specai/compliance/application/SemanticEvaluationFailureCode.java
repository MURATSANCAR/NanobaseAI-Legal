package com.nanobase.specai.compliance.application;

/**
 * Technical failure codes for semantic evaluation. Distinct from compliance decisions.
 *
 * <p>When {@code candidateCount > 0} and a technical failure occurs, persist
 * {@code evaluationStatus=FAILED} with {@code decision=null} — never map these to
 * {@code INSUFFICIENT_INFORMATION}.
 */
public enum SemanticEvaluationFailureCode {
    LLM_QUEUE_TIMEOUT,
    LLM_CONNECT_TIMEOUT,
    LLM_GENERATION_TIMEOUT,
    LLM_TIMEOUT,
    LLM_UNAVAILABLE,
    LLM_CANCELLED,
    LLM_INVALID_RESPONSE,
    LLM_CONTEXT_OVERFLOW,
    LLM_OVERLOADED,
    LLM_CIRCUIT_OPEN,
    /** @deprecated Prefer {@link #LLM_CONTEXT_OVERFLOW}. */
    CONTEXT_OVERFLOW,
    EVALUATION_ERROR
}
