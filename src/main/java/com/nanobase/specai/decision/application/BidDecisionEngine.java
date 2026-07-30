package com.nanobase.specai.decision.application;

import com.nanobase.specai.analysis.domain.Remediability;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Deterministic bid/no-bid policy. LLM may summarize but must not set the recommendation alone.
 */
@Service
public class BidDecisionEngine {

    public enum Recommendation {
        BID,
        CONDITIONAL_BID,
        NO_BID,
        MANAGEMENT_REVIEW_REQUIRED
    }

    public record DecisionInput(
        int mandatoryTotal,
        int mandatoryCompliant,
        int mandatoryNonCompliant,
        int mandatoryUnknown,
        int unresolvedHardBlockers,
        int remediableBeforeBidGaps,
        int openClarifications,
        int criticalRisks,
        int criticalContractualRisks,
        boolean allMandatoryCompliant,
        boolean financialAdequacy,
        boolean technicalAdequacy
    ) {
    }

    public record DecisionResult(
        Recommendation recommendation,
        List<String> reasons,
        List<String> conditions
    ) {
    }

    public DecisionResult decide(DecisionInput input) {
        List<String> reasons = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (input.unresolvedHardBlockers() > 0) {
            reasons.add("Unresolved hard blockers: " + input.unresolvedHardBlockers());
            if (input.criticalContractualRisks() > 0 || input.criticalRisks() > 0) {
                return new DecisionResult(Recommendation.MANAGEMENT_REVIEW_REQUIRED, reasons,
                    conditions);
            }
            return new DecisionResult(Recommendation.NO_BID, reasons, conditions);
        }

        if (input.mandatoryNonCompliant() > 0 && input.remediableBeforeBidGaps() == 0) {
            reasons.add("Mandatory non-compliant requirements are not remediable before bid");
            return new DecisionResult(Recommendation.NO_BID, reasons, conditions);
        }

        if (input.criticalContractualRisks() > 0) {
            reasons.add("Critical contractual risks require management review");
            return new DecisionResult(Recommendation.MANAGEMENT_REVIEW_REQUIRED, reasons, conditions);
        }

        if (input.mandatoryUnknown() > 0 || input.openClarifications() > 0) {
            reasons.add("Mandatory unknowns or open clarifications remain");
            if (input.openClarifications() > 0) {
                conditions.add("Resolve " + input.openClarifications() + " clarification(s)");
            }
            if (input.mandatoryUnknown() > 0) {
                conditions.add("Clarify " + input.mandatoryUnknown() + " mandatory requirement(s)");
            }
            return new DecisionResult(Recommendation.CONDITIONAL_BID, reasons, conditions);
        }

        if (input.mandatoryNonCompliant() > 0 && input.remediableBeforeBidGaps() > 0) {
            reasons.add("Mandatory gaps are remediable before bid deadline");
            conditions.add("Close " + input.remediableBeforeBidGaps() + " remediable gap(s) before bid");
            return new DecisionResult(Recommendation.CONDITIONAL_BID, reasons, conditions);
        }

        if (input.allMandatoryCompliant()
            && input.criticalRisks() == 0
            && input.financialAdequacy()
            && input.technicalAdequacy()) {
            reasons.add("All mandatory requirements compliant with no critical risks");
            return new DecisionResult(Recommendation.BID, reasons, conditions);
        }

        if (input.allMandatoryCompliant() && input.criticalRisks() > 0) {
            reasons.add("Mandatory compliance met but critical risks remain");
            return new DecisionResult(Recommendation.MANAGEMENT_REVIEW_REQUIRED, reasons, conditions);
        }

        reasons.add("Insufficient certainty for an automatic bid recommendation");
        return new DecisionResult(Recommendation.MANAGEMENT_REVIEW_REQUIRED, reasons, conditions);
    }

    public static boolean isHardBlocker(Remediability remediability) {
        return remediability == Remediability.HARD_BLOCKER;
    }
}
