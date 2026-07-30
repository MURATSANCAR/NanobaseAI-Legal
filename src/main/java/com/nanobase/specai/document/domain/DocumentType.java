package com.nanobase.specai.document.domain;

/**
 * Document kinds. Legacy values (ADDENDUM, PRODUCT_CATALOG, CERTIFICATE, TECHNICAL_DRAWING)
 * are retained for backward compatibility alongside tender-intelligence v2 types.
 */
public enum DocumentType {
    TECHNICAL_SPECIFICATION,
    ADMINISTRATIVE_SPECIFICATION,
    DRAFT_CONTRACT,
    /** Legacy alias; prefer {@link #AMENDMENT} for new uploads when v2 is enabled. */
    ADDENDUM,
    PRICE_SCHEDULE,
    PRODUCT_CATALOG,
    CERTIFICATE,
    TECHNICAL_DRAWING,
    OTHER,
    AMENDMENT,
    ANNEX,
    TECHNICAL_FORM,
    FINANCIAL_FORM,
    OFFICIAL_CLARIFICATION,
    QUESTION_RESPONSE,
    AWARD_NOTICE
}
