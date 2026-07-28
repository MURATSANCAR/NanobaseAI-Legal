package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.integration.ParserRoute.Decision;
import com.nanobase.specai.document.integration.ParserRoute.OcrMode;
import com.nanobase.specai.document.integration.ParserRoute.Provider;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DefaultDocumentParserRouter implements DocumentParserRouter {
    private static final String PDF = "application/pdf";
    private static final String DOCX =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public ParserRoute decide(DocumentRoutingContext context) {
        String mimeType = context.mimeType() == null ? "" :
            context.mimeType().toLowerCase(Locale.ROOT);
        String extension = context.fileExtension() == null ? "" :
            context.fileExtension().toLowerCase(Locale.ROOT);
        boolean supported = (PDF.equals(mimeType) && ".pdf".equals(extension))
            || (DOCX.equals(mimeType) && ".docx".equals(extension));

        if (context.annotationSynchronizationRequested()) {
            if (context.openContractsAvailable()) {
                return new ParserRoute(Provider.OPENCONTRACTS, OcrMode.AUTO, Decision.ROUTE,
                    "Annotation/corpus synchronization was requested");
            }
            return new ParserRoute(Provider.OPENCONTRACTS, OcrMode.AUTO, Decision.RETRY,
                "OpenContracts is temporarily unavailable");
        }
        if (!supported) {
            return new ParserRoute(Provider.NONE, OcrMode.DISABLED,
                Decision.MANUAL_REVIEW_REQUIRED,
                "The file type cannot be processed safely");
        }
        // Prefer integrated OpenContracts facade when enabled; Docling remains fallback.
        if (context.openContractsAvailable()) {
            boolean requiresOcr = context.scanLikely()
                || context.digitalTextRatio() != null && context.digitalTextRatio() < 0.10d;
            return new ParserRoute(Provider.OPENCONTRACTS,
                DOCX.equals(mimeType) ? OcrMode.DISABLED
                    : (requiresOcr ? OcrMode.FORCED : OcrMode.AUTO),
                Decision.ROUTE,
                "OpenContracts facade is enabled for PDF/DOCX");
        }
        if (!context.doclingAvailable()) {
            return new ParserRoute(Provider.DOCLING, OcrMode.AUTO, Decision.RETRY,
                "Docling is temporarily unavailable");
        }
        if (DOCX.equals(mimeType)) {
            return new ParserRoute(Provider.DOCLING, OcrMode.DISABLED, Decision.ROUTE,
                "DOCX is routed to Docling");
        }
        boolean requiresOcr = context.scanLikely()
            || context.digitalTextRatio() != null && context.digitalTextRatio() < 0.10d;
        return new ParserRoute(Provider.DOCLING,
            requiresOcr ? OcrMode.FORCED : OcrMode.AUTO,
            Decision.ROUTE,
            requiresOcr ? "Scanned PDF signals require OCR" : "Digital PDF is routed to Docling");
    }
}
