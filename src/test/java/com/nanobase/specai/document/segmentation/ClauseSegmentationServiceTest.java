package com.nanobase.specai.document.segmentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedPage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClauseSegmentationServiceTest {

    @Test
    void coarseDoclingPageClausesAreRefinedByLaterProviders() {
        ClauseSegmentationService service = new ClauseSegmentationService(List.of(
            new DoclingStructureProvider(),
            new TextHierarchyProvider(),
            new ObligationAwareFallbackProvider()));

        String pageText = "1. Genel hükümler\n\n"
            + "Yüklenici sulama otomasyon sistemini şartnamede belirtilen şekilde kurmak zorundadır. "
            + "Tüm saha cihazları teknik şartnameye uygun olarak temin edilecektir. "
            + "Sistem merkezi izleme yazılımı ile entegre çalışacaktır.";
        ExtractedPage page = new ExtractedPage(
            1, 595, 842, 0, pageText, pageText, 0.9, null, Map.of());
        ExtractedClause coarse = new ExtractedClause(
            "docling-page-1", null, "P1", "Sayfa 1", pageText, pageText,
            "PAGE", 1, 1, List.of(), "hash-1", 0,
            Map.of("fallback", true));
        DocumentExtractionResult extraction = new DocumentExtractionResult(
            UUID.randomUUID(), "docling", "1.0", 1, "tr", 0.9,
            List.of(page), List.of(coarse), List.of(), List.of(), Map.of());

        EnrichedExtraction enriched = service.enrich(extraction);

        assertThat(enriched.result().clauses()).isNotEmpty();
        assertThat(enriched.providerCode()).isIn(
            "TEXT_HIERARCHY", "OBLIGATION_AWARE_FALLBACK");
        assertThat(enriched.result().clauses().stream()
            .noneMatch(c -> "PAGE".equalsIgnoreCase(c.clauseType())
                && c.rawText() != null && c.rawText().equals(pageText)))
            .isTrue();
    }
}
