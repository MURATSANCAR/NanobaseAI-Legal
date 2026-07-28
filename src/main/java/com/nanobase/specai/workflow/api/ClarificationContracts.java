package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClarificationContracts {
    private ClarificationContracts() {
    }

    public record ClarificationResponse(
        UUID id,
        UUID projectId,
        UUID workflowInstanceId,
        String sourceType,
        UUID sourceId,
        String questionCode,
        String questionText,
        String reason,
        UUID priorityConceptId,
        UUID statusConceptId,
        String statusConceptCode,
        boolean requiresLegalReview,
        boolean requiresTechnicalReview,
        JsonNode externalRecipient,
        UUID approvedVersionId,
        Instant sentAt,
        Instant answeredAt,
        long version,
        List<Map<String, Object>> revisions,
        List<Map<String, Object>> sources
    ) {
    }

    public record RevisionRequest(
        @NotBlank String questionText,
        String reason,
        JsonNode sourceSnapshot
    ) {
    }

    public record ClarificationStatusRequest(@NotNull UUID targetStatusConceptId) {
    }

    public record ClarificationAnswerRequest(
        @NotNull UUID documentId,
        UUID documentVersionId,
        UUID impactAnalysisJobId,
        @NotNull UUID targetStatusConceptId
    ) {
    }
}
