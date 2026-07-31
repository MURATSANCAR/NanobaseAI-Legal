package com.nanobase.specai.document.ocr;

import java.util.Map;

public record OcrPreprocessingContext(
    String documentVersionId,
    int pageNumber,
    byte[] imageBytes,
    String mimeType,
    Map<String, Object> policyHints
) {
}
