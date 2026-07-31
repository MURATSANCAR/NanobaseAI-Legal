package com.nanobase.specai.document.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultDocumentCapabilityProfilerTest {

    private final DefaultDocumentCapabilityProfiler profiler = new DefaultDocumentCapabilityProfiler();

    @Test
    void profilesNativePdfAsNativeText() {
        DocumentCapabilityProfile profile = profiler.profile(
            UUID.randomUUID(), UUID.randomUUID(),
            "application/pdf", ".pdf", 25, 0.9d, false, 1, 0, 40_000, Map.of("lang", "tr"));
        assertThat(profile.formatConceptCode()).isEqualTo("PDF");
        assertThat(profile.contentModeConceptCode()).isEqualTo("NATIVE_TEXT");
        assertThat(profile.ocrNeedConceptCode()).isEqualTo("OPTIONAL");
    }

    @Test
    void profilesScannedPdfAsScannedImage() {
        DocumentCapabilityProfile profile = profiler.profile(
            UUID.randomUUID(), UUID.randomUUID(),
            "application/pdf", ".pdf", 12, 0.02d, true, 0, 12, 200, Map.of());
        assertThat(profile.contentModeConceptCode()).isEqualTo("SCANNED_IMAGE");
        assertThat(profile.ocrNeedConceptCode()).isEqualTo("REQUIRED");
        assertThat(profile.recommendedOcrProfileCode()).isEqualTo("PRIMARY_OCR");
    }

    @Test
    void profilesDocxAsStructured() {
        DocumentCapabilityProfile profile = profiler.profile(
            UUID.randomUUID(), UUID.randomUUID(),
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".docx", 8, 1.0d, false, 1, 0, 12_000, Map.of());
        assertThat(profile.formatConceptCode()).isEqualTo("DOCX");
        assertThat(profile.contentModeConceptCode()).isEqualTo("DOCX_STRUCTURED");
        assertThat(profile.ocrNeedConceptCode()).isEqualTo("NONE");
    }

    @Test
    void profilesTableHeavyDocs() {
        DocumentCapabilityProfile profile = profiler.profile(
            UUID.randomUUID(), UUID.randomUUID(),
            "application/pdf", ".pdf", 10, 0.8d, false, 8, 0, 20_000, Map.of());
        assertThat(profile.contentModeConceptCode()).isEqualTo("TABLE_DOMINANT");
    }
}
