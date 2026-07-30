package com.nanobase.specai.tender.domain;

/**
 * Tender workspace lifecycle. Legacy values are retained for backward compatibility.
 */
public enum TenderStatus {
    DRAFT,
    DOCUMENTS_PENDING,
    /** @deprecated Prefer {@link #ANALYZING} when TENDER_DOMAIN_V2_ENABLED */
    ANALYSIS_IN_PROGRESS,
    /** @deprecated Prefer {@link #REVIEW_REQUIRED} when TENDER_DOMAIN_V2_ENABLED */
    REVIEW_IN_PROGRESS,
    COMPLETED,
    ARCHIVED,
    ANALYSIS_PENDING,
    ANALYZING,
    REVIEW_REQUIRED,
    READY_FOR_DECISION,
    BID_APPROVED,
    CONDITIONAL_BID,
    NO_BID,
    SUBMITTED,
    AWARDED,
    NOT_AWARDED,
    CONTRACT_ACTIVE,
    CLOSED,
    CANCELLED
}
