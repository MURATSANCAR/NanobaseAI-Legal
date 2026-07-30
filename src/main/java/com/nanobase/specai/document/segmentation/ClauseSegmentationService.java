package com.nanobase.specai.document.segmentation;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractionWarning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClauseSegmentationService {
    private static final Logger log = LoggerFactory.getLogger(ClauseSegmentationService.class);
    private final List<ClauseSegmentationProvider> providers;

    public ClauseSegmentationService(List<ClauseSegmentationProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public DocumentExtractionResult enrich(DocumentExtractionResult extraction) {
        List<ExtractedClause> current = extraction.clauses() == null
            ? List.of() : extraction.clauses();
        ClauseSegmentationContext context = new ClauseSegmentationContext(extraction, current);
        ClauseSegmentationResult chosen = null;
        for (ClauseSegmentationProvider provider : providers) {
            ClauseSegmentationResult result = provider.segment(context);
            if (result.clauses() != null && !result.clauses().isEmpty()) {
                chosen = result;
                log.info(
                    "event=CLAUSE_SEGMENTATION_SELECTED provider={} clauseCount={} fallback={}",
                    result.providerCode(), result.clauses().size(), result.usedFallback());
                break;
            }
        }
        if (chosen == null) {
            log.warn("event=CLAUSE_SEGMENTATION_EMPTY documentVersionId={}",
                extraction.documentVersionId());
            List<ExtractionWarning> warnings = new ArrayList<>(extraction.warnings());
            warnings.add(new ExtractionWarning(
                "CLAUSE_ZERO",
                "WARNING",
                "No clause segments could be produced from provider chain",
                null,
                Map.of("providers", providers.stream().map(ClauseSegmentationProvider::providerCode)
                    .toList())));
            return new DocumentExtractionResult(
                extraction.documentVersionId(),
                extraction.provider(),
                extraction.providerVersion(),
                extraction.pageCount(),
                extraction.language(),
                extraction.textQualityScore(),
                extraction.pages(),
                List.of(),
                extraction.tables(),
                warnings,
                withMeta(extraction.metadata(), Map.of(
                    "clauseSegmentation", "EMPTY",
                    "clauseCount", 0)));
        }
        Map<String, Object> meta = withMeta(extraction.metadata(), Map.of(
            "clauseSegmentationProvider", chosen.providerCode(),
            "clauseSegmentationFallback", chosen.usedFallback(),
            "clauseCount", chosen.clauses().size(),
            "layoutBlockCount", chosen.layoutBlocks().size(),
            "recurringElementCount", chosen.recurringElements().size()));
        List<ExtractionWarning> warnings = new ArrayList<>(extraction.warnings());
        if (chosen.usedFallback()) {
            warnings.add(new ExtractionWarning(
                "CLAUSE_FALLBACK_USED",
                "INFO",
                "Clause segmentation used audited fallback provider",
                null,
                Map.of("provider", chosen.providerCode())));
        }
        return new DocumentExtractionResult(
            extraction.documentVersionId(),
            extraction.provider(),
            extraction.providerVersion(),
            extraction.pageCount(),
            extraction.language(),
            extraction.textQualityScore(),
            extraction.pages(),
            chosen.clauses(),
            extraction.tables(),
            warnings,
            meta);
    }

    private static Map<String, Object> withMeta(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> merged = new HashMap<>(base == null ? Map.of() : base);
        merged.putAll(extra);
        return Map.copyOf(merged);
    }
}
