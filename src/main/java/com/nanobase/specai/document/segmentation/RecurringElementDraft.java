package com.nanobase.specai.document.segmentation;

public record RecurringElementDraft(
    String normalizedSignature,
    String elementTypeCode,
    int pageOccurrenceCount,
    double pageRatio,
    double confidence
) {
}
