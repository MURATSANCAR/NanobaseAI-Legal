package com.nanobase.specai.document.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ProcessingStateMachine {
    private static final Map<DocumentStatus, Set<DocumentStatus>> TRANSITIONS = Map.ofEntries(
        Map.entry(DocumentStatus.UPLOADED, EnumSet.of(
            DocumentStatus.VIRUS_SCANNING, DocumentStatus.FAILED,
            DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.VIRUS_SCANNING, EnumSet.of(
            DocumentStatus.CLASSIFYING, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED, DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.CLASSIFYING, EnumSet.of(
            DocumentStatus.QUEUED, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED, DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.QUEUED, EnumSet.of(
            DocumentStatus.PARSING, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED, DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.PARSING, EnumSet.of(
            DocumentStatus.OCR_PROCESSING, DocumentStatus.STRUCTURE_DETECTION,
            DocumentStatus.FAILED, DocumentStatus.MANUAL_REVIEW_REQUIRED,
            DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.OCR_PROCESSING, EnumSet.of(
            DocumentStatus.STRUCTURE_DETECTION, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED, DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.STRUCTURE_DETECTION, EnumSet.of(
            DocumentStatus.INDEXING, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED, DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.INDEXING, EnumSet.of(
            DocumentStatus.READY, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED, DocumentStatus.CANCELLED)),
        Map.entry(DocumentStatus.FAILED, EnumSet.of(DocumentStatus.QUEUED))
    );

    private ProcessingStateMachine() {
    }

    public static void requireTransition(DocumentStatus current, DocumentStatus next) {
        if (current == next) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException(
                "Invalid processing job transition from %s to %s".formatted(current, next));
        }
    }

    public static boolean canTransition(DocumentStatus current, DocumentStatus next) {
        return current == next
            || TRANSITIONS.getOrDefault(current, Set.of()).contains(next);
    }
}
