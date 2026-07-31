package com.nanobase.specai.document.ocr;

import java.util.List;

public record NumericOcrValidationResult(
    boolean ambiguous,
    List<String> issues,
    double confidencePenalty
) {
}
