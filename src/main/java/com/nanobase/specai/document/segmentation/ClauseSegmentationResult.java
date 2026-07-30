package com.nanobase.specai.document.segmentation;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import java.util.List;

public record ClauseSegmentationResult(
    String providerCode,
    String providerVersion,
    List<ExtractedClause> clauses,
    List<LayoutBlockDraft> layoutBlocks,
    List<RecurringElementDraft> recurringElements,
    boolean usedFallback
) {
    public static ClauseSegmentationResult empty(String providerCode) {
        return new ClauseSegmentationResult(providerCode, "1.0", List.of(), List.of(),
            List.of(), false);
    }
}
