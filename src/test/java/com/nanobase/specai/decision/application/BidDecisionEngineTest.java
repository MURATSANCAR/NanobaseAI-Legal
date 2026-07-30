package com.nanobase.specai.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BidDecisionEngineTest {
    private final BidDecisionEngine engine = new BidDecisionEngine();

    @Test
    void noBidOnUnresolvedHardBlocker() {
        var result = engine.decide(new BidDecisionEngine.DecisionInput(
            10, 8, 1, 0, 1, 0, 0, 0, 0, false, true, true));
        assertThat(result.recommendation()).isEqualTo(BidDecisionEngine.Recommendation.NO_BID);
    }

    @Test
    void managementReviewOnCriticalContractRisk() {
        var result = engine.decide(new BidDecisionEngine.DecisionInput(
            10, 10, 0, 0, 0, 0, 0, 0, 1, true, true, true));
        assertThat(result.recommendation())
            .isEqualTo(BidDecisionEngine.Recommendation.MANAGEMENT_REVIEW_REQUIRED);
    }

    @Test
    void conditionalBidOnMandatoryUnknown() {
        var result = engine.decide(new BidDecisionEngine.DecisionInput(
            10, 8, 0, 2, 0, 0, 1, 0, 0, false, true, true));
        assertThat(result.recommendation())
            .isEqualTo(BidDecisionEngine.Recommendation.CONDITIONAL_BID);
    }

    @Test
    void bidWhenAllMandatoryCompliantAndNoCriticalRisk() {
        var result = engine.decide(new BidDecisionEngine.DecisionInput(
            10, 10, 0, 0, 0, 0, 0, 0, 0, true, true, true));
        assertThat(result.recommendation()).isEqualTo(BidDecisionEngine.Recommendation.BID);
    }

    @Test
    void noBidWhenNonCompliantAndNotRemediableBeforeBid() {
        var result = engine.decide(new BidDecisionEngine.DecisionInput(
            10, 7, 3, 0, 0, 0, 0, 0, 0, false, true, true));
        assertThat(result.recommendation()).isEqualTo(BidDecisionEngine.Recommendation.NO_BID);
    }
}
