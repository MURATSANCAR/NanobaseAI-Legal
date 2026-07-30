package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence;
import com.nanobase.specai.compliance.application.ComplianceSemanticRouter.RoutingTrace;
import java.util.List;
import java.util.UUID;

/** In-memory model/deterministic result after EXECUTE (no DB access). */
public record ComplianceExecutionResult(
    boolean success,
    boolean cancelled,
    boolean staleLease,
    String errorCode,
    String errorMessage,
    UUID decisionConceptId,
    String decisionCode,
    JsonNode summary,
    double confidence,
    boolean requiresReview,
    UUID modelRunId,
    List<RankedEvidence> selectedEvidence,
    boolean contradiction,
    RoutingTrace routing,
    String evaluationSource,
    List<String> missingElements,
    boolean ambiguousRequirement,
    String gapHint
) {
    public static ComplianceExecutionResult fromPrecomputed(
        PreparedComplianceTask.PrecomputedOutcome outcome
    ) {
        return new ComplianceExecutionResult(
            true, false, false, null, null,
            outcome.decisionConceptId(), outcome.decisionCode(), outcome.summary(),
            outcome.confidence(), outcome.requiresReview(), outcome.modelRunId(),
            outcome.selectedEvidence(), outcome.contradiction(), outcome.routing(),
            outcome.evaluationSource(), outcome.missingElements(),
            outcome.ambiguousRequirement(), outcome.gapHint());
    }

    public static ComplianceExecutionResult cancelled() {
        return new ComplianceExecutionResult(
            false, true, false, "CANCEL_REQUESTED", "Cancel requested",
            null, null, null, 0, false, null, List.of(), false, null, null,
            List.of(), false, null);
    }

    public static ComplianceExecutionResult failure(String code, String message) {
        return new ComplianceExecutionResult(
            false, false, false, code, message,
            null, null, null, 0, false, null, List.of(), false, null, null,
            List.of(), false, null);
    }
}
