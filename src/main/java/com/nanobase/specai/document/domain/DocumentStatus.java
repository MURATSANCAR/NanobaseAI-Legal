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
    PARSING_FAILED,
    OCR_FAILED,
    UNSUPPORTED_FORMAT,
    PASSWORD_PROTECTED,
    MANUAL_REVIEW_REQUIRED
}
