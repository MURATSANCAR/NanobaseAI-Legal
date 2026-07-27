package com.nanobase.specai.document.domain;

public enum DocumentStatus {
    UPLOADED,
    VIRUS_SCANNING,
    CLASSIFYING,
    PARSING,
    OCR_PROCESSING,
    STRUCTURE_DETECTION,
    INDEXING,
    READY,
    FAILED,
    MANUAL_REVIEW_REQUIRED
}
