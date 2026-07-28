package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowSimulationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkflowContracts {
    private WorkflowContracts() {
    }

    public record CreateWorkflowRequest(
        @NotBlank String workflowCode,
        @NotBlank String name,
        String description,
        @NotBlank String scope,
        UUID entityTypeConceptId
    ) {
    }

    public record WorkflowDefinitionResponse(
        UUID id,
        String workflowCode,
        String name,
        String description,
        String scope,
        UUID entityTypeConceptId,
        UUID activeVersionId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CreateWorkflowVersionRequest(
        JsonNode configuration,
        @NotEmpty List<@Valid NodeDraft> nodes,
        @NotNull List<@Valid TransitionDraft> transitions
    ) {
    }

    public record NodeDraft(
        @NotBlank String nodeCode,
        @NotNull UUID nodeTypeConceptId,
        @NotBlank String name,
        String description,
        JsonNode configuration,
        int sortOrder
    ) {
    }

    public record TransitionDraft(
        @NotBlank String sourceNodeCode,
        @NotBlank String targetNodeCode,
        @NotNull UUID transitionConceptId,
        JsonNode condition,
        UUID authorizationPolicyId,
        JsonNode actionConfiguration,
        int priority
    ) {
    }

    public record WorkflowNodeResponse(
        UUID id,
        String nodeCode,
        UUID nodeTypeConceptId,
        String nodeTypeConceptCode,
        String name,
        String description,
        JsonNode configuration,
        int sortOrder
    ) {
    }

    public record WorkflowTransitionResponse(
        UUID id,
        UUID sourceNodeId,
        UUID targetNodeId,
        UUID transitionConceptId,
        JsonNode condition,
        UUID authorizationPolicyId,
        JsonNode actionConfiguration,
        int priority
    ) {
    }

    public record WorkflowVersionResponse(
        UUID id,
        UUID workflowDefinitionId,
        int versionNumber,
        JsonNode configuration,
        String status,
        String approvedBy,
        Instant approvedAt,
        Instant createdAt,
        List<WorkflowNodeResponse> nodes,
        List<WorkflowTransitionResponse> transitions
    ) {
    }

    public record SimulationRequest(
        Map<String, Object> variables,
        List<String> authorizedNodeCodes,
        Integer maximumVisitsPerNode
    ) {
    }

    public record SimulationResponse(UUID runId, WorkflowSimulationResult result) {
    }

    public record StartWorkflowRequest(
        @NotNull UUID workflowDefinitionId,
        UUID projectId,
        @NotBlank String subjectEntityType,
        @NotNull UUID subjectEntityId,
        JsonNode contextSnapshot
    ) {
    }

    public record WorkflowInstanceResponse(
        UUID id,
        UUID workflowDefinitionId,
        UUID workflowVersionId,
        UUID projectId,
        String subjectEntityType,
        UUID subjectEntityId,
        UUID statusConceptId,
        String statusConceptCode,
        JsonNode currentState,
        JsonNode contextSnapshot,
        String startedBy,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        long version
    ) {
    }

    public record WorkflowHistoryResponse(
        List<Map<String, Object>> executions,
        List<Map<String, Object>> transitions
    ) {
    }
}
