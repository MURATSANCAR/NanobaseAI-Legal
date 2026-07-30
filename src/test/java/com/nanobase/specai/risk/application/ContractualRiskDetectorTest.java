package com.nanobase.specai.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractualRiskDetectorTest {
    private final ContractualRiskDetector detector = new ContractualRiskDetector();

    @Test
    void detectsUnlimitedLiabilityAndIpAssignment() {
        var risks = detector.detect(
            "Yüklenici sınırsız sorumluluk kabul eder. Tüm fikri mülkiyet hakları devredilir.");
        assertThat(risks).extracting(ContractualRiskDetector.DetectedRisk::riskType)
            .contains("UNLIMITED_LIABILITY", "IP_ASSIGNMENT");
    }
}
