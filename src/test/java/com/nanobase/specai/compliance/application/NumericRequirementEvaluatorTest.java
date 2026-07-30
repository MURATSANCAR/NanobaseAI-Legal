package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.analysis.domain.ConditionOperator;
import com.nanobase.specai.analysis.domain.RequirementCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NumericRequirementEvaluatorTest {
    private final NumericRequirementEvaluator evaluator = new NumericRequirementEvaluator();

    @Test
    void compliantWhenEvidenceExceedsThreshold() {
        var result = evaluator.evaluate(condition(350, "km", ConditionOperator.GREATER_THAN_OR_EQUAL),
            BigDecimal.valueOf(400), "km");
        assertThat(result.decision()).isEqualTo(NumericRequirementEvaluator.Decision.COMPLIANT);
    }

    @Test
    void nonCompliantWhenEvidenceBelowThreshold() {
        var result = evaluator.evaluate(condition(350, "km", ConditionOperator.GREATER_THAN_OR_EQUAL),
            BigDecimal.valueOf(120), "km");
        assertThat(result.decision()).isEqualTo(NumericRequirementEvaluator.Decision.NON_COMPLIANT);
    }

    @Test
    void insufficientWhenEvidenceMissing() {
        var result = evaluator.evaluate(condition(350, "km", ConditionOperator.GREATER_THAN_OR_EQUAL),
            null, null);
        assertThat(result.decision())
            .isEqualTo(NumericRequirementEvaluator.Decision.INSUFFICIENT_INFORMATION);
    }

    @Test
    void convertsKilometersAndMeters() {
        var result = evaluator.evaluate(condition(350, "km", ConditionOperator.GREATER_THAN_OR_EQUAL),
            BigDecimal.valueOf(400_000), "m");
        assertThat(result.decision()).isEqualTo(NumericRequirementEvaluator.Decision.COMPLIANT);
    }

    @Test
    void insufficientWhenUnitsAmbiguous() {
        var result = evaluator.evaluate(condition(350, "km", ConditionOperator.GREATER_THAN_OR_EQUAL),
            BigDecimal.valueOf(400), "widgets");
        assertThat(result.decision())
            .isEqualTo(NumericRequirementEvaluator.Decision.INSUFFICIENT_INFORMATION);
        assertThat(result.reasonCode()).isEqualTo("UNIT_AMBIGUOUS");
    }

    private RequirementCondition condition(int value, String unit, ConditionOperator operator) {
        return RequirementCondition.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "NUMERIC", "DISTANCE", operator, null, BigDecimal.valueOf(value), unit, null, null,
            0, true, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
