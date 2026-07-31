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
    /**
     * True when earlier extraction already produced refined clauses that later providers
     * must not replace. Coarse PAGE / fallback-only sets are not usable and should be refined.
     */
    boolean hasUsableStructuredClauses() {
        if (currentClauses == null || currentClauses.isEmpty()) {
            return false;
        }
        return !currentClauses.stream().allMatch(ClauseSegmentationContext::isCoarsePageOrFallback);
    }

    static boolean isCoarsePageOrFallback(ExtractedClause clause) {
        if (clause == null) {
            return true;
        }
        if ("PAGE".equalsIgnoreCase(clause.clauseType())) {
            return true;
        }
        return clause.metadata() != null
            && Boolean.TRUE.equals(clause.metadata().get("fallback"));
    }
}
