package com.nanobase.specai.document.domain;

public enum DocumentStatus {
    UPLOADED,
    VIRUS_SCANNING,
    CLASSIFYING,
    QUEUED,
    PARSING,
    OCR_PROCESSING,
    STRUCTURE_DETECTION,
    INDEXING,
    READY,
    FAILED,
    MANUAL_REVIEW_REQUIRED,
    CANCELLED;

    public boolean terminal() {
        return this == READY || this == FAILED || this == MANUAL_REVIEW_REQUIRED
            || this == CANCELLED;
    }
}
