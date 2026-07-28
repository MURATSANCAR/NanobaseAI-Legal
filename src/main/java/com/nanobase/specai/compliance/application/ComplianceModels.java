package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ComplianceModels {
    private ComplianceModels() {
    }

    public record PolicyVersion(UUID id, String code, JsonNode configuration) {
    }

    public record CandidateEvidence(
        UUID fragmentId,
        UUID claimId,
        UUID entityId,
        UUID attributeConceptId,
        UUID unitConceptId,
        BigDecimal numericValue,
        BigDecimal numericValueEnd,
        Boolean booleanValue,
        Instant dateValue,
        double ontologyScore,
        double lexicalScore,
        double attributeScore,
        double validityScore,
        double authorityScore,
        double historicalScore,
        int relationDistance,
        Instant documentDate,
        Map<String, Object> metadata
    ) {
    }

    public record EvidenceRerankingContext(
        UUID organizationId,
        UUID requirementId,
        UUID targetEntityId,
        List<CandidateEvidence> candidates,
        Map<String, Double> runtimeSignals
    ) {
    }

    public record RankedEvidence(CandidateEvidence evidence, double score,
                                 Map<String, Double> factors) {
    }

    public record RankedEvidenceResult(List<RankedEvidence> ranked,
                                       int rejectedCount) {
    }

    public record ComparisonContext(
        String providerCode,
        String operator,
        BigDecimal requiredValue,
        BigDecimal requiredValueEnd,
        UUID requiredUnitConceptId,
        BigDecimal evidenceValue,
        BigDecimal evidenceValueEnd,
        UUID evidenceUnitConceptId,
        Boolean requiredBoolean,
        Boolean evidenceBoolean,
        Instant requiredDate,
        Instant evidenceValidUntil,
        JsonNode expression,
        Map<String, Object> metadata
    ) {
    }

    public record ComparisonResult(
        String strategy,
        String status,
        boolean deterministic,
        JsonNode explanation,
        List<String> warnings
    ) {
    }

    public record ConfidenceContext(
        Map<String, Double> signals,
        boolean missingEvidence,
        boolean contradiction,
        JsonNode policy
    ) {
    }

    public record ConfidenceFactor(String factorConcept, double input,
                                   double weight, double effect) {
    }

    public record ConfidenceResult(double score, String levelConcept,
                                   boolean requiresReview,
                                   List<ConfidenceFactor> factors) {
    }
}
