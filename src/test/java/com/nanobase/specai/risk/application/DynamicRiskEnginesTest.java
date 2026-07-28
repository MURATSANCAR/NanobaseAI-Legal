package com.nanobase.specai.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.risk.application.DocumentChangeMatcher.ClauseSnapshot;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityContext;
import com.nanobase.specai.risk.application.RiskModels.ChangeItem;
import com.nanobase.specai.risk.application.RiskModels.ChangeSet;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidate;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidateContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictEntity;
import com.nanobase.specai.risk.application.RiskModels.ImpactAnalysisContext;
import com.nanobase.specai.risk.application.RiskModels.ImpactGraphEdge;
import com.nanobase.specai.risk.application.RiskModels.PropagationContext;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalContext;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DynamicRiskEnginesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void riskSignalsAndConceptMappingComeFromPolicy() throws Exception {
        UUID conceptId = UUID.randomUUID();
        VersionedPolicy policy = policy("""
            {
              "weights":{"evidenceGap":0.7,"confidenceGap":0.3},
              "detailedAnalysisThreshold":0.4,
              "conceptMappings":[{
                "signal":"evidenceGap","minimum":0.5,
                "conceptId":"%s","conceptCode":"TENANT_EVIDENCE_EXPOSURE",
                "scoreMultiplier":0.8,"reasonCodes":["NO_VALID_EVIDENCE"]
              }]
            }
            """.formatted(conceptId));
        var result = new ConfigurableRiskSignalEngine().evaluate(
            new RiskSignalContext(UUID.randomUUID(), UUID.randomUUID(), "REQUIREMENT",
                UUID.randomUUID(), Map.of("evidenceGap", 1d, "confidenceGap", .5),
                Map.of()), policy);

        assertThat(result.signalScore()).isEqualTo(.85);
        assertThat(result.requiresDetailedAnalysis()).isTrue();
        assertThat(result.candidateRiskConcepts().getFirst().conceptId())
            .isEqualTo(conceptId);
        assertThat(result.candidateRiskConcepts().getFirst().reasonCodes())
            .containsExactly("NO_VALID_EVIDENCE");
    }

    @Test
    void weightedExposureAndSeverityArePolicyDrivenAndExplainable() throws Exception {
        UUID severityId = UUID.randomUUID();
        var provider = new WeightedExposureMethodProvider();
        var engine = new ConfigurableRiskExposurePolicyEngine(List.of(provider));
        VersionedPolicy policy = policy("""
            {
              "exposureMethod":"WEIGHTED_SUM",
              "probabilityWeights":{"history":1.0},
              "impactWeights":{"mandatory":1.0},
              "exposureWeights":{"probability":0.25,"impact":0.75},
              "severityLevels":[
                {"minimum":0.8,"conceptId":"%s","conceptCode":"TENANT_BLOCKER"},
                {"minimum":0.0,"conceptCode":"TENANT_WATCH"}
              ]
            }
            """.formatted(severityId));

        var result = engine.calculate(
            new RiskExposureContext(Map.of("history", .6, "mandatory", .9), Map.of()),
            policy);

        assertThat(result.exposure()).isCloseTo(.825,
            org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.severityConceptId()).isEqualTo(severityId);
        assertThat(result.probability().factors()).extracting("code")
            .containsExactly("history");
    }

    @Test
    void riskConfidenceUsesVersionedWeightsAndExplainsEffects() throws Exception {
        var result = new ConfigurableRiskConfidencePolicyEngine().calculate(
            Map.of("grounding", .8, "authority", .4),
            policy("""
                {"weights":{"grounding":0.75,"authority":0.25},"reviewBelow":0.9}
                """));

        assertThat(result.score()).isCloseTo(.7,
            org.assertj.core.data.Offset.offset(0.000001));
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.factors()).extracting("code")
            .containsExactly("grounding", "authority");
    }

    @Test
    void ambiguityUsesStructuredFeaturePolicyInsteadOfWordList() throws Exception {
        UUID conceptId = UUID.randomUUID();
        VersionedPolicy policy = policy("""
            {
              "features":{"missingThreshold":0.8,"semanticUncertainty":0.2},
              "findingThreshold":0.5,
              "fallbackConcept":{"conceptId":"%s","conceptCode":"TENANT_ACCEPTANCE_GAP"}
            }
            """.formatted(conceptId));

        var result = new ConfigurableAmbiguityAnalysisEngine().analyze(
            new AmbiguityContext(UUID.randomUUID(),
                Map.of("missingThreshold", 1d, "semanticUncertainty", .4), Map.of()),
            policy);

        assertThat(result.finding()).isTrue();
        assertThat(result.conceptId()).isEqualTo(conceptId);
        assertThat(result.missingFields()).contains("missingThreshold");
    }

    @Test
    void candidateGenerationFiltersScopeAndReranksBeforeComparison() throws Exception {
        UUID concept = UUID.randomUUID();
        ConflictEntity seed = entity(concept, "PROJECT:A", 36);
        ConflictEntity sameScope = entity(concept, "PROJECT:A", 24);
        ConflictEntity otherScope = entity(concept, "PROJECT:B", 12);
        VersionedPolicy policy = policy("""
            {"candidateLimit":5,"minimumRetrievalScore":0.5,
             "stageWeights":{"entityScope":0.25,"ontologyConcept":0.30,
               "attribute":0.30,"version":0.15}}
            """);

        var result = new StagedConflictCandidateGenerator().generate(
            new ConflictCandidateContext(seed, List.of(sameScope, otherScope), Map.of()),
            policy);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().right().id()).isEqualTo(sameScope.id());
        assertThat(result.candidates().getFirst().completedStages())
            .contains("ENTITY_SCOPE", "ONTOLOGY_CONCEPT", "ATTRIBUTE", "RERANK");
    }

    @Test
    void deterministicStructuredConflictKeepsBothGroundedSources() throws Exception {
        UUID concept = UUID.randomUUID();
        ConflictEntity left = entity(concept, "PROJECT:A", 36);
        ConflictEntity right = entity(concept, "PROJECT:A", 24);
        var candidate = new ConflictCandidate(left, right, .94, List.of("RERANK"));
        var result = new StructuredValueConflictStrategy().compare(
            new ConflictComparisonContext(candidate, mapper.readTree("""
                {"structuredRules":[{
                  "path":"/value","strategyCode":"TENANT_NUMERIC_PROVIDER",
                  "conflictConceptCode":"TENANT_DURATION_CONFLICT",
                  "description":"Configured values differ.","tolerance":0
                }]}
                """), mapper.readTree("""
                {"rules":[],"onUnknown":"MANUAL_REVIEW"}
                """)));

        assertThat(result.conflict()).isTrue();
        assertThat(result.conflictConceptCode()).isEqualTo("TENANT_DURATION_CONFLICT");
        assertThat(result.supportingSourceIds()).containsExactly(left.id(), right.id());
        assertThat(result.requiresManualReview()).isTrue();
    }

    @Test
    void impactAndPropagationTraverseOnlyConfiguredDepthAndConfidence() throws Exception {
        UUID impactConcept = UUID.randomUUID();
        UUID propagationConcept = UUID.randomUUID();
        UUID dependency = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID requirement = UUID.randomUUID();
        UUID evaluation = UUID.randomUUID();
        List<ImpactGraphEdge> graph = List.of(
            new ImpactGraphEdge("CLAUSE", source, "REQUIREMENT", requirement,
                dependency, .9),
            new ImpactGraphEdge("REQUIREMENT", requirement, "COMPLIANCE_EVALUATION",
                evaluation, dependency, .8));
        VersionedPolicy policy = policy("""
            {
              "maximumDepth":2,"minimumConfidence":0.5,
              "impactConceptId":"%s","impactConceptCode":"TENANT_REANALYZE",
              "propagationConceptId":"%s","propagationConceptCode":"TENANT_PROPAGATED"
            }
            """.formatted(impactConcept, propagationConcept));
        ChangeSet changeSet = new ChangeSet(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), List.of(new ChangeItem(UUID.randomUUID(), UUID.randomUUID(),
                "CLAUSE", source, "CLAUSE", source, 1)));

        var impact = new GraphImpactAnalysisEngine().analyze(changeSet,
            new ImpactAnalysisContext(graph, Map.of()), policy);
        var propagation = new GraphRiskPropagationEngine().propagate(
            new PropagationContext(UUID.randomUUID(), source, graph, Map.of()), policy);

        assertThat(impact.affectedEntities()).extracting("entityId")
            .contains(source, requirement, evaluation);
        assertThat(propagation).extracting("targetEntityId")
            .contains(requirement, evaluation);
        assertThat(propagation).allMatch(candidate ->
            "TENANT_PROPAGATED".equals(candidate.propagationConceptCode()));
    }

    @Test
    void structuralChangeMatcherUsesPolicyThresholdAndConceptCodes() throws Exception {
        ClauseSnapshot oldClause = new ClauseSnapshot(UUID.randomUUID(), "4.1",
            "Warranty", "warranty is thirty six months", "a".repeat(64), 1);
        ClauseSnapshot newClause = new ClauseSnapshot(UUID.randomUUID(), "4.1",
            "Warranty", "warranty is twenty four months", "b".repeat(64), 1);
        VersionedPolicy policy = policy("""
            {"changeMatching":{
              "minimumSimilarity":0.4,
              "addedConceptCode":"TENANT_ADD","removedConceptCode":"TENANT_REMOVE",
              "modifiedConceptCode":"TENANT_CHANGE","unchangedConceptCode":"TENANT_SAME"
            }}
            """);

        var result = new PolicyDocumentChangeMatcher().match(
            List.of(oldClause), List.of(newClause), policy);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().changeConceptCode()).isEqualTo("TENANT_CHANGE");
    }

    private VersionedPolicy policy(String json) throws Exception {
        return new VersionedPolicy(UUID.randomUUID(), "test", mapper.readTree(json));
    }

    private ConflictEntity entity(UUID concept, String scope, int value) {
        return new ConflictEntity(UUID.randomUUID(), "REQUIREMENT", UUID.randomUUID(),
            UUID.randomUUID(), concept, scope,
            mapper.valueToTree(Map.of("value", value)), String.valueOf(value), 1);
    }
}
