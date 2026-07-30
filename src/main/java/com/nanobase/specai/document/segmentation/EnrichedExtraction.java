package com.nanobase.specai.document.segmentation;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import java.util.List;

public record EnrichedExtraction(
    DocumentExtractionResult result,
    String providerCode,
    String providerVersion,
    List<LayoutBlockDraft> layoutBlocks,
    List<RecurringElementDraft> recurringElements,
    boolean usedFallback
) {
    public EnrichedExtraction {
        layoutBlocks = layoutBlocks == null ? List.of() : List.copyOf(layoutBlocks);
        recurringElements = recurringElements == null
            ? List.of() : List.copyOf(recurringElements);
    }
}
