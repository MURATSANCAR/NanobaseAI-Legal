package com.nanobase.specai.document.capability;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultDocumentProcessingRouterTest {

    private final DefaultDocumentProcessingRouter router = new DefaultDocumentProcessingRouter();

    @Test
    void scannedProfileSelectsPrimaryOcrAndRetryHints() {
        DocumentCapabilityProfile profile = sample("PDF", "SCANNED_IMAGE", "REQUIRED");
        DocumentProcessingPlan plan = router.resolve(
            profile, new DocumentProcessingPolicyVersion("default", "1", Map.of()));
        assertThat(plan.ocrProviderCode()).isEqualTo("PRIMARY_OCR");
        assertThat(plan.clauseProviderChain()).contains("TEXT_HIERARCHY");
        assertThat(plan.retryPolicy().get("preferPageScopedRetry")).isEqualTo(true);
    }

    @Test
    void docxProfileDisablesOcrAndUsesDocxLayout() {
        DocumentCapabilityProfile profile = sample("DOCX", "DOCX_STRUCTURED", "NONE");
        DocumentProcessingPlan plan = router.resolve(
            profile, new DocumentProcessingPolicyVersion("default", "1", Map.of()));
        assertThat(plan.layoutProviderCode()).isEqualTo("DOCX_STRUCTURE");
        assertThat(plan.ocrProviderCode()).isEqualTo("DISABLED");
    }

    @Test
    void policyCanOverrideClauseChainWithoutHardcodingFormatsInCaller() {
        DocumentCapabilityProfile profile = sample("PDF", "NATIVE_TEXT", "OPTIONAL");
        DocumentProcessingPlan plan = router.resolve(
            profile,
            new DocumentProcessingPolicyVersion("custom", "1", Map.of(
                "clauseProviderChain", List.of("CUSTOM_A", "CUSTOM_B"))));
        assertThat(plan.clauseProviderChain()).containsExactly("CUSTOM_A", "CUSTOM_B");
    }

    private static DocumentCapabilityProfile sample(String format, String mode, String ocr) {
        return new DocumentCapabilityProfile(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            format, mode, "MEDIUM", ocr,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.valueOf(0.5),
            Map.of(), 10, 1000, null, null, null, "BALANCED", Instant.now());
    }
}
