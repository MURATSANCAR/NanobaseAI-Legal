package com.nanobase.specai.document.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.document.integration.ParserRoute.Decision;
import com.nanobase.specai.document.integration.ParserRoute.OcrMode;
import com.nanobase.specai.document.integration.ParserRoute.Provider;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultDocumentParserRouterTest {
    private final DefaultDocumentParserRouter router = new DefaultDocumentParserRouter();

    @Test
    void routesScannedPdfToDoclingWithForcedOcr() {
        ParserRoute route = router.decide(context(
            "application/pdf", ".pdf", true, true, false));
        assertThat(route.provider()).isEqualTo(Provider.DOCLING);
        assertThat(route.ocrMode()).isEqualTo(OcrMode.FORCED);
        assertThat(route.decision()).isEqualTo(Decision.ROUTE);
    }

    @Test
    void routesDocxWithoutOcrAndRetriesUnavailableProvider() {
        ParserRoute docx = router.decide(context(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".docx", false, true, false));
        ParserRoute unavailable = router.decide(context(
            "application/pdf", ".pdf", false, false, false));
        assertThat(docx.ocrMode()).isEqualTo(OcrMode.DISABLED);
        assertThat(unavailable.decision()).isEqualTo(Decision.RETRY);
    }

    @Test
    void openContractsIsOnlySelectedForAnnotationSynchronization() {
        ParserRoute route = router.decide(context(
            "application/pdf", ".pdf", false, true, true));
        assertThat(route.provider()).isEqualTo(Provider.OPENCONTRACTS);
    }

    private DocumentRoutingContext context(
        String mimeType, String extension, boolean scan, boolean docling,
        boolean annotation) {
        return new DocumentRoutingContext(UUID.randomUUID(), mimeType, extension,
            100, 2, scan ? .01 : .9, scan, .1, "tr", docling, true,
            annotation, null);
    }
}
