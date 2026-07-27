package com.nanobase.specai.document.integration;

public record ParserRoute(
    Provider provider,
    OcrMode ocrMode,
    Decision decision,
    String reason
) {
    public enum Provider {
        DOCLING, OPENCONTRACTS, NONE
    }

    public enum OcrMode {
        DISABLED, AUTO, FORCED
    }

    public enum Decision {
        ROUTE, RETRY, MANUAL_REVIEW_REQUIRED
    }
}
