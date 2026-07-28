package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportingContracts {
    private ReportingContracts() {
    }

    public record CreateReportDefinitionRequest(
        @NotBlank String reportCode,
        @NotBlank String name,
        String description,
        @NotBlank String scope,
        UUID subjectEntityTypeConceptId
    ) {
    }

    public record ReportDefinitionResponse(
        UUID id,
        String reportCode,
        String name,
        String description,
        String scope,
        UUID subjectEntityTypeConceptId,
        UUID activeVersionId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CreateReportVersionRequest(
        JsonNode sectionConfiguration,
        @NotNull UUID dataPolicyVersionId,
        @NotNull UUID templateVersionId,
        @NotEmpty List<@Valid ReportSectionDraft> sections
    ) {
    }

    public record ReportSectionDraft(
        @NotBlank String sectionCode,
        @NotNull UUID sectionTypeConceptId,
        @NotBlank String titleTemplate,
        JsonNode dataQueryConfiguration,
        JsonNode renderConfiguration,
        JsonNode visibilityCondition,
        int sortOrder
    ) {
    }

    public record GenerateReportRequest(
        @NotNull UUID reportDefinitionVersionId,
        @NotEmpty List<UUID> formatConceptIds,
        UUID staleOverrideApprovalId
    ) {
    }

    public record PreviewReportRequest(@NotNull UUID projectId) {
    }

    public record ReportArtifactResponse(
        UUID id,
        UUID formatConceptId,
        String formatConceptCode,
        String fileName,
        String mimeType,
        long fileSize,
        String sha256,
        int versionNumber,
        Instant createdAt
    ) {
    }

    public record ReportJobResponse(
        UUID id,
        UUID projectId,
        UUID reportDefinitionVersionId,
        UUID templateVersionId,
        UUID dataSnapshotId,
        UUID statusConceptId,
        String statusConceptCode,
        int progress,
        String requestedBy,
        Instant startedAt,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        List<ReportArtifactResponse> artifacts
    ) {
    }

    public record DownloadUrlResponse(String url, Instant expiresAt) {
    }
}
