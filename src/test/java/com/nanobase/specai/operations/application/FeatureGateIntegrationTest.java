package com.nanobase.specai.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.shared.observability.PlatformMetrics;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dual feature-gate matrix required for 30 Jul 2026 production readiness.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeatureGateIntegrationTest {
    private static final UUID PILOT_ORG = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_ORG = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID UNKNOWN_ORG = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROJECT = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private Environment environment;
    @Mock
    private ObjectProvider<PlatformMetrics> metricsProvider;
    @Mock
    private PlatformMetrics metrics;

    private FeatureFlagService service;

    @BeforeEach
    void setUp() {
        when(metricsProvider.getIfAvailable()).thenReturn(metrics);
        service = new FeatureFlagService(jdbc, environment, metricsProvider);
    }

    @Test
    void envFalseDbTrue_featureDisabled() {
        allowEnv(false);
        assertThat(service.enabled(PILOT_ORG, PROJECT, TenderIntelligenceFlags.GAP_ANALYSIS))
            .isFalse();
        verify(jdbc, never()).queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any());
        verify(metrics, never()).featureGateDenied(anyString(), anyString());
    }

    @Test
    void envTrueDbFalse_featureDisabled() {
        allowEnv(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any()))
            .thenReturn(false);
        assertThat(service.enabled(PILOT_ORG, PROJECT, TenderIntelligenceFlags.GAP_ANALYSIS))
            .isFalse();
        verify(metrics, never()).featureGateDenied(anyString(), anyString());
    }

    @Test
    void envTrueDbTrue_featureEnabled() {
        allowEnv(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any()))
            .thenReturn(true);
        assertThat(service.enabled(PILOT_ORG, PROJECT, TenderIntelligenceFlags.GAP_ANALYSIS))
            .isTrue();
        verify(metrics, never()).featureGateDenied(anyString(), anyString());
    }

    @Test
    void globalKillSwitchBlocksEvenWhenPilotAssigned() {
        when(environment.getProperty(
            "specai.tender-intelligence.tender-domain-v2-enabled", Boolean.class, false))
            .thenReturn(false);
        assertThat(service.enabled(PILOT_ORG, PROJECT, TenderIntelligenceFlags.TENDER_DOMAIN_V2))
            .isFalse();
        verify(jdbc, never()).queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any());
    }

    @Test
    void pilotOrganizationEnabled() {
        allowEnv(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
            eq(PILOT_ORG), eq(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION),
            eq(PROJECT), eq(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION)))
            .thenReturn(true);
        assertThat(service.enabled(PILOT_ORG, PROJECT,
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION)).isTrue();
    }

    @Test
    void nonPilotOrganizationDisabled() {
        allowEnv(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
            eq(OTHER_ORG), eq(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION),
            eq(PROJECT), eq(TenderIntelligenceFlags.DETERMINISTIC_EVALUATION)))
            .thenReturn(false);
        assertThat(service.enabled(OTHER_ORG, PROJECT,
            TenderIntelligenceFlags.DETERMINISTIC_EVALUATION)).isFalse();
    }

    @Test
    void unknownOrganizationDisabled() {
        allowEnv(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class),
            eq(UNKNOWN_ORG), eq(TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION),
            eq(PROJECT), eq(TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION)))
            .thenReturn(false);
        assertThat(service.enabled(UNKNOWN_ORG, PROJECT,
            TenderIntelligenceFlags.REQUIREMENT_CLASSIFICATION)).isFalse();
    }

    @Test
    void dbAccessFailure_failClosedDisabled() {
        allowEnv(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any()))
            .thenThrow(new DataAccessResourceFailureException("db down"));
        assertThat(service.enabled(PILOT_ORG, PROJECT, TenderIntelligenceFlags.GAP_ANALYSIS))
            .isFalse();
        verify(metrics).featureGateDenied(TenderIntelligenceFlags.GAP_ANALYSIS, "db_error");
    }

    private void allowEnv(boolean allow) {
        when(environment.getProperty(
            "specai.tender-intelligence.gap-analysis-enabled", Boolean.class, false))
            .thenReturn(allow);
        when(environment.getProperty(
            "specai.tender-intelligence.deterministic-evaluation-enabled", Boolean.class, false))
            .thenReturn(allow);
        when(environment.getProperty(
            "specai.tender-intelligence.requirement-classification-enabled", Boolean.class, false))
            .thenReturn(allow);
        when(environment.getProperty(
            "specai.tender-intelligence.tender-domain-v2-enabled", Boolean.class, false))
            .thenReturn(allow);
    }
}
