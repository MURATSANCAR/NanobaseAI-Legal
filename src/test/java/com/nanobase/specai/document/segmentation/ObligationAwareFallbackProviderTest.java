package com.nanobase.specai.document.segmentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentExtractionResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedClause;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExtractedPage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObligationAwareFallbackProviderTest {

    private ObligationAwareFallbackProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ObligationAwareFallbackProvider();
    }

    // ── provider identity ─────────────────────────────────────────────────────

    @Test
    void providerCodeIsCorrect() {
        assertThat(provider.providerCode()).isEqualTo("OBLIGATION_AWARE_FALLBACK");
    }

    // ── no-op when clauses already exist ─────────────────────────────────────

    @Test
    void returnsEmptyWhenCurrentClausesExist() {
        List<ExtractedClause> existing = List.of(
            makeClause("existing-1", "Already extracted content here."));
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            emptyExtraction(), existing);
        ClauseSegmentationResult result = provider.segment(ctx);
        assertThat(result.clauses()).isEmpty();
        assertThat(result.providerCode()).isEqualTo("OBLIGATION_AWARE_FALLBACK");
    }

    // ── empty pages ───────────────────────────────────────────────────────────

    @Test
    void returnsEmptyWhenNoPagesAvailable() {
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of()), List.of());
        ClauseSegmentationResult result = provider.segment(ctx);
        assertThat(result.clauses()).isEmpty();
    }

    // ── obligation sentences extracted ───────────────────────────────────────

    @Test
    void extractsObligationSentencesFromPageTextWithoutHeadings() {
        String pageText = "The contractor shall provide a detailed schedule within 10 days. "
            + "All materials must meet the specified quality standards. "
            + "The supplier is required to ensure proper delivery timelines.";
        ExtractedPage page = makePage(1, pageText);
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);

        assertThat(result.clauses()).isNotEmpty();
        assertThat(result.usedFallback()).isTrue();
        assertThat(result.providerCode()).isEqualTo("OBLIGATION_AWARE_FALLBACK");
        // Each clause should be backed by obligation text
        for (ExtractedClause clause : result.clauses()) {
            assertThat(clause.rawText()).isNotBlank();
            assertThat(clause.rawText().length()).isGreaterThanOrEqualTo(40);
        }
    }

    @Test
    void extractsObligationSentenceContainingShall() {
        String pageText = "The vendor shall deliver the goods within 30 days of order placement. "
            + "Random non-obligation text here. "
            + "The system shall support concurrent users without degradation.";
        ExtractedPage page = makePage(1, pageText);
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);

        assertThat(result.clauses()).isNotEmpty();
        boolean anyContainsShall = result.clauses().stream()
            .anyMatch(c -> c.rawText().toLowerCase().contains("shall"));
        assertThat(anyContainsShall).isTrue();
    }

    @Test
    void extractsObligationSentenceContainingMust() {
        String pageText = "All components must comply with ISO 9001 certification requirements. "
            + "Filler text without obligations fills the space here as needed. ";
        ExtractedPage page = makePage(1, pageText);
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);

        assertThat(result.clauses()).isNotEmpty();
    }

    @Test
    void handlesMultiplePagesWithObligations() {
        ExtractedPage page1 = makePage(1,
            "For all offered automation equipment the bidder must provide proof of "
            + "financial standing and operational capacity. "
            + "All tender documents must be submitted in triplicate before the deadline.");
        ExtractedPage page2 = makePage(2,
            "During the full contract period the contractor shall maintain insurance "
            + "coverage throughout the project. "
            + "Every listed subcontractor must be pre-approved by the client in writing.");

        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page1, page2)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);
        assertThat(result.clauses().size()).isGreaterThanOrEqualTo(2);
    }

    // ── fallback to page content when no obligations found ───────────────────

    @Test
    void fallsBackToPageContentWhenNoObligationSentencesFound() {
        String longPageText = "This is a substantial page with lots of general "
            + "informational content that does not contain any obligation keywords. "
            + "It is merely descriptive and explains the background of the procurement "
            + "process without making any binding requirements on the bidder or supplier. "
            + "The context is merely informational for participants.";
        ExtractedPage page = makePage(1, longPageText);
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);
        // Should fall back to one bounded clause per substantial page
        assertThat(result.clauses()).isNotEmpty();
    }

    @Test
    void veryShortPageProducesNoClauses() {
        // Page text shorter than 120 chars bypasses fallback
        ExtractedPage page = makePage(1, "Short page.");
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());
        ClauseSegmentationResult result = provider.segment(ctx);
        // Short page should not produce clauses
        assertThat(result.clauses()).isEmpty();
    }

    // ── caps at MAX_CLAUSES ───────────────────────────────────────────────────

    @Test
    void clauseCountCappedAt40() {
        // Build a page with many obligation sentences
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("The supplier shall provide item number ").append(i)
                .append(" as specified in the technical requirements. ");
        }
        ExtractedPage page = makePage(1, sb.toString());
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);
        assertThat(result.clauses().size()).isLessThanOrEqualTo(40);
    }

    // ── layout blocks ─────────────────────────────────────────────────────────

    @Test
    void layoutBlocksProducedForEachClause() {
        String pageText = "The contractor shall deliver the goods on time. "
            + "All parts must conform to the technical specification provided.";
        ExtractedPage page = makePage(1, pageText);
        ClauseSegmentationContext ctx = new ClauseSegmentationContext(
            extractionWithPages(List.of(page)), List.of());

        ClauseSegmentationResult result = provider.segment(ctx);
        assertThat(result.layoutBlocks().size()).isEqualTo(result.clauses().size());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ExtractedPage makePage(int pageNumber, String text) {
        return new ExtractedPage(pageNumber, 595, 842, 0, text, text, 0.8, null, Map.of());
    }

    private ExtractedClause makeClause(String sourceId, String text) {
        return new ExtractedClause(sourceId, null, "C1", text, text, text,
            "PARAGRAPH", 1, 1, List.of(), "hash-" + sourceId, 0, Map.of());
    }

    private DocumentExtractionResult emptyExtraction() {
        return new DocumentExtractionResult(
            null, "test", "1.0", 0, "en", 0.8,
            List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    private DocumentExtractionResult extractionWithPages(List<ExtractedPage> pages) {
        return new DocumentExtractionResult(
            null, "test", "1.0", pages.size(), "en", 0.8,
            pages, List.of(), List.of(), List.of(), Map.of());
    }
}
