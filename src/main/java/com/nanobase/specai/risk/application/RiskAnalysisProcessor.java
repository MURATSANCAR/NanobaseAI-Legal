package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictCandidateContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonContext;
import com.nanobase.specai.risk.application.RiskModels.ConflictEntity;
import com.nanobase.specai.risk.application.RiskModels.PropagationContext;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureContext;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalContext;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import com.nanobase.specai.risk.application.RiskPersistencePort.RequirementCandidate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAnalysisProcessor {
    private final RiskCatalogPort catalog;
    private final RiskPersistencePort store;
    private final RiskSignalEngine signalEngine;
    private final RiskExposurePolicyEngine exposureEngine;
    private final RiskConfidencePolicyEngine confidenceEngine;
    private final AmbiguityAnalysisEngine ambiguityEngine;
    private final ConflictCandidateGenerator candidateGenerator;
    private final ConflictStrategyRegistry conflictStrategies;
    private final RiskPropagationEngine propagationEngine;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    public RiskAnalysisProcessor(RiskCatalogPort catalog, RiskPersistencePort store,
                                 RiskSignalEngine signalEngine,
                                 RiskExposurePolicyEngine exposureEngine,
                                 RiskConfidencePolicyEngine confidenceEngine,
                                 AmbiguityAnalysisEngine ambiguityEngine,
                                 ConflictCandidateGenerator candidateGenerator,
                                 ConflictStrategyRegistry conflictStrategies,
                                 RiskPropagationEngine propagationEngine,
                                 OutboxService outbox, ObjectMapper mapper) {
        this.catalog = catalog;
        this.store = store;
        this.signalEngine = signalEngine;
        this.exposureEngine = exposureEngine;
        this.confidenceEngine = confidenceEngine;
        this.ambiguityEngine = ambiguityEngine;
        this.candidateGenerator = candidateGenerator;
        this.conflictStrategies = conflictStrategies;
        this.propagationEngine = propagationEngine;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public void process(UUID organizationId, UUID jobId) {
        try {
            Map<String, Object> job = store.job(organizationId, jobId)
                .orElseThrow(() -> new IllegalArgumentException("Risk analysis job not found"));
            UUID projectId = (UUID) job.get("projectId");
            UUID profileId = (UUID) job.get("riskAnalysisProfileId");
            RiskProfileInputs profile = catalog.profile(organizationId, profileId);
            VersionedPolicy riskPolicy = hydrated(organizationId, profile.ontologyVersionId(),
                catalog.policy(organizationId, profile.riskPolicyVersionId()));
            VersionedPolicy ambiguityPolicy = hydrated(organizationId, profile.ontologyVersionId(),
                catalog.policy(organizationId, profile.ambiguityPolicyVersionId()));
            VersionedPolicy confidencePolicy = hydrated(organizationId,
                profile.ontologyVersionId(),
                catalog.policy(organizationId, profile.confidencePolicyVersionId()));
            VersionedPolicy conflictPolicy = hydrated(organizationId, profile.ontologyVersionId(),
                catalog.policy(organizationId, profile.conflictPolicyVersionId()));
            VersionedPolicy impactPolicy = hydrated(organizationId, profile.ontologyVersionId(),
                catalog.policy(organizationId, profile.impactPolicyVersionId()));
            ObjectNode riskConfiguration = riskPolicy.configuration().deepCopy();
            riskConfiguration.set("severityLevels",
                hydrateConcepts(organizationId, profile.ontologyVersionId(),
                    catalog.severityPolicy(organizationId, profile.severityPolicyVersionId())));
            riskPolicy = new VersionedPolicy(riskPolicy.id(), riskPolicy.code(), riskConfiguration);
            JsonNode authority = catalog.authorityPolicy(organizationId,
                profile.documentAuthorityPolicyId());
            List<RequirementCandidate> requirements = store.requirements(organizationId, projectId);
            store.startJob(organizationId, jobId, requirements.size());
            store.event(organizationId, jobId, "STARTED", 0,
                "Risk analysis started", mapper.createObjectNode());
            outbox.publish(organizationId, "RiskAnalysis", jobId,
                "risk.analysis.started.v1", RabbitConfiguration.RISK_ANALYSIS_STARTED,
                Map.of("jobId", jobId, "projectId", projectId), null);
            int riskCount = 0;
            int ambiguityCount = 0;
            int conflictCount = 0;
            int failures = 0;
            List<CreatedRisk> createdRisks = new ArrayList<>();
            Set<String> comparedPairs = new HashSet<>();
            for (int index = 0; index < requirements.size(); index++) {
                RequirementCandidate source = requirements.get(index);
                try {
                    long evidenceCount = store.validEvidenceCount(organizationId, source.id());
                    Map<String, Double> signals = signals(
                        riskPolicy.configuration(), source, evidenceCount);
                    var signal = signalEngine.evaluate(new RiskSignalContext(
                        organizationId, projectId, "REQUIREMENT", source.id(), signals,
                        Map.of("evidenceCount", evidenceCount)), riskPolicy);
                    if (signal.requiresDetailedAnalysis()) {
                        var exposure = exposureEngine.calculate(
                            new RiskExposureContext(signals, Map.of()), riskPolicy);
                        var selected = signal.candidateRiskConcepts().getFirst();
                        UUID status = requiredConcept(organizationId, profile.ontologyVersionId(),
                            riskPolicy.configuration().path("reviewStatusConceptCode").asText());
                        UUID sourceRole = requiredConcept(organizationId, profile.ontologyVersionId(),
                            riskPolicy.configuration().path("sourceRoleConceptCode").asText());
                        UUID riskId = store.createRisk(organizationId, projectId, profileId,
                            source, selected.conceptId(), status, sourceRole, signal, exposure,
                            confidenceEngine.calculate(confidenceInputs(source,
                                signal.signalScore()), confidencePolicy));
                        createdRisks.add(new CreatedRisk(riskId, source.id()));
                        riskCount++;
                    }
                    Map<String, Double> ambiguityFeatures =
                        ambiguityFeatures(ambiguityPolicy.configuration(), source);
                    var ambiguity = ambiguityEngine.analyze(
                        new AmbiguityContext(source.id(), ambiguityFeatures, Map.of()),
                        ambiguityPolicy);
                    if (ambiguity.finding() && ambiguity.conceptId() != null) {
                        store.createAmbiguity(organizationId, projectId, profileId,
                            source, ambiguity, null);
                        ambiguityCount++;
                    }
                    int retrievalLimit = Math.max(1,
                        conflictPolicy.configuration().path("retrievalLimit").asInt(250));
                    List<ConflictEntity> retrieved = store.conflictCandidates(
                        organizationId, projectId, source, retrievalLimit);
                    ConflictEntity seed = new ConflictEntity(source.id(), "REQUIREMENT",
                        source.documentId(), source.documentVersionId(), source.conceptId(),
                        "PROJECT:" + projectId, source.attributes(), source.text(),
                        source.pageNumber());
                    var generated = candidateGenerator.generate(
                        new ConflictCandidateContext(seed, retrieved, Map.of()), conflictPolicy);
                    for (var candidate : generated.candidates()) {
                        String pair = orderedPair(candidate.left().id(), candidate.right().id());
                        if (!comparedPairs.add(pair)) {
                            continue;
                        }
                        try {
                            var comparison = conflictStrategies.compare(
                                new ConflictComparisonContext(candidate,
                                    conflictPolicy.configuration(), authority));
                            if (!comparison.conflict()) {
                                continue;
                            }
                            UUID concept = requiredConcept(organizationId,
                                profile.ontologyVersionId(), comparison.conflictConceptCode());
                            UUID status = requiredConcept(organizationId,
                                profile.ontologyVersionId(),
                                conflictPolicy.configuration()
                                    .path("reviewStatusConceptCode").asText());
                            UUID leftSide = requiredConcept(organizationId,
                                profile.ontologyVersionId(),
                                conflictPolicy.configuration().path("leftSideConceptCode").asText());
                            UUID rightSide = requiredConcept(organizationId,
                                profile.ontologyVersionId(),
                                conflictPolicy.configuration().path("rightSideConceptCode").asText());
                            store.createConflict(organizationId, projectId, profileId, source,
                                candidate.right(), concept, status, leftSide, rightSide,
                                comparison);
                            conflictCount++;
                        } catch (IllegalArgumentException unsupported) {
                            // A policy-selected pair without a registered deterministic
                            // provider is left for model/manual analysis; no result is invented.
                        }
                    }
                } catch (RuntimeException candidateFailure) {
                    failures++;
                }
                int progress = requirements.isEmpty() ? 100
                    : (int) Math.floor(100d * (index + 1) / requirements.size());
                store.event(organizationId, jobId, "PROGRESS", progress,
                    "Risk analysis progress",
                    mapper.valueToTree(Map.of("processed", index + 1,
                        "total", requirements.size())));
            }
            var graph = store.dependencyGraph(organizationId, projectId);
            for (CreatedRisk created : createdRisks) {
                var propagated = propagationEngine.propagate(
                    new PropagationContext(created.riskId(), created.sourceEntityId(),
                        graph, Map.of()), impactPolicy);
                store.savePropagation(organizationId, created.riskId(), propagated);
            }
            int manualReviews = riskCount + ambiguityCount + conflictCount;
            store.finishJob(organizationId, jobId, requirements.size(), riskCount,
                ambiguityCount, conflictCount, manualReviews, failures);
            store.event(organizationId, jobId, "COMPLETED", 100,
                "Risk analysis completed", mapper.valueToTree(Map.of(
                    "risks", riskCount, "ambiguities", ambiguityCount,
                    "conflicts", conflictCount, "failures", failures)));
            outbox.publish(organizationId, "RiskAnalysis", jobId,
                "risk.analysis.completed.v1", RabbitConfiguration.RISK_ANALYSIS_COMPLETED,
                Map.of("jobId", jobId, "risks", riskCount,
                    "ambiguities", ambiguityCount, "conflicts", conflictCount), null);
        } catch (RuntimeException failure) {
            store.failJob(organizationId, jobId, failure.getClass().getSimpleName());
            outbox.publish(organizationId, "RiskAnalysis", jobId,
                "risk.analysis.failed.v1", RabbitConfiguration.RISK_ANALYSIS_FAILED,
                Map.of("jobId", jobId, "errorCode", failure.getClass().getSimpleName()), null);
            // Do not rethrow: that would roll back FAILED and leave the job stuck.
        }
    }

    private VersionedPolicy hydrated(UUID organizationId, UUID ontologyVersionId,
                                     VersionedPolicy policy) {
        return new VersionedPolicy(policy.id(), policy.code(),
            hydrateConcepts(organizationId, ontologyVersionId, policy.configuration()));
    }

    private JsonNode hydrateConcepts(UUID organizationId, UUID ontologyVersionId,
                                     JsonNode source) {
        JsonNode copy = source.deepCopy();
        hydrateNode(organizationId, ontologyVersionId, copy);
        return copy;
    }

    private void hydrateNode(UUID organizationId, UUID ontologyVersionId, JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.hasNonNull("conceptCode") && !object.hasNonNull("conceptId")) {
                catalog.conceptId(organizationId, ontologyVersionId,
                    object.path("conceptCode").asText())
                    .ifPresent(id -> object.put("conceptId", id.toString()));
            }
            List<String> fieldNames = new ArrayList<>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                if (fieldName.endsWith("ConceptCode") && object.path(fieldName).isTextual()) {
                    String idField = fieldName.substring(0,
                        fieldName.length() - "ConceptCode".length()) + "ConceptId";
                    catalog.conceptId(organizationId, ontologyVersionId,
                        object.path(fieldName).asText())
                        .ifPresent(id -> object.put(idField, id.toString()));
                }
            }
            fieldNames.forEach(fieldName ->
                hydrateNode(organizationId, ontologyVersionId, object.path(fieldName)));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child ->
                hydrateNode(organizationId, ontologyVersionId, child));
        }
    }

    private Map<String, Double> signals(JsonNode configuration,
                                        RequirementCandidate source,
                                        long evidenceCount) {
        Map<String, Double> raw = new HashMap<>();
        raw.put("groundingCoverage", source.groundingCoverage());
        raw.put("requirementConfidence", source.confidence());
        raw.put("testabilityPresent",
            source.testabilityStatus() == null || source.testabilityStatus().isBlank() ? 0d : 1d);
        raw.put("validEvidencePresent", evidenceCount > 0 ? 1d : 0d);
        Map<String, Double> result = new HashMap<>();
        configuration.path("signalSources").fields().forEachRemaining(field -> {
            JsonNode descriptor = field.getValue();
            double value = raw.getOrDefault(descriptor.path("source").asText(), 0d);
            if ("ONE_MINUS".equals(descriptor.path("transform").asText())) {
                value = 1 - value;
            }
            result.put(field.getKey(), Math.max(0, Math.min(1, value)));
        });
        return Map.copyOf(result);
    }

    private Map<String, Double> ambiguityFeatures(JsonNode configuration,
                                                  RequirementCandidate source) {
        Map<String, Double> result = new HashMap<>();
        configuration.path("featureSources").fields().forEachRemaining(field -> {
            String pointer = field.getValue().path("jsonPointer").asText();
            JsonNode value = source.attributes().at(pointer);
            boolean missing = value.isMissingNode() || value.isNull()
                || (value.isTextual() && value.asText().isBlank());
            result.put(field.getKey(), missing ? 1d : 0d);
        });
        return Map.copyOf(result);
    }

    private Map<String, Double> confidenceInputs(RequirementCandidate source,
                                                 double signalScore) {
        return Map.of(
            "groundingCoverage", source.groundingCoverage(),
            "schemaValidation", 1d,
            "parserQuality", source.attributes().path("parserQuality").asDouble(0),
            "ocrQuality", source.attributes().path("ocrQuality").asDouble(0),
            "clauseSignal", signalScore,
            "ontologyMatch", source.conceptId() == null ? 0d : 1d,
            "unitValidation", source.attributes().path("unitValidation").asDouble(0));
    }

    private UUID requiredConcept(UUID organizationId, UUID ontologyVersionId, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Policy concept code is missing");
        }
        return catalog.conceptId(organizationId, ontologyVersionId, code)
            .orElseThrow(() -> new IllegalArgumentException(
                "Policy references unknown ontology concept " + code));
    }

    private String orderedPair(UUID left, UUID right) {
        return left.compareTo(right) < 0 ? left + ":" + right : right + ":" + left;
    }

    private record CreatedRisk(UUID riskId, UUID sourceEntityId) {
    }
}
