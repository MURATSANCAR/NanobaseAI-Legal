package com.nanobase.specai.analysis.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.analysis.domain.Requirement;
import com.nanobase.specai.analysis.domain.RequirementExtractionJob;
import com.nanobase.specai.analysis.domain.RequirementRevision;
import java.time.Instant;
import java.util.UUID;

public final class AnalysisContracts {
    private AnalysisContracts() {
    }

    public record AnalysisProfileResponse(
        UUID id, UUID organizationId, UUID projectId, UUID documentId,
        UUID documentVersionId, JsonNode sectorContext, JsonNode documentContext,
        JsonNode terminologySetIds, UUID ontologyVersionId, UUID policyVersionId,
        UUID promptPackageVersionId, UUID outputSchemaVersionId,
        UUID modelRoutingPolicyId, UUID confidencePolicyId,
        JsonNode snapshot, String contentHash, Instant createdAt
    ) {
        public static AnalysisProfileResponse from(AnalysisProfile profile,
                                                   ObjectMapper mapper) {
            return new AnalysisProfileResponse(profile.id(), profile.organizationId(),
                profile.projectId(), profile.documentId(), profile.documentVersionId(),
                json(mapper, profile.sectorContextJson()),
                json(mapper, profile.documentContextJson()),
                json(mapper, profile.terminologySetIdsJson()),
                profile.ontologyVersionId(), profile.policyVersionId(),
                profile.promptPackageVersionId(), profile.outputSchemaVersionId(),
                profile.modelRoutingPolicyId(), profile.confidencePolicyId(),
                json(mapper, profile.snapshotJson()), profile.contentHash(),
                profile.createdAt());
        }
    }

    public record ExtractionJobResponse(
        UUID id, UUID projectId, UUID documentId, UUID documentVersionId,
        String status, UUID analysisProfileId, UUID ontologyVersionId,
        UUID terminologySnapshotId, UUID policyVersionId,
        UUID promptPackageVersionId, UUID outputSchemaVersionId,
        UUID modelRoutingPolicyId, UUID confidencePolicyId,
        int totalClauseCount, int processedClauseCount,
        int extractedRequirementCount, int manualReviewCount,
        UUID correlationId, UUID parentJobId, Instant startedAt,
        Instant completedAt, Instant createdAt, Instant updatedAt, long version
    ) {
        public static ExtractionJobResponse from(RequirementExtractionJob job) {
            return new ExtractionJobResponse(job.id(), job.projectId(), job.documentId(),
                job.documentVersionId(), job.status(), job.analysisProfileId(),
                job.ontologyVersionId(), job.terminologySnapshotId(),
                job.policyVersionId(), job.promptPackageVersionId(),
                job.outputSchemaVersionId(), job.modelRoutingPolicyId(),
                job.confidencePolicyId(), job.totalClauseCount(),
                job.processedClauseCount(), job.extractedRequirementCount(),
                job.manualReviewCount(), job.correlationId(), job.parentJobId(),
                job.startedAt(), job.completedAt(), job.createdAt(), job.updatedAt(),
                job.version());
        }
    }

    public record RequirementResponse(
        UUID id, UUID projectId, UUID documentId, UUID documentVersionId,
        UUID extractionJobId, UUID sourceClauseId, String requirementCode,
        String title, String requirementText, UUID primaryConceptId,
        String modality, UUID modalityConceptId, String testabilityStatus,
        String conditionText, String subjectText, String actionText,
        String objectText, JsonNode attributes, String extractionMethod,
        String reviewStatus, String groundingStatus, double groundingCoverage,
        UUID analysisProfileId, UUID ontologyVersionId, UUID policyVersionId,
        UUID promptVersionId, double combinedConfidence,
        Instant createdAt, Instant updatedAt, long version
    ) {
        public static RequirementResponse from(Requirement requirement,
                                               ObjectMapper mapper) {
            return new RequirementResponse(requirement.id(), requirement.projectId(),
                requirement.documentId(), requirement.documentVersionId(),
                requirement.extractionJobId(), requirement.sourceClauseId(),
                requirement.requirementCode(), requirement.title(),
                requirement.requirementText(), requirement.primaryConceptId(),
                requirement.modality(), requirement.modalityConceptId(),
                requirement.testabilityStatus(), requirement.conditionText(),
                requirement.subjectText(), requirement.actionText(),
                requirement.objectText(), json(mapper, requirement.attributesJson()),
                requirement.extractionMethod(), requirement.reviewStatus(),
                requirement.groundingStatus(), requirement.groundingCoverage(),
                requirement.analysisProfileId(), requirement.ontologyVersionId(),
                requirement.policyVersionId(), requirement.promptVersionId(),
                requirement.combinedConfidence(), requirement.createdAt(),
                requirement.updatedAt(), requirement.version());
        }
    }

    public record RevisionResponse(UUID id, UUID requirementId, int revisionNumber,
                                   JsonNode snapshot, String sourceType,
                                   UUID sourceReferenceId, String createdBy,
                                   Instant createdAt) {
        public static RevisionResponse from(RequirementRevision revision,
                                            ObjectMapper mapper) {
            return new RevisionResponse(revision.id(), revision.requirementId(),
                revision.revisionNumber(), json(mapper, revision.snapshotJson()),
                revision.sourceType(), revision.sourceReferenceId(),
                revision.createdBy(), revision.createdAt());
        }
    }

    static JsonNode json(ObjectMapper mapper, String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored analysis JSON is invalid", exception);
        }
    }
}
