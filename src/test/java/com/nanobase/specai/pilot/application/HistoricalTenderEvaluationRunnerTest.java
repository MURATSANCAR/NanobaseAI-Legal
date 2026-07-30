package com.nanobase.specai.pilot.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.compliance.application.NumericRequirementEvaluator;
import com.nanobase.specai.decision.application.BidDecisionEngine;
import org.junit.jupiter.api.Test;

class HistoricalTenderEvaluationRunnerTest {
    @Test
    void acceptanceSetHasZeroFalseCompliant() {
        var runner = new HistoricalTenderEvaluationRunner(new NumericRequirementEvaluator(),
            new BidDecisionEngine());
        var report = runner.runBuiltInAcceptanceSet();
        assertThat(report.falseCompliantCount()).isZero();
        assertThat(report.caseCount()).isGreaterThanOrEqualTo(8);
        assertThat(report.pass()).isTrue();
    }
}
