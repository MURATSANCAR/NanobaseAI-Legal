package com.nanobase.specai.pilot.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PilotContracts {
    private PilotContracts() {
    }

    public record CreateFeedbackRequest(
        @NotNull UUID projectId,
        UUID pilotSessionId,
        @NotBlank String feedbackTypeCode,
        @NotBlank String severityCode,
        @NotBlank String classificationCode,
        @NotBlank String entityType,
        UUID entityId,
        @NotBlank String title,
        @NotBlank String description,
        String expectedBehavior,
        String actualBehavior,
        List<EvidenceDraft> evidence
    ) {
    }

    public record CreatePilotSessionRequest(
        @NotNull UUID projectId,
        @NotBlank String pilotPhaseCode,
        @NotNull UUID configurationSnapshotId,
        Instant startedAt
    ) {
    }

    public record RecordPilotEventRequest(
        @NotBlank String eventTypeCode,
        @NotBlank String entityType,
        UUID entityId,
        UUID correlationId,
        @NotNull JsonNode metadata
    ) {
    }

    public record RecordPilotMetricRequest(
        @NotBlank String metricCode,
        double metricValue,
        JsonNode dimensions,
        Instant measuredAt
    ) {
    }

    public record EvidenceDraft(
        @NotBlank String evidenceTypeCode,
        @NotBlank String referenceEntityType,
        UUID referenceEntityId,
        JsonNode sanitizedSnapshot
    ) {
    }

    public record AssignFeedbackRequest(@NotBlank String assignedTeamCode) {
    }

    public record ResolveFeedbackRequest(
        @NotBlank String resolutionSummary,
        boolean createRegressionCase,
        UUID regressionSuiteId,
        String regressionCaseTypeCode,
        JsonNode expectedBehavior,
        JsonNode sanitizedInput
    ) {
    }

    public record TriageFeedbackRequest(
        @NotBlank String rootCauseCode,
        List<String> secondaryCauseCodes,
        @NotBlank String reproducibilityCode,
        JsonNode affectedScope,
        @DecimalMin("0") @DecimalMax("100") double impactScore,
        @DecimalMin("0") @DecimalMax("100") double frequencyScore,
        @NotBlank String analysisSummary,
        JsonNode analysisSignals
    ) {
    }

    public record CreateConfigurationSnapshotRequest(
        @NotBlank String snapshotTypeCode,
        JsonNode modelDeployments,
        JsonNode modelProfiles,
        JsonNode promptVersions,
        JsonNode policyVersions,
        JsonNode ontologyVersions,
        JsonNode terminologySnapshots,
        JsonNode outputSchemaVersions,
        JsonNode workflowVersions,
        JsonNode featureFlags
    ) {
    }

    public record CreateReproductionPackageRequest(
        @NotNull UUID feedbackCaseId,
        @NotNull UUID configurationSnapshotId,
        @NotNull JsonNode sanitizedInput,
        @NotNull JsonNode expectedOutput,
        @NotNull JsonNode actualOutput,
        @NotNull JsonNode executionInstructions
    ) {
    }

    public record CreateRegressionSuiteRequest(
        @NotBlank String suiteCode,
        @NotBlank String name,
        @NotBlank String scope
    ) {
    }

    public record CreateImprovementCandidateRequest(
        @NotBlank String candidateTypeCode,
        @NotNull UUID rootCauseRecordId,
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String targetComponentCode,
        @NotNull UUID baselineConfigurationSnapshotId,
        @NotNull UUID candidateConfigurationSnapshotId,
        @NotNull JsonNode expectedImprovement,
        @NotNull JsonNode riskAssessment
    ) {
    }

    public record CreateExperimentRequest(
        @NotBlank String experimentTypeCode,
        @NotBlank String name,
        String description,
        List<UUID> datasetIds,
        @NotNull JsonNode metricConfiguration,
        @NotNull UUID qualityGateVersionId
    ) {
    }

    public record StartExperimentRunRequest(JsonNode runtimeConfiguration) {
    }

    public record RecordExperimentResultRequest(
        @NotNull UUID candidateSnapshotId,
        @NotNull JsonNode metrics,
        @NotNull JsonNode regressionSummary,
        @NotNull JsonNode resourceUsage,
        @NotNull JsonNode failureSummary,
        boolean qualityGatePassed
    ) {
    }

    public record CanaryRequest(
        UUID projectId,
        String userGroup,
        @DecimalMin("0.01") @DecimalMax("100") double trafficPercentage
    ) {
    }

    public record ComparisonResultRequest(
        @NotNull JsonNode comparison,
        boolean passed
    ) {
    }

    public record ConfigurationRollbackRequest(
        @NotNull UUID activeSnapshotId,
        @NotNull UUID targetSnapshotId,
        @NotBlank String reason,
        @NotNull List<String> approvers
    ) {
    }

    public record CreateQualityDebtRequest(
        @NotNull UUID feedbackCaseId,
        @NotBlank String debtTypeCode,
        @NotBlank String severityCode,
        @NotBlank String businessImpact,
        @NotBlank String technicalImpact,
        String workaround,
        String targetRelease,
        @NotBlank String ownerUserId,
        JsonNode compensatingControls
    ) {
    }

    public record CreateDisagreementRequest(
        @NotBlank String entityType,
        @NotNull UUID entityId,
        @NotBlank String reviewerAId,
        @NotBlank String reviewerBId,
        @NotNull JsonNode decisionA,
        @NotNull JsonNode decisionB,
        @NotBlank String disagreementCode
    ) {
    }

    public record AdjudicationRequest(@NotNull JsonNode finalDecision) {
    }

    public record ConceptResponse(
        UUID id,
        String catalogCode,
        String code,
        String name,
        String description,
        JsonNode metadata,
        int sortOrder
    ) {
    }

    public record RootCauseSuggestionResponse(
        String primaryCauseConcept,
        double confidence,
        List<Map<String, Object>> contributingFactors,
        List<String> recommendedInvestigationAreas,
        String explanation
    ) {
    }

    public record PilotDashboardResponse(
        Instant generatedAt,
        JsonNode configuration,
        long totalFeedback,
        long openFeedback,
        long releaseBlockers,
        double meanResolutionHours,
        long regressionCases,
        double manualReviewRate,
        double expertCorrectionRate,
        Double satisfactionScore,
        String uatStatus,
        List<Map<String, Object>> rootCauseDistribution,
        List<Map<String, Object>> qualityTrend,
        List<Map<String, Object>> recurringErrors
    ) {
    }
}
