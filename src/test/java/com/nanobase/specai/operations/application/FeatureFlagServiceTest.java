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
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeatureFlagServiceTest {
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
    void envKillSwitchBlocksDbLookup() {
        when(environment.getProperty(
            "specai.tender-intelligence.tender-domain-v2-enabled", Boolean.class, false))
            .thenReturn(false);

        boolean enabled = service.enabled(
            UUID.randomUUID(), UUID.randomUUID(), TenderIntelligenceFlags.TENDER_DOMAIN_V2);

        assertThat(enabled).isFalse();
        verify(jdbc, never()).queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any());
    }

    @Test
    void envAllowPlusDbTrueEnablesFeature() {
        when(environment.getProperty(
            "specai.tender-intelligence.gap-analysis-enabled", Boolean.class, false))
            .thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any()))
            .thenReturn(true);

        boolean enabled = service.enabled(
            UUID.randomUUID(), UUID.randomUUID(), TenderIntelligenceFlags.GAP_ANALYSIS);

        assertThat(enabled).isTrue();
    }

    @Test
    void nonIntelligenceFlagsRemainDbOnly() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any()))
            .thenReturn(true);

        boolean enabled = service.enabled(UUID.randomUUID(), UUID.randomUUID(), "SOME_OTHER_FLAG");

        assertThat(enabled).isTrue();
    }

    @Test
    void dbFailureFailsClosed() {
        when(environment.getProperty(
            "specai.tender-intelligence.gap-analysis-enabled", Boolean.class, false))
            .thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(), any(), any(), any()))
            .thenThrow(new QueryTimeoutException("timeout"));

        assertThat(service.enabled(
            UUID.randomUUID(), UUID.randomUUID(), TenderIntelligenceFlags.GAP_ANALYSIS))
            .isFalse();
        verify(metrics).featureGateDenied(TenderIntelligenceFlags.GAP_ANALYSIS, "db_error");
    }
}
