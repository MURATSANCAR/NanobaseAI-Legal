package com.nanobase.specai.companyfit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.companyfit.domain.CompanyFitModels.CapabilityKind;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.CompanyCapability;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.FitStatus;
import com.nanobase.specai.companyfit.domain.CompanyFitModels.OverallFit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanyFitEngineTest {
    private final CompanyCapabilityExtractor extractor = new CompanyCapabilityExtractor();
    private final CompanyFitEngine engine = new CompanyFitEngine();

    @Test
    void extractsIsoAndDimm() {
        var caps = extractor.extract("org1", "d1",
            "Şirketimiz ISO 27001 bilgi güvenliği belgesine sahiptir. "
                + "Geçerlilik: 31.12.2027. Sunucu konfigürasyonunda en az 16 DIMM yuvası desteklenir.");
        assertThat(caps.stream().map(CompanyCapability::canonicalKey))
            .contains("iso_27001", "min_dimm_slots");
    }

    @Test
    void fitMetOnMatchingCapability() {
        var caps = List.of(new CompanyCapability(
            "c1", "org1", CapabilityKind.PRODUCT_SPEC, "min_dimm_slots", "DIMM 16",
            Map.of("value", 16), null, null, "UNKNOWN", "d1", "16 DIMM", 0.9));
        var row = engine.matchRequirement(Map.of(
            "requirementId", "r1",
            "category", "TECHNICAL",
            "priority", "MUST",
            "text", "Sunucu üzerinde en az 16 adet DIMM yuvası bulunmalıdır.",
            "measurement", "dimm",
            "operator", ">=",
            "acceptanceThreshold", "16"
        ), caps);
        assertThat(row.status()).isEqualTo(FitStatus.MET);
    }

    @Test
    void fitMissingWithoutCaps() {
        var row = engine.matchRequirement(Map.of(
            "requirementId", "r2",
            "category", "COMPLIANCE",
            "priority", "MUST",
            "text", "ISO 27001 belgesi sunulacaktır."
        ), List.of());
        assertThat(row.status()).isEqualTo(FitStatus.MISSING);
    }

    @Test
    void overallConditional() {
        var caps = List.of(new CompanyCapability(
            "c1", "org1", CapabilityKind.COMPLIANCE_CERT, "iso_27001", "ISO 27001",
            Map.of(), null, null, "UNKNOWN", "d1", null, 0.95));
        var report = engine.buildReport("org1", "tender1", List.of(
            Map.of("requirementId", "r1", "category", "COMPLIANCE", "priority", "MUST",
                "text", "ISO 27001 zorunludur."),
            Map.of("requirementId", "r2", "category", "TECHNICAL", "priority", "MUST",
                "text", "En az 16 DIMM yuvası bulunmalıdır.",
                "measurement", "dimm", "operator", ">=", "acceptanceThreshold", "16")
        ), caps);
        assertThat(report.mustTotal()).isEqualTo(2);
        assertThat(report.mustMet()).isEqualTo(1);
        assertThat(report.overall()).isIn(OverallFit.CONDITIONAL, OverallFit.NOT_FIT);
    }

    @Test
    void insufficientDataWhenNoCapabilities() {
        var report = engine.buildReport("org1", "t1", List.of(
            Map.of("requirementId", "r1", "category", "COMPLIANCE", "priority", "MUST",
                "text", "ISO 27001")
        ), List.of());
        assertThat(report.overall()).isEqualTo(OverallFit.INSUFFICIENT_DATA);
    }
}
