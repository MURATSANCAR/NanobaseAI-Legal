package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.decision.application.BidDecisionEngine;
import org.junit.jupiter.api.Test;

/**
 * Regression scenarios from the tender-intelligence acceptance set.
 */
class TenderIntelligenceRegressionTest {
    private final NumericRequirementEvaluator numeric = new NumericRequirementEvaluator();
    private final BidDecisionEngine bid = new BidDecisionEngine();

    @Test
    void distanceMissingIsInsufficientNotNonCompliant() {
        var condition = com.nanobase.specai.analysis.domain.RequirementCondition.create(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            "NUMERIC", "DISTANCE",
            com.nanobase.specai.analysis.domain.ConditionOperator.GREATER_THAN_OR_EQUAL,
            null, java.math.BigDecimal.valueOf(350), "km", null, null, 0, true,
            java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(numeric.evaluate(condition, null, null).decision())
            .isEqualTo(NumericRequirementEvaluator.Decision.INSUFFICIENT_INFORMATION);
    }

    @Test
    void distanceBelowThresholdIsNonCompliant() {
        var condition = com.nanobase.specai.analysis.domain.RequirementCondition.create(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            "NUMERIC", "DISTANCE",
            com.nanobase.specai.analysis.domain.ConditionOperator.GREATER_THAN_OR_EQUAL,
            null, java.math.BigDecimal.valueOf(350), "km", null, null, 0, true,
            java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(numeric.evaluate(condition, java.math.BigDecimal.valueOf(120), "km").decision())
            .isEqualTo(NumericRequirementEvaluator.Decision.NON_COMPLIANT);
    }

    @Test
    void distanceAboveThresholdIsCompliant() {
        var condition = com.nanobase.specai.analysis.domain.RequirementCondition.create(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            "NUMERIC", "DISTANCE",
            com.nanobase.specai.analysis.domain.ConditionOperator.GREATER_THAN_OR_EQUAL,
            null, java.math.BigDecimal.valueOf(350), "km", null, null, 0, true,
            java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(numeric.evaluate(condition, java.math.BigDecimal.valueOf(400), "km").decision())
            .isEqualTo(NumericRequirementEvaluator.Decision.COMPLIANT);
    }

    @Test
    void hardBlockerForcesNoBidOrManagementReview() {
        var noBid = bid.decide(new BidDecisionEngine.DecisionInput(
            5, 4, 1, 0, 1, 0, 0, 0, 0, false, true, true));
        assertThat(noBid.recommendation()).isIn(
            BidDecisionEngine.Recommendation.NO_BID,
            BidDecisionEngine.Recommendation.MANAGEMENT_REVIEW_REQUIRED);
    }
}
