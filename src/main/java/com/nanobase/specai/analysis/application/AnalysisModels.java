package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.document.domain.Clause;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalysisModels {
    private AnalysisModels() {
    }

    public record ProfileInputs(
        UUID ontologyVersionId,
        List<UUID> terminologyCatalogIds,
        UUID extractionPolicyVersionId,
        UUID promptPackageVersionId,
        UUID outputSchemaVersionId,
        UUID modelRoutingPolicyVersionId,
        UUID confidencePolicyVersionId
    ) {
    }

    public record PolicyDocument(UUID id, String policyCode, String policyType,
                                 JsonNode configuration) {
    }

    public record TerminologyMatch(UUID entryId, UUID catalogId, String term,
                                   String termType, String semanticRole, double weight,
                                   JsonNode metadata) {
    }

    public record ConceptMatch(UUID id, String code, String name, String conceptType,
                               JsonNode metadata) {
    }

    public record UnitMatch(UUID id, String canonicalCode, String symbol,
                            String dimension, JsonNode conversionMetadata) {
    }

    public record ClauseSignalContext(
        UUID organizationId,
        UUID policyVersionId,
        Clause clause,
        Map<String, Double> signalValues,
        Map<String, Object> metadata
    ) {
    }

    public record ClauseSignal(String source, double score, String reasonCode,
                               Map<String, Object> metadata) {
    }

    public record ClauseSignalResult(double signalScore, String recommendedAction,
                                     List<ClauseSignal> signals,
                                     boolean requiresManualReview) {
    }

    public record ContextItem(String sourceType, UUID sourceId, String text,
                              double relevance, Map<String, Object> metadata) {
    }

    public record ClauseAnalysisContext(Clause clause, List<ContextItem> selectedContext,
                                        Map<String, Object> features) {
    }

    public record ExtractionStrategy(String code, String modelProfile,
                                     UUID promptPackageVersionId, boolean includeTables,
                                     int maximumOutputTokens, String validationPolicy,
                                     String retryPolicy, boolean secondModelValidation,
                                     JsonNode outputConfiguration) {
    }

    public record GroundingInput(String sourceText, List<String> sourceFragments,
                                 JsonNode extractedRequirement,
                                 Map<String, Object> sourceMetadata) {
    }

    public record GroundingEvidence(String method, String fragment, double score,
                                    Map<String, Object> metadata) {
    }

    public record GroundingResult(String status, double coverage,
                                  List<String> unsupportedFields,
                                  List<GroundingEvidence> evidence) {
    }

    public record ConfidenceContext(Map<String, Double> factors) {
    }

    public record ConfidenceFactor(String code, double effect, double input,
                                   double weight) {
    }

    public record ConfidenceResult(double score, String level,
                                   List<ConfidenceFactor> factors,
                                   boolean requiresReview) {
    }

    public record DuplicateCandidate(UUID requirementId, String normalizedText,
                                     UUID conceptId, JsonNode attributes,
                                     UUID sourceClauseId, UUID documentVersionId) {
    }

    public record DuplicateResult(String status, double score,
                                  List<String> reasonCodes) {
    }

    public record ModelCandidate(UUID profileId, String profileCode,
                                 UUID deploymentId, String healthStatus,
                                 JsonNode selectionPolicy,
                                 Map<String, Double> liveSignals) {
    }

    public record ModelRoutingContext(Map<String, Double> signals,
                                      Map<String, Object> attributes) {
    }

    public record ModelRoutingResult(UUID profileId, String profileCode,
                                     UUID deploymentId, List<String> reasonCodes,
                                     Map<String, Double> scoredCandidates) {
    }

    public record PromptMaterial(UUID packageVersionId, UUID outputSchemaVersionId,
                                 List<String> components, JsonNode componentConfiguration) {
    }

    public record ExtractionRequest(UUID jobId, UUID organizationId, UUID clauseId,
                                    String logicalModel, String modelProfile,
                                    List<String> promptComponents, JsonNode outputSchema,
                                    ClauseAnalysisContext context,
                                    int maximumOutputTokens, UUID correlationId) {
    }

    public record ExtractionResponse(UUID modelRunId, JsonNode output,
                                     long latencyMs, int inputTokens, int outputTokens,
                                     Instant completedAt) {
    }
}
