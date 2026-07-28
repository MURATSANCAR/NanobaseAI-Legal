package com.nanobase.specai.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseAnalysisContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ClauseSignalContext;
import com.nanobase.specai.analysis.application.AnalysisModels.ConfidenceContext;
import com.nanobase.specai.analysis.application.AnalysisModels.DuplicateCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingInput;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.document.domain.Clause;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DynamicAnalysisEnginesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void clauseSignalUsesVersionedWeightsAndThresholds() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        AnalysisCatalogPort catalog = mock(AnalysisCatalogPort.class);
        when(catalog.policy(organizationId, policyId)).thenReturn(new PolicyDocument(
            policyId, "tenant-policy", "EXTRACTION", mapper.readTree("""
                {
                  "signalWeights":{"semantic":0.7,"structure":0.3},
                  "decisionThresholds":{"extract":0.8,"manualReview":0.5}
                }
                """)));
        DynamicClauseSignalEvaluator evaluator = new DynamicClauseSignalEvaluator(catalog);
        ClauseSignalContext context = new ClauseSignalContext(organizationId, policyId,
            clause(), Map.of("semantic", 0.9, "structure", 0.8), Map.of());

        var result = evaluator.evaluate(context);

        assertThat(result.signalScore()).isEqualTo(0.87);
        assertThat(result.recommendedAction()).isEqualTo("EXTRACT");
        assertThat(result.signals()).extracting("source")
            .containsExactlyInAnyOrder("semantic", "structure");
    }

    @Test
    void confidenceIsExplainableAndPolicyDriven() throws Exception {
        WeightedConfidencePolicyEngine engine = new WeightedConfidencePolicyEngine();
        PolicyDocument policy = new PolicyDocument(UUID.randomUUID(), "confidence",
            "CONFIDENCE", mapper.readTree("""
                {
                  "weights":{"grounding":0.75,"schema":0.25},
                  "levels":[
                    {"code":"A","minimum":0.9},
                    {"code":"B","minimum":0.5},
                    {"code":"C","minimum":0.0}
                  ],
                  "reviewBelow":0.9
                }
                """));

        var result = engine.calculate(
            new ConfidenceContext(Map.of("grounding", 0.8, "schema", 1.0)), policy);

        assertThat(result.score()).isCloseTo(0.85,
            org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.level()).isEqualTo("B");
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.factors()).hasSize(2);
    }

    @Test
    void groundingRejectsMissingFragmentsAndVerifiesNumbers() throws Exception {
        LayeredGroundingValidator validator = new LayeredGroundingValidator();
        JsonNode output = mapper.readTree("""
            {"requirementCode":"REQ-1","attributes":{"minimumCapacity":500}}
            """);

        var grounded = validator.validate(new GroundingInput(
            "Sistem en az 500 eşzamanlı kullanıcıyı destekler.",
            List.of("en az 500 eşzamanlı kullanıcıyı"),
            output, Map.of("excludedPaths", List.of("requirementCode"))));
        var ungrounded = validator.validate(new GroundingInput(
            "Kaynak metin", List.of(), output, Map.of()));

        assertThat(grounded.status()).isEqualTo("GROUNDED");
        assertThat(grounded.evidence()).extracting("method")
            .contains("EXACT_FRAGMENT", "NUMBER_GROUNDING");
        assertThat(ungrounded.status()).isEqualTo("UNGROUNDED");
    }

    @Test
    void outputSchemaChangesWithoutSectorDto() throws Exception {
        JacksonOutputSchemaValidator validator = new JacksonOutputSchemaValidator();
        JsonNode schema = mapper.readTree("""
            {
              "type":"object",
              "required":["dynamicField"],
              "properties":{"dynamicField":{"type":"number","minimum":10}},
              "additionalProperties":false
            }
            """);

        assertThat(validator.validate(schema,
            mapper.readTree("{\"dynamicField\":12}"))).isEmpty();
        assertThat(validator.validate(schema,
            mapper.readTree("{\"dynamicField\":4}")))
            .anyMatch(error -> error.contains("below minimum"));
    }

    @Test
    void strategyNameAndModelProfileComeFromPolicy() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        AnalysisCatalogPort catalog = mock(AnalysisCatalogPort.class);
        when(catalog.policy(organizationId, policyId)).thenReturn(new PolicyDocument(
            policyId, "extraction", "EXTRACTION", mapper.readTree("""
                {
                  "strategies":[{
                    "code":"CUSTOM_TABLE_POLICY",
                    "when":{"hasTableContext":{"equals":"true"}},
                    "modelProfile":"TENANT_TABLE_MODEL",
                    "includeTables":true,
                    "maximumOutputTokens":3210,
                    "validationPolicy":"TENANT_STRICT",
                    "retryPolicy":"TENANT_REPAIR",
                    "secondModelValidation":true
                  }]
                }
                """)));
        PolicyExtractionStrategyResolver resolver =
            new PolicyExtractionStrategyResolver(catalog);
        AnalysisProfile profile = profile(organizationId, policyId);
        ClauseAnalysisContext context = new ClauseAnalysisContext(
            clause(), List.of(), Map.of("hasTableContext", true));

        var strategy = resolver.resolve(context, profile);

        assertThat(strategy.code()).isEqualTo("CUSTOM_TABLE_POLICY");
        assertThat(strategy.modelProfile()).isEqualTo("TENANT_TABLE_MODEL");
        assertThat(strategy.maximumOutputTokens()).isEqualTo(3210);
    }

    @Test
    void duplicateThresholdsAndWeightsAreNotCompiledConstants() throws Exception {
        PolicyDuplicateDetectionEngine engine = new PolicyDuplicateDetectionEngine();
        PolicyDocument policy = new PolicyDocument(UUID.randomUUID(), "duplicate",
            "EXTRACTION", mapper.readTree("""
                {
                  "duplicate":{
                    "possible":0.3,
                    "likely":0.9,
                    "weights":{"text":1.0,"concept":0.0,"attributes":0.0,"source":0.0}
                  }
                }
                """));
        DuplicateCandidate left = new DuplicateCandidate(UUID.randomUUID(),
            "alpha beta", null, mapper.createObjectNode(), UUID.randomUUID(),
            UUID.randomUUID());
        DuplicateCandidate right = new DuplicateCandidate(UUID.randomUUID(),
            "alpha gamma", null, mapper.createObjectNode(), UUID.randomUUID(),
            UUID.randomUUID());

        assertThat(engine.compare(left, right, policy).status())
            .isEqualTo("POSSIBLE_DUPLICATE");
    }

    @Test
    void evaluationQualityGatesAcceptNewMetricsWithoutCodeChanges() throws Exception {
        GenericEvaluationPolicyEngine engine = new GenericEvaluationPolicyEngine();
        var result = engine.evaluate(List.of(
                Map.of("grounding", 0.9, "tenantMetric", 0.8, "latency", 100d),
                Map.of("grounding", 1.0, "tenantMetric", 0.9, "latency", 200d)),
            mapper.readTree("""
                {
                  "minimums":{"grounding":0.9,"tenantMetric":0.8},
                  "maximums":{"latency":250}
                }
                """));

        assertThat(result.passed()).isTrue();
        assertThat(result.aggregateMetrics().get("tenantMetric")).isCloseTo(0.85,
            org.assertj.core.data.Offset.offset(0.000001));
    }

    private Clause clause() {
        return new Clause(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            "1", "Başlık", "Kaynak 500", "kaynak 500", "PARAGRAPH",
            1, 1, "[]", "a".repeat(64), 0, Instant.now());
    }

    private AnalysisProfile profile(UUID organizationId, UUID policyId) {
        return new AnalysisProfile(UUID.randomUUID(), organizationId, UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "[]", "{}",
            "[]", UUID.randomUUID(), policyId, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), "{}", "b".repeat(64), Instant.now());
    }
}
