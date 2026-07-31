package com.nanobase.specai.document.ocr;

import java.util.List;
import java.util.Map;

public record OcrPreprocessingResult(
    byte[] processedImageBytes,
    String mimeType,
    List<String> operationsApplied,
    Map<String, Object> metadata
) {
}
