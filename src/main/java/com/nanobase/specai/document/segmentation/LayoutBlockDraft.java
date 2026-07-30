package com.nanobase.specai.document.segmentation;

public record LayoutBlockDraft(
    int blockIndex,
    int pageNumber,
    String blockTypeCode,
    String textContent,
    String normalizedText,
    int readingOrder,
    double confidence
) {
}
