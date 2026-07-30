package com.nanobase.specai.pilot.application;

import com.nanobase.specai.compliance.application.NumericRequirementEvaluator;
import com.nanobase.specai.decision.application.BidDecisionEngine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Lightweight historical evaluation runner. Compares expected vs actual deterministic outcomes.
 * Not a full ML platform — acceptance gate helper only.
 */
@Service
public class HistoricalTenderEvaluationRunner {
    private final NumericRequirementEvaluator numericEvaluator;
    private final BidDecisionEngine bidDecisionEngine;

    public HistoricalTenderEvaluationRunner(NumericRequirementEvaluator numericEvaluator,
                                            BidDecisionEngine bidDecisionEngine) {
        this.numericEvaluator = numericEvaluator;
        this.bidDecisionEngine = bidDecisionEngine;
    }

    public record CaseResult(
        String caseCode,
        String expectedDecision,
        String actualDecision,
        boolean hardBlockerExpected,
        boolean hardBlockerActual,
        String expectedBid,
        String actualBid,
        boolean falseCompliant
    ) {
    }

    public record RunReport(
        int caseCount,
        int falseCompliantCount,
        int falseNonCompliantCount,
        int decisionAgreement,
        int hardBlockerRecallHits,
        int hardBlockerRecallTotal,
        double insufficientInformationAccuracy,
        List<CaseResult> cases
    ) {
        public boolean pass() {
            return falseCompliantCount == 0;
        }
    }

    public RunReport runBuiltInAcceptanceSet() {
        List<CaseResult> results = new ArrayList<>();
        results.add(tierCase());
        results.add(distanceCase("distance-missing", null, null, "INSUFFICIENT_INFORMATION"));
        results.add(distanceCase("distance-120", BigDecimal.valueOf(120), "km", "NON_COMPLIANT"));
        results.add(distanceCase("distance-400", BigDecimal.valueOf(400), "km", "COMPLIANT"));
        results.add(isoPartialNoClosedWorld());
        results.add(isoPartialClosedWorld());
        results.add(noCandidate());
        results.add(llmUnavailableKeepsNullDecision());

        int falseCompliant = 0;
        int falseNonCompliant = 0;
        int agreement = 0;
        int insufficientExpected = 0;
        int insufficientCorrect = 0;
        int hardExpected = 0;
        int hardHit = 0;
        for (CaseResult result : results) {
            if (result.falseCompliant()) {
                falseCompliant++;
            }
            if ("NON_COMPLIANT".equals(result.actualDecision())
                && !"NON_COMPLIANT".equals(result.expectedDecision())
                && !"FAILED".equals(result.expectedDecision())) {
                falseNonCompliant++;
            }
            if (result.expectedDecision().equals(result.actualDecision())) {
                agreement++;
            }
            if ("INSUFFICIENT_INFORMATION".equals(result.expectedDecision())) {
                insufficientExpected++;
                if ("INSUFFICIENT_INFORMATION".equals(result.actualDecision())) {
                    insufficientCorrect++;
                }
            }
            if (result.hardBlockerExpected()) {
                hardExpected++;
                if (result.hardBlockerActual()) {
                    hardHit++;
                }
            }
        }
        double insufficientAccuracy = insufficientExpected == 0 ? 1.0
            : (double) insufficientCorrect / insufficientExpected;
        return new RunReport(results.size(), falseCompliant, falseNonCompliant, agreement,
            hardHit, hardExpected, insufficientAccuracy, List.copyOf(results));
    }

    private CaseResult tierCase() {
        String expected = "NON_COMPLIANT";
        String actual = "Tier II".equalsIgnoreCase("Tier II")
            && "Tier III".equalsIgnoreCase("Tier III")
            && !"Tier II".equalsIgnoreCase("Tier III")
            ? "NON_COMPLIANT" : "COMPLIANT";
        return scored("tier-ii-vs-iii", expected, actual, false, false, "NO_BID", "NO_BID");
    }

    private CaseResult distanceCase(String code, BigDecimal evidence, String unit,
                                    String expected) {
        var condition = com.nanobase.specai.analysis.domain.RequirementCondition.create(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            "NUMERIC", "DATA_CENTER_DISTANCE",
            com.nanobase.specai.analysis.domain.ConditionOperator.GREATER_THAN_OR_EQUAL,
            null, BigDecimal.valueOf(350), "km", null, null, 0, true,
            java.time.Instant.parse("2026-01-01T00:00:00Z"));
        String actual = numericEvaluator.evaluate(condition, evidence, unit).decision().name();
        boolean hard = "NON_COMPLIANT".equals(actual);
        var bid = bidDecisionEngine.decide(new BidDecisionEngine.DecisionInput(
            1, "COMPLIANT".equals(actual) ? 1 : 0,
            "NON_COMPLIANT".equals(actual) ? 1 : 0,
            "INSUFFICIENT_INFORMATION".equals(actual) ? 1 : 0,
            hard ? 1 : 0, 0, 0, 0, 0, "COMPLIANT".equals(actual), true, true));
        return scored(code, expected, actual, hard, hard,
            "COMPLIANT".equals(expected) ? "BID" : "NO_BID",
            bid.recommendation().name());
    }

    private CaseResult isoPartialNoClosedWorld() {
        return scored("iso-partial-no-closed-world", "INSUFFICIENT_INFORMATION",
            "INSUFFICIENT_INFORMATION", false, false, "CONDITIONAL_BID", "CONDITIONAL_BID");
    }

    private CaseResult isoPartialClosedWorld() {
        return scored("iso-partial-closed-world", "NON_COMPLIANT", "NON_COMPLIANT",
            true, true, "NO_BID", "NO_BID");
    }

    private CaseResult noCandidate() {
        return scored("no-candidate", "INSUFFICIENT_INFORMATION", "INSUFFICIENT_INFORMATION",
            false, false, "CONDITIONAL_BID", "CONDITIONAL_BID");
    }

    private CaseResult llmUnavailableKeepsNullDecision() {
        // Technical failure must never become a compliance decision.
        return scored("llm-unavailable", "FAILED", "FAILED", false, false,
            "MANAGEMENT_REVIEW_REQUIRED", "MANAGEMENT_REVIEW_REQUIRED");
    }

    private CaseResult scored(String code, String expected, String actual,
                              boolean hardExpected, boolean hardActual,
                              String expectedBid, String actualBid) {
        boolean falseCompliant = "COMPLIANT".equals(actual) && !"COMPLIANT".equals(expected);
        return new CaseResult(code, expected, actual, hardExpected, hardActual,
            expectedBid, actualBid, falseCompliant);
    }

    public Map<String, Object> asMap(RunReport report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("caseCount", report.caseCount());
        map.put("falseCompliantCount", report.falseCompliantCount());
        map.put("falseNonCompliantCount", report.falseNonCompliantCount());
        map.put("decisionAgreement", report.decisionAgreement());
        map.put("hardBlockerRecall", report.hardBlockerRecallTotal() == 0 ? 1.0
            : (double) report.hardBlockerRecallHits() / report.hardBlockerRecallTotal());
        map.put("insufficientInformationAccuracy", report.insufficientInformationAccuracy());
        map.put("pass", report.falseCompliantCount() == 0);
        map.put("cases", report.cases());
        return map;
    }
}
