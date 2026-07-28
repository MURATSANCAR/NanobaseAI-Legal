package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.risk.application.DocumentChangeMatcher.ClauseSnapshot;
import com.nanobase.specai.risk.application.RiskModels.ChangeSet;
import com.nanobase.specai.risk.application.RiskModels.ImpactAnalysisContext;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeImpactService {
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final ClauseRepository clauses;
    private final DocumentChangeMatcher matcher;
    private final ImpactAnalysisEngine impactEngine;
    private final RiskCatalogPort catalog;
    private final RiskPersistencePort riskStore;
    private final ChangeImpactPersistencePort store;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public ChangeImpactService(DocumentRepository documents,
                               DocumentVersionRepository versions,
                               ClauseRepository clauses,
                               DocumentChangeMatcher matcher,
                               ImpactAnalysisEngine impactEngine,
                               RiskCatalogPort catalog,
                               RiskPersistencePort riskStore,
                               ChangeImpactPersistencePort store,
                               ProjectAccessService access,
                               CurrentTenant currentTenant,
                               OutboxService outbox,
                               AuditService audit,
                               ObjectMapper mapper) {
        this.documents = documents;
        this.versions = versions;
        this.clauses = clauses;
        this.matcher = matcher;
        this.impactEngine = impactEngine;
        this.catalog = catalog;
        this.riskStore = riskStore;
        this.store = store;
        this.access = access;
        this.currentTenant = currentTenant;
        this.outbox = outbox;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> create(UUID documentId, UUID baseVersionId,
                                      UUID targetVersionId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndOrganizationId(documentId, principal.tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        access.requireDocumentView(document.projectId(), principal);
        DocumentVersion base = requiredVersion(baseVersionId, documentId, principal.tenantId());
        DocumentVersion target = requiredVersion(targetVersionId, documentId, principal.tenantId());
        if (base.id().equals(target.id())) {
            throw new IllegalArgumentException("Change set requires two distinct versions");
        }
        RiskProfileInputs inputs = catalog.resolveProfileInputs(
            principal.tenantId(), document.projectId());
        VersionedPolicy policy = hydrate(principal.tenantId(), inputs.ontologyVersionId(),
            catalog.policy(principal.tenantId(), inputs.impactPolicyVersionId()));
        UUID changeSetId = store.createChangeSet(principal.tenantId(), document.projectId(),
            base.id(), target.id(), policy.id());
        List<DocumentChangeMatcher.Match> matches = matcher.match(
            snapshots(base.id(), principal.tenantId()),
            snapshots(target.id(), principal.tenantId()), policy);
        store.saveMatches(principal.tenantId(), changeSetId, matches,
            code -> catalog.conceptId(principal.tenantId(),
                inputs.ontologyVersionId(), code).orElse(null));
        Map<String, Long> counts = matches.stream().collect(Collectors.groupingBy(
            DocumentChangeMatcher.Match::changeConceptCode, Collectors.counting()));
        store.completeChangeSet(principal.tenantId(), changeSetId,
            mapper.valueToTree(Map.of("total", matches.size(), "counts", counts)));
        outbox.publish(principal.tenantId(), "DocumentChangeSet", changeSetId,
            "document.change-set.created.v1", RabbitConfiguration.DOCUMENT_CHANGE_SET_CREATED,
            Map.of("changeSetId", changeSetId, "documentId", documentId,
                "baseVersionId", base.id(), "targetVersionId", target.id()),
            RequestContext.current().correlationId());
        audit.record("DOCUMENT_CHANGE_SET_CREATED", "DOCUMENT_CHANGE_SET",
            changeSetId, null, Map.of("itemCount", matches.size()));
        return changeSet(changeSetId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> changeSet(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> changeSet = store.changeSet(principal.tenantId(), id);
        access.requireView(uuid(changeSet, "projectId"), principal);
        Map<String, Object> result = new LinkedHashMap<>(changeSet);
        result.put("items", store.changeItems(principal.tenantId(), id));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> items(UUID id) {
        changeSet(id);
        return store.changeItems(currentTenant.require().tenantId(), id);
    }

    @Transactional
    public Map<String, Object> correct(UUID changeSetId, UUID itemId,
                                       UUID baseClauseId, UUID targetClauseId,
                                       UUID changeTypeConceptId, String reviewStatus,
                                       JsonNode attributes) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> changeSet = store.changeSet(principal.tenantId(), changeSetId);
        access.requireView(uuid(changeSet, "projectId"), principal);
        store.correctMatch(principal.tenantId(), itemId, baseClauseId,
            targetClauseId, changeTypeConceptId, reviewStatus, attributes);
        audit.record("DOCUMENT_CHANGE_MATCH_CORRECTED", "DOCUMENT_CHANGE_ITEM",
            itemId, null, Map.of("reviewStatus", reviewStatus));
        return changeSet(changeSetId);
    }

    @Transactional
    public Map<String, Object> analyze(UUID changeSetId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> persisted = store.changeSet(principal.tenantId(), changeSetId);
        UUID projectId = uuid(persisted, "projectId");
        access.requireView(projectId, principal);
        RiskProfileInputs inputs = catalog.resolveProfileInputs(principal.tenantId(), projectId);
        VersionedPolicy policy = hydrate(principal.tenantId(), inputs.ontologyVersionId(),
            catalog.policy(principal.tenantId(), inputs.impactPolicyVersionId()));
        var items = store.changeItemModels(principal.tenantId(), changeSetId);
        UUID jobId = store.createImpactJob(principal.tenantId(), projectId, changeSetId,
            policy.id(), items.size());
        ChangeSet changeSet = new ChangeSet(changeSetId, principal.tenantId(),
            projectId, items);
        var result = impactEngine.analyze(changeSet,
            new ImpactAnalysisContext(
                riskStore.dependencyGraph(principal.tenantId(), projectId), Map.of()),
            policy);
        store.completeImpactJob(principal.tenantId(), jobId, result.affectedEntities());
        UUID staleStatus = uuid(policy.configuration(), "stalenessStatusConceptId");
        UUID trigger = uuid(policy.configuration(), "stalenessTriggerConceptId");
        store.markStale(principal.tenantId(), result.affectedEntities(),
            staleStatus, trigger, changeSetId);
        outbox.publish(principal.tenantId(), "ImpactAnalysis", jobId,
            "impact.analysis.completed.v1", "impact.analysis.completed.v1",
            Map.of("jobId", jobId, "changeSetId", changeSetId,
                "affectedCount", result.affectedEntities().size()),
            RequestContext.current().correlationId());
        audit.record("IMPACT_ANALYSIS_COMPLETED", "IMPACT_ANALYSIS", jobId,
            null, Map.of("affectedCount", result.affectedEntities().size()));
        return impact(jobId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> impact(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> job = store.impactJob(principal.tenantId(), id);
        access.requireView(uuid(job, "projectId"), principal);
        return job;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> impactEvents(UUID id) {
        impact(id);
        return store.impactEvents(currentTenant.require().tenantId(), id);
    }

    private DocumentVersion requiredVersion(UUID versionId, UUID documentId,
                                            UUID organizationId) {
        DocumentVersion version = versions.findByIdAndOrganizationId(versionId, organizationId)
            .orElseThrow(() -> new IllegalArgumentException("Document version not found"));
        if (!version.documentId().equals(documentId)) {
            throw new IllegalArgumentException("Document version is outside the selected document");
        }
        return version;
    }

    private List<ClauseSnapshot> snapshots(UUID versionId, UUID organizationId) {
        return clauses.findAllByDocumentVersionIdAndOrganizationIdOrderBySortOrder(
                versionId, organizationId).stream()
            .map(this::snapshot).toList();
    }

    private ClauseSnapshot snapshot(Clause clause) {
        return new ClauseSnapshot(clause.id(), clause.clauseNumber(), clause.title(),
            clause.normalizedText(), clause.contentHash(), clause.sortOrder());
    }

    private VersionedPolicy hydrate(UUID organizationId, UUID ontologyVersionId,
                                    VersionedPolicy policy) {
        JsonNode copy = policy.configuration().deepCopy();
        if (copy.isObject()) {
            hydrateObject(organizationId, ontologyVersionId, (ObjectNode) copy);
        }
        return new VersionedPolicy(policy.id(), policy.code(), copy);
    }

    private void hydrateObject(UUID organizationId, UUID ontologyVersionId,
                               ObjectNode object) {
        List<String> fields = new java.util.ArrayList<>();
        object.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            JsonNode value = object.path(field);
            if (field.endsWith("ConceptCode") && value.isTextual()) {
                catalog.conceptId(organizationId, ontologyVersionId, value.asText())
                    .ifPresent(id -> object.put(field.substring(0,
                        field.length() - "ConceptCode".length()) + "ConceptId",
                        id.toString()));
            }
            if (value.isObject()) {
                hydrateObject(organizationId, ontologyVersionId, (ObjectNode) value);
            }
        }
    }

    private static UUID uuid(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private static UUID uuid(JsonNode values, String key) {
        String value = values.path(key).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Impact policy concept is not configured: " + key);
        }
        return UUID.fromString(value);
    }
}
