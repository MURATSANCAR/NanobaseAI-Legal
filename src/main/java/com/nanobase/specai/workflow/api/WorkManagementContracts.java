package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkManagementContracts {
    private WorkManagementContracts() {
    }

    public record TaskResponse(
        UUID id,
        UUID projectId,
        UUID workflowInstanceId,
        UUID workflowExecutionId,
        String taskCode,
        UUID taskTypeConceptId,
        String taskTypeConceptCode,
        String subjectEntityType,
        UUID subjectEntityId,
        String title,
        String description,
        UUID priorityConceptId,
        String priorityConceptCode,
        UUID statusConceptId,
        String statusConceptCode,
        String assignedUserId,
        String assignedGroupId,
        Instant dueAt,
        Instant startedAt,
        Instant completedAt,
        long version,
        List<Map<String, Object>> comments
    ) {
    }

    public record AssignTaskRequest(String assignedUserId, String assignedGroupId) {
    }

    public record TaskStatusRequest(@NotNull UUID targetStatusConceptId) {
    }

    public record AddCommentRequest(
        @NotBlank String commentText,
        @NotNull UUID visibilityConceptId
    ) {
    }

    public record EscalateTaskRequest(
        @NotNull UUID escalationPolicyVersionId,
        @NotNull UUID levelConceptId,
        @NotNull UUID triggerReasonConceptId,
        String targetUserId,
        String targetGroupId
    ) {
    }

    public record ApprovalDecisionRequest(
        @NotNull UUID approvalStepId,
        @NotNull UUID decisionConceptId,
        String comment,
        JsonNode decisionSnapshot
    ) {
    }

    public record ApprovalDecisionResponse(
        UUID id,
        UUID approvalRequestId,
        UUID approvalStepId,
        String reviewerUserId,
        UUID decisionConceptId,
        String decisionConceptCode,
        String comment,
        JsonNode decisionSnapshot,
        Instant decidedAt
    ) {
    }

    public record ApprovalResponse(
        UUID id,
        UUID projectId,
        UUID workflowInstanceId,
        UUID workflowExecutionId,
        String subjectEntityType,
        UUID subjectEntityId,
        UUID statusConceptId,
        String statusConceptCode,
        String requestedBy,
        Instant requestedAt,
        Instant completedAt,
        Instant expiresAt,
        JsonNode policyConfiguration,
        List<Map<String, Object>> steps,
        List<ApprovalDecisionResponse> decisions,
        List<Map<String, Object>> availableDecisions
    ) {
    }
}
