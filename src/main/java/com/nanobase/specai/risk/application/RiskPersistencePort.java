package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityResult;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonResult;
import com.nanobase.specai.risk.application.RiskModels.ConflictEntity;
import com.nanobase.specai.risk.application.RiskModels.PropagationCandidate;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureResult;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RiskPersistencePort {
    record RequirementCandidate(
        UUID id, UUID projectId, UUID documentId, UUID documentVersionId,
        UUID clauseId, UUID conceptId, String requirementCode, String title,
        String text, JsonNode attributes, double confidence, double groundingCoverage,
        String testabilityStatus, int pageNumber, JsonNode boundingBoxes
    ) {
    }

    UUID createProfile(UUID organizationId, UUID projectId, RiskProfileInputs inputs,
                       JsonNode snapshot, String contentHash);
    UUID createJob(UUID organizationId, UUID projectId, UUID profileId,
                   UUID knowledgeSnapshotId, long requirementSetVersion, String createdBy);
    void startJob(UUID organizationId, UUID jobId, int totalCandidates);
    void finishJob(UUID organizationId, UUID jobId, int processed, int risks,
                   int ambiguities, int conflicts, int manualReviews, int failures);
    void failJob(UUID organizationId, UUID jobId, String errorCode);
    void cancelJob(UUID organizationId, UUID jobId);
    void event(UUID organizationId, UUID jobId, String eventType, int progress,
               String message, JsonNode metadata);
    Optional<Map<String, Object>> job(UUID organizationId, UUID jobId);
    List<Map<String, Object>> jobEvents(UUID organizationId, UUID jobId);
    List<RequirementCandidate> requirements(UUID organizationId, UUID projectId);
    long validEvidenceCount(UUID organizationId, UUID requirementId);
    Optional<UUID> latestKnowledgeSnapshot(UUID organizationId, UUID projectId);
    UUID createRisk(UUID organizationId, UUID projectId, UUID profileId,
                    RequirementCandidate source, UUID riskConceptId, UUID statusConceptId,
                    UUID sourceRoleConceptId, RiskSignalResult signal,
                    RiskExposureResult exposure, double confidence);
    UUID createAmbiguity(UUID organizationId, UUID projectId, UUID profileId,
                         RequirementCandidate source, AmbiguityResult result,
                         UUID severityConceptId);
    List<ConflictEntity> conflictCandidates(UUID organizationId, UUID projectId,
                                            RequirementCandidate seed, int limit);
    UUID createConflict(UUID organizationId, UUID projectId, UUID profileId,
                        RequirementCandidate left, ConflictEntity right,
                        UUID conflictConceptId, UUID statusConceptId,
                        UUID leftSideConceptId, UUID rightSideConceptId,
                        ConflictComparisonResult result);
    List<Map<String, Object>> listRisks(UUID organizationId, UUID projectId);
    Optional<Map<String, Object>> risk(UUID organizationId, UUID riskId);
    List<Map<String, Object>> riskSources(UUID organizationId, UUID riskId);
    List<Map<String, Object>> riskFactors(UUID organizationId, UUID riskId);
    List<Map<String, Object>> riskHistory(UUID organizationId, UUID riskId);
    void reviewRisk(UUID organizationId, UUID riskId, String reviewStatus,
                    UUID changeConceptId, String actor, JsonNode feedback);
    void assignRisk(UUID organizationId, UUID riskId, String ownerUserId,
                    LocalDate dueDate, UUID changeConceptId, String actor);
    List<Map<String, Object>> listAmbiguities(UUID organizationId, UUID projectId);
    Optional<Map<String, Object>> ambiguity(UUID organizationId, UUID ambiguityId);
    List<Map<String, Object>> ambiguitySources(UUID organizationId, UUID ambiguityId);
    List<Map<String, Object>> interpretations(UUID organizationId, UUID ambiguityId);
    UUID addInterpretation(UUID organizationId, UUID ambiguityId, String text,
                           JsonNode attributes, double confidence, JsonNode sourceIds);
    void reviewAmbiguity(UUID organizationId, UUID ambiguityId, String reviewStatus);
    List<Map<String, Object>> listConflicts(UUID organizationId, UUID projectId);
    Optional<Map<String, Object>> conflict(UUID organizationId, UUID conflictId);
    List<Map<String, Object>> conflictSources(UUID organizationId, UUID conflictId);
    List<Map<String, Object>> conflictFactors(UUID organizationId, UUID conflictId);
    List<Map<String, Object>> conflictHistory(UUID organizationId, UUID conflictId);
    void reviewConflict(UUID organizationId, UUID conflictId, String reviewStatus,
                        UUID changeConceptId, String actor, JsonNode resolution);
    List<RiskModels.ImpactGraphEdge> dependencyGraph(UUID organizationId, UUID projectId);
    void savePropagation(UUID organizationId, UUID riskId,
                         List<PropagationCandidate> candidates);
    List<Map<String, Object>> propagation(UUID organizationId, UUID riskId);
    List<Map<String, Object>> mitigationCandidates(UUID organizationId, UUID riskId);
    UUID createClarificationCandidate(UUID organizationId, UUID projectId,
                                      String entityType, UUID entityId,
                                      UUID strategyVersionId, String question, String reason,
                                      JsonNode sourceIds, UUID priorityConceptId,
                                      boolean legalReview);
}
