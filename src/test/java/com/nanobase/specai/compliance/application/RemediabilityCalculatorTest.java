package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nanobase.specai.analysis.domain.LifecycleStage;
import com.nanobase.specai.analysis.domain.Remediability;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class RemediabilityCalculatorTest {
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void hardBlockerWhenLeadTimeExceedsBidWindow() {
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), any(), any(), any()))
            .thenReturn(100);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var calculator = new RemediabilityCalculator(jdbc, clock);
        Remediability result = calculator.calculate(new RemediabilityCalculator.Input(
            UUID.randomUUID(), "CERTIFICATION", "OBTAIN_CERTIFICATE",
            LocalDate.of(2026, 1, 31), LifecycleStage.BID_SUBMISSION, null));
        assertThat(result).isEqualTo(Remediability.HARD_BLOCKER);
    }

    @Test
    void remediableBeforeBidWhenLeadTimeFits() {
        when(jdbc.query(any(String.class), any(ResultSetExtractor.class), any(), any(), any()))
            .thenReturn(20);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var calculator = new RemediabilityCalculator(jdbc, clock);
        Remediability result = calculator.calculate(new RemediabilityCalculator.Input(
            UUID.randomUUID(), "PERSONNEL_COUNT", "HIRE_PERSONNEL",
            LocalDate.of(2026, 2, 15), LifecycleStage.BID_SUBMISSION, null));
        assertThat(result).isEqualTo(Remediability.REMEDIABLE_BEFORE_BID);
    }
}
