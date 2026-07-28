package com.nanobase.specai.compliance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceModels.CandidateEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceContext;
import com.nanobase.specai.compliance.application.ComplianceModels.EvidenceRerankingContext;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import com.nanobase.specai.knowledge.application.DynamicValueValidator;
import com.nanobase.specai.knowledge.application.PolicyEntityResolutionService;
import com.nanobase.specai.knowledge.application.PolicyEvidenceValidityEngine;
import com.nanobase.specai.knowledge.application.PolicySourceAuthorityEvaluator;
import com.nanobase.specai.knowledge.application.UnitConversionService;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.DynamicValue;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityCandidate;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityResolutionContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Sprint5DynamicEnginesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unknownValueTypesArePreservedAsUnsupportedMetadata() throws Exception {
        DynamicValue value = new DynamicValue("VECTOR", null, null, null, null,
            null, mapper.readTree("{\"values\":[1,2]}"), null, Map.of());
        DynamicValue normalized = new DynamicValueValidator().normalize(value,
            mapper.readTree("{\"unsupportedValueTypeAction\":\"STORE\"}"));

        assertThat(normalized.type()).isEqualTo("VECTOR");
        assertThat(normalized.unsupported()).isTrue();
        assertThat(normalized.unsupportedMetadata()).containsEntry("originalType", "VECTOR");
    }

    @Test
    void invalidRangeIsRejectedByDynamicShapeValidation() {
        DynamicValue value = new DynamicValue("RANGE", null,
            BigDecimal.TEN, BigDecimal.ONE, null, null, null, null, Map.of());

        assertThatThrownBy(() -> new DynamicValueValidator().normalize(
            value, mapper.createObjectNode()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Range start");
    }

    @Test
    void entityResolutionDoesNotAutoMergeAmbiguousCandidates() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID type = UUID.randomUUID();
        EntityResolutionContext context = new EntityResolutionContext(
            organizationId, "Nanobase AI", Map.of(), type, null, null, null,
            mapper.readTree("""
                {"confirmedThreshold":0.90,"possibleThreshold":0.60,
                 "ambiguityDelta":0.10,
                 "weights":{"normalizedName":1.0}}
                """));
        EntityCandidate first = new EntityCandidate(UUID.randomUUID(), "NanobaseAI",
            Map.of(), type, null, null, null, 0);
        EntityCandidate second = new EntityCandidate(UUID.randomUUID(), "Nanobase AI",
            Map.of(), type, null, null, null, 0);

        var result = new PolicyEntityResolutionService().resolve(
            context, List.of(first, second));

        assertThat(result.status()).isEqualTo("AMBIGUOUS");
        assertThat(result.matchedEntityId()).isNull();
        assertThat(result.ambiguousCandidateIds()).containsExactlyInAnyOrder(
            first.entityId(), second.entityId());
    }

    @Test
    void rerankingUsesConfiguredSignalsAndLimit() throws Exception {
        UUID organizationId = UUID.randomUUID();
        CandidateEvidence strong = candidate(0.9, 0.8, 0.9);
        CandidateEvidence weak = candidate(0.2, 0.3, 0.4);
        PolicyVersion policy = new PolicyVersion(UUID.randomUUID(), "TEST",
            mapper.readTree("""
                {"candidateLimits":{"reranking":1},"minimumValidityScore":0.2,
                 "signals":{"ontology":0.4,"lexical":0.2,
                            "evidenceValidity":0.4}}
                """));

        var result = new PolicyEvidenceReranker().rerank(
            new EvidenceRerankingContext(organizationId, UUID.randomUUID(),
                null, List.of(weak, strong), Map.of()), policy);

        assertThat(result.ranked()).hasSize(1);
        assertThat(result.ranked().getFirst().evidence().fragmentId())
            .isEqualTo(strong.fragmentId());
        assertThat(result.rejectedCount()).isEqualTo(1);
    }

    @Test
    void numericThresholdIsDeterministicAndUnitAware() {
        UUID unit = UUID.randomUUID();
        NumericThresholdComparisonStrategy strategy =
            new NumericThresholdComparisonStrategy(mock(UnitConversionService.class), mapper);
        ComparisonContext context = new ComparisonContext("numeric-threshold",
            "GREATER_THAN_OR_EQUAL", BigDecimal.valueOf(500), null, unit,
            BigDecimal.valueOf(1000), null, unit, null, null, null, null,
            mapper.createObjectNode(), Map.of("organizationId", UUID.randomUUID()));

        var result = strategy.compare(context);

        assertThat(result.status()).isEqualTo("SATISFIED");
        assertThat(result.deterministic()).isTrue();
        assertThat(result.explanation().path("unitCompatible").asBoolean()).isTrue();
    }

    @Test
    void rangeDateBooleanAndCompositeConditionsAreDeterministic() {
        var range = new NumericRangeComparisonStrategy(mapper).compare(
            new ComparisonContext("numeric-range", null, BigDecimal.TEN,
                BigDecimal.valueOf(20), null, BigDecimal.ONE,
                BigDecimal.valueOf(25), null, null, null, null, null, null,
                Map.of()));
        var date = new DateValidityComparisonStrategy(mapper).compare(
            new ComparisonContext("date-validity", null, null, null, null,
                null, null, null, null, null, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"), mapper.createObjectNode(),
                Map.of()));
        var bool = new BooleanExistenceComparisonStrategy(mapper).compare(
            new ComparisonContext("boolean-existence", null, null, null, null,
                null, null, null, true, true, null, null,
                mapper.createObjectNode(), Map.of()));
        var composite = new CompositeConditionEvaluator().combine(
            "AT_LEAST_N", List.of("SATISFIED", "NOT_SATISFIED", "SATISFIED"),
            Map.of("minimum", 2));

        assertThat(range.status()).isEqualTo("SATISFIED");
        assertThat(date.status()).isEqualTo("SATISFIED");
        assertThat(bool.status()).isEqualTo("SATISFIED");
        assertThat(composite.status()).isEqualTo("SATISFIED");
    }

    @Test
    void confidenceExplainsPolicyFactorsAndForcesContradictionReview() throws Exception {
        var result = new PolicyComplianceConfidenceEngine().evaluate(
            new ConfidenceContext(
                Map.of("relevance", 1d, "validity", 0.8),
                false, true,
                mapper.readTree("""
                    {"weights":{"relevance":0.6,"validity":0.4},
                     "penalties":{"contradiction":0.3},"reviewBelow":0.7,
                     "levels":[{"concept":"HIGH","minimum":0.8},
                               {"concept":"LOW","minimum":0.0}]}
                    """)));

        assertThat(result.requiresReview()).isTrue();
        assertThat(result.factors()).extracting(
            ComplianceModels.ConfidenceFactor::factorConcept)
            .contains("relevance", "validity", "CONTRADICTION");
    }

    @Test
    void evidenceValidityAndSourceAuthorityArePolicyDriven() throws Exception {
        UUID issuer = UUID.randomUUID();
        var authority = new PolicySourceAuthorityEvaluator().evaluate(
            "CUSTOM_DOCUMENT", issuer,
            mapper.readTree("""
                {"defaultScore":0.20,
                 "sourceScores":{"CUSTOM_DOCUMENT":0.55},
                 "issuerOverrides":{"%s":0.90}}
                """.formatted(issuer)),
            mapper.readTree("{\"score\":0.75}"));
        var validity = new PolicyEvidenceValidityEngine().evaluate(
            new PolicyEvidenceValidityEngine.ValidityInput(
                Instant.parse("2026-07-28T00:00:00Z"),
                Instant.parse("2026-07-27T00:00:00Z"),
                1, 1, true, authority),
            mapper.readTree("""
                {"factors":{"notExpired":0.5,"parserQuality":0.1,
                            "ocrQuality":0.1,"verified":0.1,"authority":0.2},
                 "minimumUsableScore":0.6,
                 "selectors":{"usable":"tenant-usable",
                              "expired":"tenant-expired",
                              "indeterminate":"tenant-review"}}
                """));

        assertThat(authority).isEqualTo(0.75);
        assertThat(validity.statusSelector()).isEqualTo("tenant-expired");
        assertThat(validity.factors()).extracting(
            PolicyEvidenceValidityEngine.ValidityFactor::factor)
            .contains("notExpired", "authority", "verified");
    }

    private CandidateEvidence candidate(double ontology, double lexical,
                                        double validity) {
        return new CandidateEvidence(UUID.randomUUID(), null, null, null, null,
            null, null, null, null, ontology, lexical, 0, validity, 0.5, 0,
            0, Instant.now(), Map.of());
    }
}
