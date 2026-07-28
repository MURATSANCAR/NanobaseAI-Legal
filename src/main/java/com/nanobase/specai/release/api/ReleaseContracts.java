package com.nanobase.specai.release.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReleaseContracts {
    private ReleaseContracts() {
    }

    public record CreateReleaseRequest(
        @NotBlank String releaseCode,
        @NotBlank String semanticVersion,
        @NotBlank String releaseTypeCode,
        @NotBlank String sourceCommit,
        @NotBlank String buildNumber
    ) {
    }

    public record GateResultRequest(
        @NotBlank String gateCode,
        @NotBlank String status,
        @NotNull JsonNode evidenceReferences,
        @NotBlank String summary,
        String waiverReason,
        JsonNode compensatingControls
    ) {
    }

    public record ApprovalRequest(JsonNode approvalSteps) {
    }

    public record ApprovalDecisionRequest(
        @NotBlank String step,
        @NotBlank String decision,
        String comment
    ) {
    }

    public record DryRunRequest(
        @NotBlank String environment,
        @NotNull JsonNode steps,
        @NotNull JsonNode evidenceReferences
    ) {
    }

    public record DryRunResultRequest(
        boolean passed,
        @NotNull JsonNode steps,
        @NotNull JsonNode evidenceReferences
    ) {
    }

    public record DeploymentResultRequest(
        boolean succeeded,
        @NotNull JsonNode evidence,
        String summary
    ) {
    }

    public record GoLiveDecisionRequest(
        @NotBlank String decision,
        JsonNode conditions,
        JsonNode openRisks,
        @NotBlank String rollbackPlanReference
    ) {
    }

    public record StabilizationRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt
    ) {
    }

    public record ReleaseManifestRequest(
        @NotBlank String backendImageDigest,
        @NotBlank String frontendImageDigest,
        @NotNull JsonNode workerImageDigests,
        @NotNull JsonNode modelArtifacts,
        @NotNull JsonNode promptVersions,
        @NotNull JsonNode policyVersions,
        @NotNull JsonNode ontologyVersions,
        @NotNull JsonNode workflowVersions,
        @NotNull JsonNode databaseMigrationVersions
    ) {
    }

    public record ReleaseArtifactRequest(
        @NotBlank String artifactTypeCode,
        @NotBlank String artifactReference,
        @NotBlank String sha256,
        @NotBlank String signatureReference,
        String sbomReference
    ) {
    }

    public record ReleasePackageResponse(
        Map<String, Object> release,
        Map<String, Object> manifest,
        List<Map<String, Object>> gates,
        List<Map<String, Object>> artifacts,
        List<Map<String, Object>> approvals,
        List<Map<String, Object>> blockers,
        List<Map<String, Object>> qualityDebt,
        List<Map<String, Object>> dryRuns,
        List<Map<String, Object>> decisions,
        boolean eligibleForGoLive,
        List<String> missingEvidence
    ) {
    }

    public record SystemVersionResponse(
        String applicationVersion,
        String buildNumber,
        String commitHash,
        String releaseDate,
        String databaseMigrationVersion,
        String configurationManifestVersion,
        String modelPublicProfileVersion
    ) {
    }

    public record DiagnosticBundleResponse(
        UUID id,
        Instant generatedAt,
        Instant expiresAt,
        String contentHash,
        JsonNode manifest
    ) {
    }
}
