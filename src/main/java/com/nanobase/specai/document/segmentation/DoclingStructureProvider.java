package com.nanobase.specai.document.segmentation;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Pass-through when Docling/OpenContracts already produced structured clauses. */
@Component
@Order(10)
public class DoclingStructureProvider implements ClauseSegmentationProvider {
    @Override
    public String providerCode() {
        return "DOCLING_STRUCTURE";
    }

    @Override
    public ClauseSegmentationResult segment(ClauseSegmentationContext context) {
        DocumentExtractionResult extraction = context.extraction();
        List<ExtractedClause> clauses = context.currentClauses();
        if (clauses == null || clauses.isEmpty()) {
            return ClauseSegmentationResult.empty(providerCode());
        }
        return new ClauseSegmentationResult(
            providerCode(),
            extraction.providerVersion() == null ? "1.0" : extraction.providerVersion(),
            clauses,
            List.of(),
            List.of(),
            false);
    }
}
