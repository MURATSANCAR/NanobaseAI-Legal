package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RiskModels {
    private RiskModels() {
    }

    public record VersionedPolicy(UUID id, String code, JsonNode configuration) {
    }

    public record RiskProfileInputs(
        UUID analysisProfileId,
        UUID riskTaxonomyVersionId,
        UUID ontologyVersionId,
        UUID terminologySnapshotId,
        UUID riskPolicyVersionId,
        UUID conflictPolicyVersionId,
        UUID ambiguityPolicyVersionId,
        UUID impactPolicyVersionId,
        UUID confidencePolicyVersionId,
        UUID severityPolicyVersionId,
        UUID promptPackageVersionId,
        UUID modelRoutingPolicyId,
        UUID documentAuthorityPolicyId
    ) {
    }

    public record RiskSignalContext(
        UUID organizationId,
        UUID projectId,
        String sourceEntityType,
        UUID sourceEntityId,
        Map<String, Double> signals,
        Map<String, Object> metadata
    ) {
    }

    public record SignalFactor(String code, double value, double weight, double effect) {
    }

    public record CandidateRiskConcept(UUID conceptId, String conceptCode, double score,
                                       List<String> reasonCodes) {
    }

    public record RiskSignalResult(double signalScore,
                                   List<CandidateRiskConcept> candidateRiskConcepts,
                                   List<SignalFactor> factors,
                                   boolean requiresDetailedAnalysis) {
    }

    public record RiskExposureContext(Map<String, Double> signals,
                                      Map<String, Object> metadata) {
    }

    public record ExposureFactor(String code, double input, double weight, double effect) {
    }

    public record ScoredDimension(double score, List<ExposureFactor> factors) {
    }

    public record RiskExposureResult(ScoredDimension probability,
                                     ScoredDimension impact,
                                     double exposure,
                                     UUID severityConceptId,
                                     String severityConceptCode,
                                     String method) {
    }

    public record AmbiguityContext(UUID sourceEntityId,
                                   Map<String, Double> features,
                                   Map<String, Object> metadata) {
    }

    public record AmbiguityFactor(String code, double value, double weight, double effect) {
    }

    public record AmbiguityResult(boolean finding,
                                  UUID conceptId,
                                  String conceptCode,
                                  double confidence,
                                  List<AmbiguityFactor> factors,
                                  List<String> missingFields) {
    }

    public record ConflictEntity(UUID id, String entityType, UUID documentId,
                                 UUID documentVersionId, UUID conceptId,
                                 String scopeKey, JsonNode attributes,
                                 String sourceText, int pageNumber) {
    }

    public record ConflictCandidateContext(ConflictEntity seed,
                                           List<ConflictEntity> retrievedCandidates,
                                           Map<String, Object> metadata) {
    }

    public record ConflictCandidate(ConflictEntity left, ConflictEntity right,
                                    double retrievalScore, List<String> completedStages) {
    }

    public record ConflictCandidateResult(List<ConflictCandidate> candidates,
                                          int retrievedCount, int rerankedCount) {
    }

    public record ConflictComparisonContext(ConflictCandidate candidate,
                                            JsonNode strategyConfiguration,
                                            JsonNode authorityConfiguration) {
    }

    public record ConflictComparisonResult(boolean conflict,
                                           String conflictConceptCode,
                                           String strategyCode,
                                           String description,
                                           double confidence,
                                           JsonNode leftInterpretation,
                                           JsonNode rightInterpretation,
                                           JsonNode authorityAssessment,
                                           boolean requiresManualReview,
                                           List<UUID> supportingSourceIds) {
    }

    public record ImpactGraphEdge(String sourceType, UUID sourceId,
                                  String targetType, UUID targetId,
                                  UUID dependencyConceptId, double confidence) {
    }

    public record ChangeSet(UUID id, UUID organizationId, UUID projectId,
                            List<ChangeItem> items) {
    }

    public record ChangeItem(UUID id, UUID changeTypeConceptId,
                             String sourceType, UUID sourceId,
                             String targetType, UUID targetId,
                             double confidence) {
    }

    public record ImpactAnalysisContext(List<ImpactGraphEdge> graph,
                                        Map<String, Object> metadata) {
    }

    public record AffectedEntity(String entityType, UUID entityId,
                                 UUID impactConceptId, String impactConceptCode,
                                 List<String> reasonCodes, double confidence) {
    }

    public record ImpactAnalysisResult(List<AffectedEntity> affectedEntities) {
    }

    public record PropagationContext(UUID sourceRiskId, UUID sourceEntityId,
                                     List<ImpactGraphEdge> graph,
                                     Map<String, Object> metadata) {
    }

    public record PropagationCandidate(String targetEntityType, UUID targetEntityId,
                                       UUID propagationConceptId,
                                       String propagationConceptCode,
                                       List<UUID> path, double confidence) {
    }
}
