package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.EvidenceAssessmentRequest;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceService {
    private final JdbcTemplate jdbc;
    private final CurrentTenant currentTenant;
    private final AuditService audit;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;

    public EvidenceService(JdbcTemplate jdbc, CurrentTenant currentTenant,
                           AuditService audit, OutboxService outbox,
                           PlatformMetrics metrics) {
        this.jdbc = jdbc;
        this.currentTenant = currentTenant;
        this.audit = audit;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID documentId, String validityStatus) {
        UUID organizationId = currentTenant.require().tenantId();
        return jdbc.queryForList("""
            select fragment.id, fragment.document_id, fragment.document_version_id,
                   fragment.clause_id, fragment.document_page_id, fragment.page_number,
                   fragment.content_hash, fragment.language, fragment.parser_quality,
                   fragment.ocr_quality, fragment.valid_from, fragment.valid_until,
                   latest.score as validity_score,
                   status.concept_code as validity_status,
                   (select count(*) from compliance_evidence_link link
                    where link.organization_id = fragment.organization_id
                      and link.evidence_fragment_id = fragment.id) as usage_count
            from evidence_fragment fragment
            left join lateral (
              select assessment.status_concept_id, assessment.score
              from evidence_validity_assessment assessment
              where assessment.organization_id = fragment.organization_id
                and assessment.evidence_fragment_id = fragment.id
              order by assessment.assessed_at desc limit 1
            ) latest on true
            left join ontology_concept status on status.id = latest.status_concept_id
            where fragment.organization_id = ?
              and (?::uuid is null or fragment.document_id = ?::uuid)
              and (? is null or status.concept_code = ?)
            order by fragment.created_at desc
            """, organizationId, documentId, documentId, validityStatus, validityStatus);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        UUID organizationId = currentTenant.require().tenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select fragment.id, fragment.document_id, fragment.document_version_id,
                   fragment.clause_id, fragment.document_page_id, fragment.page_number,
                   fragment.fragment_text, fragment.bounding_boxes_json::text,
                   fragment.source_start_offset, fragment.source_end_offset,
                   fragment.content_hash, fragment.language, fragment.parser_quality,
                   fragment.ocr_quality, fragment.valid_from, fragment.valid_until,
                   document.logical_name as document_name,
                   document.document_type, version.version_number,
                   latest.score as validity_score,
                   latest.factors_json::text as validity_factors,
                   status.concept_code as validity_status
            from evidence_fragment fragment
            join document on document.id = fragment.document_id
             and document.organization_id = fragment.organization_id
            join document_version version on version.id = fragment.document_version_id
             and version.organization_id = fragment.organization_id
            left join lateral (
              select assessment.status_concept_id, assessment.score, assessment.factors_json
              from evidence_validity_assessment assessment
              where assessment.organization_id = fragment.organization_id
                and assessment.evidence_fragment_id = fragment.id
              order by assessment.assessed_at desc limit 1
            ) latest on true
            left join ontology_concept status on status.id = latest.status_concept_id
            where fragment.id = ? and fragment.organization_id = ?
            """, id, organizationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Evidence fragment not found");
        }
        return rows.getFirst();
    }

    @Transactional
    public Map<String, Object> assess(UUID id, EvidenceAssessmentRequest request,
                                      boolean invalidating) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = get(id);
        requireConcept(principal.tenantId(), request.statusConceptId());
        requirePolicy(principal.tenantId(), request.policyVersionId());
        jdbc.update("""
            insert into evidence_validity_assessment (
                id, organization_id, evidence_fragment_id, policy_version_id,
                status_concept_id, score, factors_json, assessed_at,
                assessed_by_type, created_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, now(), ?, now())
            """, UUID.randomUUID(), principal.tenantId(), id, request.policyVersionId(),
            request.statusConceptId(), request.score(), json(request.factors()),
            request.assessedByType());
        if (invalidating) {
            jdbc.update("""
                update evidence_fragment set valid_until = coalesce(valid_until, now())
                where id = ? and organization_id = ?
                """, id, principal.tenantId());
            metrics.evidenceInvalid();
        }
        Map<String, Object> after = get(id);
        String action = invalidating ? "EVIDENCE_INVALIDATED" : "EVIDENCE_VERIFIED";
        audit.record(action, "EvidenceFragment", id, before, after);
        outbox.publish(principal.tenantId(), "EvidenceFragment", id, action,
            invalidating ? "evidence.invalidated.v1" : "evidence.verified.v1",
            Map.of("evidenceFragmentId", id, "statusConceptId", request.statusConceptId()),
            RequestContext.current().correlationId());
        return after;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> usages(UUID id) {
        UUID organizationId = currentTenant.require().tenantId();
        get(id);
        return jdbc.queryForList("""
            select 'ATTRIBUTE' as usage_type, attribute.id as usage_id,
                   attribute.entity_id as owner_id, attribute.attribute_concept_id as concept_id
            from entity_attribute attribute
            where attribute.organization_id = ? and attribute.source_fragment_id = ?
            union all
            select 'RELATION', relation.id, relation.source_entity_id,
                   relation.relation_concept_id
            from knowledge_relation relation
            where relation.organization_id = ? and relation.source_fragment_id = ?
            union all
            select 'CAPABILITY', capability_evidence.id, capability.owner_entity_id,
                   capability.capability_concept_id
            from capability_evidence
            join capability on capability.id = capability_evidence.capability_id
             and capability.organization_id = capability_evidence.organization_id
            where capability_evidence.organization_id = ?
              and capability_evidence.evidence_fragment_id = ?
            union all
            select 'COMPLIANCE', link.id, evaluation.id,
                   evaluation.suggested_decision_concept_id
            from compliance_evidence_link link
            join compliance_evaluation evaluation
              on evaluation.id = link.compliance_evaluation_id
             and evaluation.organization_id = link.organization_id
            where link.organization_id = ? and link.evidence_fragment_id = ?
            """, organizationId, id, organizationId, id,
            organizationId, id, organizationId, id);
    }

    private void requireConcept(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from ontology_concept where id = ? and active = true
              and (organization_id = ? or organization_id is null)
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active ontology concept not found");
        }
    }

    private void requirePolicy(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from policy_version where id = ? and status = 'ACTIVE'
              and (organization_id = ? or organization_id is null)
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active policy version not found");
        }
    }

    private String json(JsonNode node) {
        return node == null ? "[]" : node.toString();
    }
}
