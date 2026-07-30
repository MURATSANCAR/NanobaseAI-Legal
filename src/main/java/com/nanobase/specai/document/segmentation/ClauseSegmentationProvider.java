package com.nanobase.specai.document.segmentation;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import java.util.List;

public interface ClauseSegmentationProvider {
    String providerCode();

    /**
     * Returns segments when this provider can improve or replace the current clause set.
     * Empty list means "no opinion / skip".
     */
    ClauseSegmentationResult segment(ClauseSegmentationContext context);
}

record ClauseSegmentationContext(
    DocumentExtractionResult extraction,
    List<ExtractedClause> currentClauses
) {
}

record ClauseSegmentationResult(
    String providerCode,
    String providerVersion,
    List<ExtractedClause> clauses,
    List<LayoutBlockDraft> layoutBlocks,
    List<RecurringElementDraft> recurringElements,
    boolean usedFallback
) {
    static ClauseSegmentationResult empty(String providerCode) {
        return new ClauseSegmentationResult(providerCode, "1.0", List.of(), List.of(),
            List.of(), false);
    }
}

record LayoutBlockDraft(
    int blockIndex,
    int pageNumber,
    String blockTypeCode,
    String textContent,
    String normalizedText,
    int readingOrder,
    double confidence
) {
}

record RecurringElementDraft(
    String normalizedSignature,
    String elementTypeCode,
    int pageOccurrenceCount,
    double pageRatio,
    double confidence
) {
}
