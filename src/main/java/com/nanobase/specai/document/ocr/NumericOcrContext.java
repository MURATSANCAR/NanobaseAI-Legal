package com.nanobase.specai.document.ocr;

public record NumericOcrContext(
    String rawText,
    Double characterConfidence,
    String sourceRegionHint
) {
}
