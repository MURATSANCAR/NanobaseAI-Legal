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
