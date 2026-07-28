package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.infrastructure.AnalysisPersistenceStore;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.risk.application.RiskModels.PropagationContext;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskReviewService {
    private final RiskPersistencePort store;
    private final RiskCatalogPort catalog;
    private final RiskPropagationEngine propagationEngine;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final AuditService audit;
    private final AnalysisPersistenceStore analysisStore;
    private final ObjectMapper mapper;

    public RiskReviewService(RiskPersistencePort store, RiskCatalogPort catalog,
                             RiskPropagationEngine propagationEngine,
                             ProjectAccessService access, CurrentTenant currentTenant,
                             AuditService audit, AnalysisPersistenceStore analysisStore,
                             ObjectMapper mapper) {
        this.store = store;
        this.catalog = catalog;
        this.propagationEngine = propagationEngine;
        this.access = access;
        this.currentTenant = currentTenant;
        this.audit = audit;
        this.analysisStore = analysisStore;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> risks(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        return store.listRisks(principal.tenantId(), projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> risk(UUID riskId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> risk = requiredRisk(principal.tenantId(), riskId);
        access.requireView(uuid(risk, "project_id"), principal);
        return detail(risk, Map.of(
            "sources", store.riskSources(principal.tenantId(), riskId),
            "factors", store.riskFactors(principal.tenantId(), riskId),
            "history", store.riskHistory(principal.tenantId(), riskId),
            "propagation", store.propagation(principal.tenantId(), riskId),
            "mitigations", store.mitigationCandidates(principal.tenantId(), riskId)));
    }

    @Transactional
    public Map<String, Object> reviewRisk(UUID riskId, String reviewStatus,
                                          String feedbackType, String reason,
                                          boolean approvedForLearning) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = requiredRisk(principal.tenantId(), riskId);
        UUID projectId = uuid(before, "project_id");
        access.requireView(projectId, principal);
        ProfileContext profile = profile(principal.tenantId(),
            uuid(before, "risk_analysis_profile_id"));
        UUID changeConcept = concept(principal.tenantId(), profile,
            "REVISION_CREATED");
        store.reviewRisk(principal.tenantId(), riskId, reviewStatus, changeConcept,
            principal.subject(), mapper.valueToTree(Map.of("reason", nullable(reason))));
        Map<String, Object> after = requiredRisk(principal.tenantId(), riskId);
        feedback(principal, projectId, "RISK", riskId, feedbackType, before, after,
            reason, approvedForLearning, profile);
        audit.record("RISK_REVIEWED", "RISK", riskId, before, after);
        return risk(riskId);
    }

    @Transactional
    public Map<String, Object> assignRisk(UUID riskId, String ownerUserId, LocalDate dueDate) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = requiredRisk(principal.tenantId(), riskId);
        access.requireView(uuid(before, "project_id"), principal);
        ProfileContext profile = profile(principal.tenantId(),
            uuid(before, "risk_analysis_profile_id"));
        store.assignRisk(principal.tenantId(), riskId, ownerUserId, dueDate,
            concept(principal.tenantId(), profile, "REVISION_CREATED"), principal.subject());
        Map<String, Object> after = requiredRisk(principal.tenantId(), riskId);
        audit.record("RISK_ASSIGNED", "RISK", riskId, before, after);
        return risk(riskId);
    }

    @Transactional
    public Map<String, Object> updateRisk(UUID riskId, String title, String description,
                                          Double probability, Double impact, Double exposure,
                                          UUID severityConceptId, UUID statusConceptId,
                                          String feedbackType, String reason) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = requiredRisk(principal.tenantId(), riskId);
        UUID projectId = uuid(before, "project_id");
        access.requireView(projectId, principal);
        ProfileContext profile = profile(principal.tenantId(),
            uuid(before, "risk_analysis_profile_id"));
        store.updateRisk(principal.tenantId(), riskId, title, description,
            probability, impact, exposure, severityConceptId, statusConceptId,
            concept(principal.tenantId(), profile, "REVISION_CREATED"), principal.subject());
        Map<String, Object> after = requiredRisk(principal.tenantId(), riskId);
        feedback(principal, projectId, "RISK", riskId, feedbackType, before, after,
            reason, false, profile);
        audit.record("RISK_UPDATED", "RISK", riskId, before, after);
        return risk(riskId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> riskExplanation(UUID riskId) {
        Map<String, Object> risk = risk(riskId);
        return detail(risk, Map.of(
            "explainability", Map.of(
                "policyBased", true,
                "finalDecision", false,
                "groundedSourceCount", ((List<?>) risk.get("sources")).size())));
    }

    @Transactional
    public List<Map<String, Object>> propagation(UUID riskId, boolean refresh) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> risk = requiredRisk(principal.tenantId(), riskId);
        UUID projectId = uuid(risk, "project_id");
        access.requireView(projectId, principal);
        if (refresh && store.propagation(principal.tenantId(), riskId).isEmpty()) {
            ProfileContext profile = profile(principal.tenantId(),
                uuid(risk, "risk_analysis_profile_id"));
            var policy = hydratePolicy(principal.tenantId(), profile,
                profile.inputs().impactPolicyVersionId());
            var candidates = propagationEngine.propagate(new PropagationContext(
                riskId, uuid(risk, "source_entity_id"),
                store.dependencyGraph(principal.tenantId(), projectId), Map.of()), policy);
            store.savePropagation(principal.tenantId(), riskId, candidates);
        }
        return store.propagation(principal.tenantId(), riskId);
    }

    @Transactional
    public List<Map<String, Object>> chooseMitigation(UUID riskId, UUID candidateId,
                                                      String reviewStatus) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> risk = requiredRisk(principal.tenantId(), riskId);
        access.requireView(uuid(risk, "project_id"), principal);
        store.reviewMitigation(principal.tenantId(), riskId, candidateId, reviewStatus);
        audit.record("MITIGATION_REVIEWED", "RISK", riskId, null,
            Map.of("candidateId", candidateId, "reviewStatus", reviewStatus));
        return store.mitigationCandidates(principal.tenantId(), riskId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ambiguities(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        return store.listAmbiguities(principal.tenantId(), projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ambiguity(UUID ambiguityId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> finding = requiredAmbiguity(principal.tenantId(), ambiguityId);
        access.requireView(uuid(finding, "project_id"), principal);
        return detail(finding, Map.of(
            "sources", store.ambiguitySources(principal.tenantId(), ambiguityId),
            "interpretations", store.interpretations(principal.tenantId(), ambiguityId)));
    }

    @Transactional
    public Map<String, Object> reviewAmbiguity(UUID ambiguityId, String reviewStatus,
                                               String feedbackType, String reason) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = requiredAmbiguity(principal.tenantId(), ambiguityId);
        UUID projectId = uuid(before, "project_id");
        access.requireView(projectId, principal);
        store.reviewAmbiguity(principal.tenantId(), ambiguityId, reviewStatus);
        Map<String, Object> after = requiredAmbiguity(principal.tenantId(), ambiguityId);
        ProfileContext profile = profile(principal.tenantId(),
            uuid(before, "analysis_profile_id"));
        feedback(principal, projectId, "AMBIGUITY", ambiguityId, feedbackType,
            before, after, reason, false, profile);
        audit.record("AMBIGUITY_REVIEWED", "AMBIGUITY", ambiguityId, before, after);
        return ambiguity(ambiguityId);
    }

    @Transactional
    public Map<String, Object> addInterpretation(UUID ambiguityId, String text,
                                                 JsonNode attributes, double confidence,
                                                 JsonNode sourceIds) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> finding = requiredAmbiguity(principal.tenantId(), ambiguityId);
        access.requireView(uuid(finding, "project_id"), principal);
        validateSources(sourceIds, store.ambiguitySources(principal.tenantId(), ambiguityId));
        UUID id = store.addInterpretation(principal.tenantId(), ambiguityId, text,
            attributes, confidence, sourceIds);
        audit.record("AMBIGUITY_INTERPRETATION_ADDED", "AMBIGUITY", ambiguityId,
            null, Map.of("interpretationId", id));
        return ambiguity(ambiguityId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> conflicts(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        return store.listConflicts(principal.tenantId(), projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> conflict(UUID conflictId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> conflict = requiredConflict(principal.tenantId(), conflictId);
        access.requireView(uuid(conflict, "project_id"), principal);
        return detail(conflict, Map.of(
            "sources", store.conflictSources(principal.tenantId(), conflictId),
            "factors", store.conflictFactors(principal.tenantId(), conflictId),
            "history", store.conflictHistory(principal.tenantId(), conflictId)));
    }

    @Transactional
    public Map<String, Object> reviewConflict(UUID conflictId, String reviewStatus,
                                              String feedbackType, String reason,
                                              JsonNode resolution) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = requiredConflict(principal.tenantId(), conflictId);
        UUID projectId = uuid(before, "project_id");
        access.requireView(projectId, principal);
        ProfileContext profile = profile(principal.tenantId(),
            uuid(before, "analysis_profile_id"));
        store.reviewConflict(principal.tenantId(), conflictId, reviewStatus,
            concept(principal.tenantId(), profile, "REVISION_CREATED"),
            principal.subject(), resolution);
        Map<String, Object> after = requiredConflict(principal.tenantId(), conflictId);
        feedback(principal, projectId, "CONFLICT", conflictId, feedbackType,
            before, after, reason, false, profile);
        audit.record("CONFLICT_REVIEWED", "CONFLICT", conflictId, before, after);
        return conflict(conflictId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> conflictExplanation(UUID conflictId) {
        Map<String, Object> conflict = conflict(conflictId);
        return detail(conflict, Map.of(
            "groundingRule", "Both persisted tenant-scoped sources are required",
            "authorityIsPolicyBound", true,
            "finalDecision", false));
    }

    @Transactional
    public Map<String, Object> clarification(String entityType, UUID entityId,
                                             String question, String reason,
                                             JsonNode sourceIds, UUID priorityConceptId,
                                             boolean legalReview) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> entity;
        List<Map<String, Object>> sources;
        if ("AMBIGUITY".equals(entityType)) {
            entity = requiredAmbiguity(principal.tenantId(), entityId);
            sources = store.ambiguitySources(principal.tenantId(), entityId);
        } else if ("CONFLICT".equals(entityType)) {
            entity = requiredConflict(principal.tenantId(), entityId);
            sources = store.conflictSources(principal.tenantId(), entityId);
        } else {
            throw new IllegalArgumentException("Unsupported clarification source entity");
        }
        UUID projectId = uuid(entity, "project_id");
        access.requireView(projectId, principal);
        validateSources(sourceIds, sources);
        UUID id = store.createClarificationCandidate(principal.tenantId(), projectId,
            entityType, entityId, catalog.activeClarificationStrategy(principal.tenantId()),
            question, reason, sourceIds, priorityConceptId, legalReview);
        audit.record("CLARIFICATION_CANDIDATE_CREATED", entityType, entityId,
            null, Map.of("candidateId", id));
        return Map.of("id", id, "reviewStatus", "CANDIDATE",
            "requiresHumanApproval", true);
    }

    private void validateSources(JsonNode sourceIds, List<Map<String, Object>> sources) {
        Set<String> allowed = sources.stream().map(source ->
            String.valueOf(source.get("id"))).collect(Collectors.toSet());
        if (!sourceIds.isArray() || sourceIds.isEmpty()) {
            throw new IllegalArgumentException("Grounded candidate requires source IDs");
        }
        sourceIds.forEach(source -> {
            if (!allowed.contains(source.asText())) {
                throw new IllegalArgumentException("Unknown or out-of-scope source ID");
            }
        });
    }

    private ProfileContext profile(UUID organizationId, UUID riskProfileId) {
        return new ProfileContext(riskProfileId, catalog.profile(organizationId, riskProfileId));
    }

    private RiskModels.VersionedPolicy hydratePolicy(UUID organizationId,
                                                      ProfileContext profile,
                                                      UUID policyId) {
        var policy = catalog.policy(organizationId, policyId);
        JsonNode copy = policy.configuration().deepCopy();
        if (copy.isObject()) {
            ObjectNodeHydrator.hydrate((com.fasterxml.jackson.databind.node.ObjectNode) copy,
                code -> catalog.conceptId(organizationId,
                    profile.inputs().ontologyVersionId(), code).orElse(null));
        }
        return new RiskModels.VersionedPolicy(policy.id(), policy.code(), copy);
    }

    private UUID concept(UUID organizationId, ProfileContext profile, String code) {
        return catalog.conceptId(organizationId, profile.inputs().ontologyVersionId(), code)
            .orElseThrow(() -> new IllegalArgumentException(
                "Required workflow concept is missing: " + code));
    }

    private void feedback(TenantPrincipal principal, UUID projectId, String entityType,
                          UUID entityId, String feedbackType, Object before, Object after,
                          String reason, boolean approved, ProfileContext profile) {
        String type = feedbackType == null || feedbackType.isBlank()
            ? "EXPERT_CORRECTION" : feedbackType;
        var inputs = profile.inputs();
        analysisStore.feedback(principal.tenantId(), projectId, entityType, entityId,
            type, before, after, reason, principal.subject(), inputs.analysisProfileId(),
            inputs.ontologyVersionId(), inputs.riskPolicyVersionId(),
            inputs.promptPackageVersionId(), null, approved, Instant.now());
    }

    private Map<String, Object> requiredRisk(UUID organizationId, UUID riskId) {
        return store.risk(organizationId, riskId)
            .orElseThrow(() -> new IllegalArgumentException("Risk not found"));
    }

    private Map<String, Object> requiredAmbiguity(UUID organizationId, UUID id) {
        return store.ambiguity(organizationId, id)
            .orElseThrow(() -> new IllegalArgumentException("Ambiguity finding not found"));
    }

    private Map<String, Object> requiredConflict(UUID organizationId, UUID id) {
        return store.conflict(organizationId, id)
            .orElseThrow(() -> new IllegalArgumentException("Conflict not found"));
    }

    private Map<String, Object> detail(Map<String, Object> base,
                                       Map<String, Object> additions) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        result.putAll(additions);
        return result;
    }

    private static UUID uuid(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private record ProfileContext(UUID id, RiskModels.RiskProfileInputs inputs) {
    }

    private static final class ObjectNodeHydrator {
        private ObjectNodeHydrator() {
        }

        static void hydrate(com.fasterxml.jackson.databind.node.ObjectNode node,
                            java.util.function.Function<String, UUID> resolver) {
            List<String> names = new java.util.ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = node.path(name);
                if (name.endsWith("ConceptCode") && value.isTextual()) {
                    UUID id = resolver.apply(value.asText());
                    if (id != null) {
                        node.put(name.substring(0,
                            name.length() - "ConceptCode".length()) + "ConceptId",
                            id.toString());
                    }
                }
                if (value.isObject()) {
                    hydrate((com.fasterxml.jackson.databind.node.ObjectNode) value, resolver);
                } else if (value.isArray()) {
                    value.forEach(child -> {
                        if (child.isObject()) {
                            hydrate((com.fasterxml.jackson.databind.node.ObjectNode) child,
                                resolver);
                        }
                    });
                }
            }
        }
    }
}
