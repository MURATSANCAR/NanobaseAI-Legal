package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequirementCapabilityMatcherTest {
    @Mock
    private ClosedWorldValidator closedWorldValidator;

    @Test
    void isoPartialMatchWithoutClosedWorldIsUnknown() {
        when(closedWorldValidator.hasActiveDeclaration(any(), any(), anyString()))
            .thenReturn(false);
        when(closedWorldValidator.hasActiveDeclaration(any(), any(), isNull()))
            .thenReturn(false);
        var matcher = new RequirementCapabilityMatcher(new NumericRequirementEvaluator(),
            closedWorldValidator);
        UUID org = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        var conditions = List.of(
            condition("CERTIFICATE", "ISO 27001", ConditionOperator.EXISTS),
            condition("CERTIFICATE", "ISO 22301", ConditionOperator.EXISTS),
            condition("CERTIFICATE", "PCI DSS", ConditionOperator.EXISTS));
        var capabilities = List.of(capability("CERTIFICATION", "iso 27001", "ISO 27001"));
        var result = matcher.match(org, project, conditions, capabilities, LocalDate.now());
        assertThat(result.status())
            .isEqualTo(RequirementCapabilityMatcher.MatchStatus.PARTIALLY_MATCHED);
    }

    @Test
    void missingCertificatesWithClosedWorldAreNotMatched() {
        when(closedWorldValidator.hasActiveDeclaration(any(), any(), any()))
            .thenReturn(true);
        var matcher = new RequirementCapabilityMatcher(new NumericRequirementEvaluator(),
            closedWorldValidator);
        UUID org = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        var conditions = List.of(
            condition("CERTIFICATE", "ISO 27001", ConditionOperator.EXISTS),
            condition("CERTIFICATE", "ISO 22301", ConditionOperator.EXISTS));
        var capabilities = List.of(capability("CERTIFICATION", "iso 27001", "ISO 27001"));
        var result = matcher.match(org, project, conditions, capabilities, LocalDate.now());
        assertThat(result.status())
            .isIn(RequirementCapabilityMatcher.MatchStatus.PARTIALLY_MATCHED,
                RequirementCapabilityMatcher.MatchStatus.NOT_MATCHED);
        assertThat(result.missingConditionIds()).isNotEmpty();
    }

    private RequirementCondition condition(String field, String expected,
                                           ConditionOperator operator) {
        return RequirementCondition.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "CERTIFICATE", field, operator, expected, null, null, null, null, 0, true,
            Instant.parse("2026-01-01T00:00:00Z"));
    }

    private RequirementCapabilityMatcher.CapabilitySnapshot capability(String type, String name,
                                                                       String text) {
        return new RequirementCapabilityMatcher.CapabilitySnapshot(
            UUID.randomUUID(), type, name, text, null, null, null, null,
            LocalDate.of(2027, 4, 30), "datacenter", "ACTIVE");
    }
}
