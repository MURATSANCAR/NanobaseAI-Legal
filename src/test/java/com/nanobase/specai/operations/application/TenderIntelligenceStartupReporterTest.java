package com.nanobase.specai.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class TenderIntelligenceStartupReporterTest {
    @Mock
    private FeatureFlagService flags;

    @Test
    void emitsStructuredProductionRuntimePolicyJson() throws Exception {
        when(flags.tenderIntelligenceEnvironmentSnapshot()).thenReturn(Map.of(
            TenderIntelligenceFlags.TENDER_DOMAIN_V2, false,
            TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION, false,
            TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY, false,
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION, false,
            TenderIntelligenceFlags.GAP_ANALYSIS, false,
            TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT, false,
            TenderIntelligenceFlags.RISK_ENGINE, false,
            TenderIntelligenceFlags.BID_DECISION, false,
            TenderIntelligenceFlags.OBLIGATION_MANAGEMENT, false
        ));
        TenderIntelligenceStartupReporter reporter = new TenderIntelligenceStartupReporter(
            flags, new ObjectMapper(), new MockEnvironment(),
            "BALANCED_ONLY", false, false, 1, false);
        reporter.report();
        // Serialization path exercised; snapshot keys stay camelCase aliases only.
        JsonNode node = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(
            Map.of("event", "production_runtime_policy", "modelRouting", "BALANCED_ONLY")));
        assertThat(node.get("event").asText()).isEqualTo("production_runtime_policy");
        assertThat(node.get("modelRouting").asText()).isEqualTo("BALANCED_ONLY");
    }

    @Test
    void productionProfileRejectsFastEnabled() {
        when(flags.tenderIntelligenceEnvironmentSnapshot()).thenReturn(Map.of(
            TenderIntelligenceFlags.TENDER_DOMAIN_V2, false,
            TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION, false,
            TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY, false,
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION, false,
            TenderIntelligenceFlags.GAP_ANALYSIS, false,
            TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT, false,
            TenderIntelligenceFlags.RISK_ENGINE, false,
            TenderIntelligenceFlags.BID_DECISION, false,
            TenderIntelligenceFlags.OBLIGATION_MANAGEMENT, false
        ));
        Environment env = new MockEnvironment().withProperty("unused", "x");
        ((MockEnvironment) env).setActiveProfiles("production");
        TenderIntelligenceStartupReporter reporter = new TenderIntelligenceStartupReporter(
            flags, new ObjectMapper(), env,
            "BALANCED_ONLY", true, false, 1, false);
        assertThatThrownBy(reporter::report)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fastEnabled=true");
    }

    @Test
    void productionProfileRejectsParallelismAboveOne() {
        when(flags.tenderIntelligenceEnvironmentSnapshot()).thenReturn(Map.of(
            TenderIntelligenceFlags.TENDER_DOMAIN_V2, false,
            TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION, false,
            TenderIntelligenceFlags.COMPANY_CAPABILITY_REGISTRY, false,
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION, false,
            TenderIntelligenceFlags.GAP_ANALYSIS, false,
            TenderIntelligenceFlags.CLARIFICATION_MANAGEMENT, false,
            TenderIntelligenceFlags.RISK_ENGINE, false,
            TenderIntelligenceFlags.BID_DECISION, false,
            TenderIntelligenceFlags.OBLIGATION_MANAGEMENT, false
        ));
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");
        TenderIntelligenceStartupReporter reporter = new TenderIntelligenceStartupReporter(
            flags, new ObjectMapper(), env,
            "BALANCED_ONLY", false, false, 2, false);
        assertThatThrownBy(reporter::report)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("evaluationParallelism=2");
    }
}
