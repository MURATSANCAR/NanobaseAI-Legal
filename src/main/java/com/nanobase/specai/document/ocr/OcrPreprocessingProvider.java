package com.nanobase.specai.document.ocr;

public interface OcrPreprocessingProvider {
    String providerCode();

    OcrPreprocessingResult process(OcrPreprocessingContext context);
}
