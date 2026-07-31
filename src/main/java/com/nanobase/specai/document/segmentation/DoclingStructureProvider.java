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
        // Page-sized fallback clauses should be refined by later providers / chunking.
        boolean onlyCoarsePages = clauses.stream().allMatch(clause ->
            "PAGE".equalsIgnoreCase(clause.clauseType())
                || (clause.metadata() != null
                    && Boolean.TRUE.equals(clause.metadata().get("fallback"))));
        if (onlyCoarsePages) {
            return ClauseSegmentationResult.empty(providerCode());
        }
        List<LayoutBlockDraft> blocks = new ArrayList<>();
        int index = 0;
        for (ExtractedClause clause : clauses) {
            String text = clause.normalizedText() == null ? clause.rawText() : clause.normalizedText();
            if (text == null || text.isBlank()) {
                continue;
            }
            blocks.add(new LayoutBlockDraft(
                index,
                clause.pageStart(),
                clause.clauseType() == null ? "PARAGRAPH" : clause.clauseType(),
                text,
                text,
                clause.sortOrder(),
                0.85d));
            index++;
        }
        return new ClauseSegmentationResult(
            providerCode(),
            extraction.providerVersion() == null ? "1.0" : extraction.providerVersion(),
            clauses,
            blocks,
            List.of(),
            false);
    }
}
