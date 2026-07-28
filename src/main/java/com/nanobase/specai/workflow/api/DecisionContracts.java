package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DecisionContracts {
    private DecisionContracts() {
    }

    public record CreateDecisionSupportRequest(
        UUID workflowInstanceId,
        @NotNull UUID decisionPolicyVersionId,
        UUID dataSnapshotId
    ) {
    }

    public record ExecutiveDecisionRequest(
        @NotNull UUID decisionConceptId,
        @NotBlank String decisionText,
        JsonNode conditions
    ) {
    }

    public record DecisionSupportResponse(
        UUID id,
        UUID projectId,
        UUID workflowInstanceId,
        String caseCode,
        UUID decisionPolicyVersionId,
        UUID dataSnapshotId,
        UUID recommendedDecisionConceptId,
        String recommendedDecisionConceptCode,
        UUID finalDecisionConceptId,
        String finalDecisionConceptCode,
        double confidence,
        UUID statusConceptId,
        String statusConceptCode,
        JsonNode explanation,
        List<Map<String, Object>> factors,
        List<Map<String, Object>> executiveDecisions
    ) {
    }

    public record FinalizeProjectRequest(
        UUID workflowInstanceId,
        @NotNull UUID finalizationPolicyVersionId,
        @NotNull UUID decisionSupportCaseId,
        @NotNull UUID executiveDecisionId,
        @NotNull UUID finalReportArtifactId,
        @NotNull UUID targetStatusConceptId
    ) {
    }

    public record ReopenProjectRequest(
        @NotBlank String reason,
        @NotNull UUID targetStatusConceptId
    ) {
    }
}
