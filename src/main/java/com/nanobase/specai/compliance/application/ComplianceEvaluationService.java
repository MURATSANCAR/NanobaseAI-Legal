package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.compliance.api.ComplianceContracts.EvidenceLinkRequest;
import com.nanobase.specai.compliance.api.ComplianceContracts.ReviewRequest;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceEvaluationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final ProjectAccessService access;
    private final AuditService audit;
    private final OutboxService outbox;

    public ComplianceEvaluationService(JdbcTemplate jdbc, ObjectMapper mapper,
                                       CurrentTenant currentTenant,
                                       ProjectAccessService access, AuditService audit,
                                       OutboxService outbox) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.access = access;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        return jdbc.queryForList("""
            select evaluation.id, evaluation.requirement_id as "requirementId",
                   requirement.requirement_code as "requirementCode",
                   requirement.requirement_text as "requirementText",
                   requirement.primary_concept_id as "requirementConceptId",
                   requirement_concept.concept_code as "requirementConcept",
                   evaluation.target_scope_json::text as "targetScope",
                   suggested.concept_code as "suggestedDecision",
                   final.concept_code as "finalDecision",
                   evaluation.combined_confidence as "combinedConfidence",
                   grounding.concept_code as "groundingStatus",
                   evaluation.review_status as "reviewStatus",
                   (select count(*) from compliance_evidence_link link
                    where link.organization_id = evaluation.organization_id
                      and link.compliance_evaluation_id = evaluation.id)
                       as "evidenceCount",
                   (select count(*) from compliance_evidence_link link
                    where link.organization_id = evaluation.organization_id
                      and link.compliance_evaluation_id = evaluation.id
                      and link.contradiction_strength > 0)
                       as "contradictionCount",
                   evaluation.created_at as "createdAt",
                   evaluation.updated_at as "updatedAt"
            from compliance_evaluation evaluation
            join requirement on requirement.id = evaluation.requirement_id
             and requirement.organization_id = evaluation.organization_id
            left join ontology_concept requirement_concept
              on requirement_concept.id = requirement.primary_concept_id
            join ontology_concept suggested
              on suggested.id = evaluation.suggested_decision_concept_id
            left join ontology_concept final
              on final.id = evaluation.final_decision_concept_id
            join ontology_concept grounding
              on grounding.id = evaluation.grounding_status_concept_id
            where evaluation.organization_id = ? and evaluation.project_id = ?
            order by requirement.created_at, evaluation.created_at desc
            """, principal.tenantId(), projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        UUID organizationId = currentTenant.require().tenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select evaluation.*, requirement.requirement_code,
                   requirement.requirement_text,
                   suggested.concept_code as suggested_decision,
                   final.concept_code as final_decision,
                   grounding.concept_code as grounding_status
            from compliance_evaluation evaluation
            join requirement on requirement.id = evaluation.requirement_id
             and requirement.organization_id = evaluation.organization_id
            join ontology_concept suggested
              on suggested.id = evaluation.suggested_decision_concept_id
            left join ontology_concept final
              on final.id = evaluation.final_decision_concept_id
            join ontology_concept grounding
              on grounding.id = evaluation.grounding_status_concept_id
            where evaluation.id = ? and evaluation.organization_id = ?
            """, id, organizationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Compliance evaluation not found");
        }
        Map<String, Object> response = new java.util.LinkedHashMap<>(rows.getFirst());
        response.put("evidence", evidence(organizationId, id));
        response.put("history", history(organizationId, id));
        return response;
    }

    @Transactional
    public Map<String, Object> review(UUID id, ReviewRequest request) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = get(id);
        Decision decision = decision(principal.tenantId(), request.finalDecisionConceptId());
        requireConcept(principal.tenantId(), request.changeTypeConceptId());
        if (decision.positive()) {
            enforcePositiveDecision(principal.tenantId(), id);
        }
        revision(principal, id, before, request.changeTypeConceptId());
        jdbc.update("""
            update compliance_evaluation
            set final_decision_concept_id = ?, review_status = 'APPROVED',
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, request.finalDecisionConceptId(), id, principal.tenantId());
        Map<String, Object> after = get(id);
        feedback(principal, id, before, after, request.feedbackTypeConceptId(),
            request.reason(), request.changeTypeConceptId());
        audit.record("COMPLIANCE_EVALUATION_REVIEWED", "ComplianceEvaluation",
            id, before, after);
        outbox.publish(principal.tenantId(), "ComplianceEvaluation", id,
            "ExpertFeedbackRecorded", "expert.feedback.recorded.v1",
            Map.of("evaluationId", id,
                "finalDecisionConceptId", request.finalDecisionConceptId()),
            RequestContext.current().correlationId());
        return after;
    }

    @Transactional
    public Map<String, Object> addEvidence(UUID id, EvidenceLinkRequest request) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = get(id);
        requireEvidence(principal.tenantId(), request.evidenceFragmentId());
        requireConcept(principal.tenantId(), request.relationRoleConceptId());
        if (request.evidenceClaimId() != null) {
            Integer count = jdbc.queryForObject("""
                select count(*) from evidence_claim
                where id = ? and organization_id = ?
                  and evidence_fragment_id = ?
                """, Integer.class, request.evidenceClaimId(), principal.tenantId(),
                request.evidenceFragmentId());
            if (count == null || count != 1) {
                throw new IllegalArgumentException(
                    "Evidence claim does not belong to the selected fragment");
            }
        }
        UUID linkId = UUID.randomUUID();
        jdbc.update("""
            insert into compliance_evidence_link (
                id, organization_id, compliance_evaluation_id, evidence_fragment_id,
                evidence_claim_id, relation_role_concept_id, relevance_score,
                validity_score, support_strength, contradiction_strength,
                selected_by_type, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """, linkId, principal.tenantId(), id, request.evidenceFragmentId(),
            request.evidenceClaimId(), request.relationRoleConceptId(),
            request.relevanceScore(), request.validityScore(), request.supportStrength(),
            request.contradictionStrength(), request.selectedByType());
        UUID changeType = changeConcept(principal.tenantId(), "EVIDENCE_LINK_CHANGED");
        revision(principal, id, before, changeType);
        Map<String, Object> after = get(id);
        audit.record("COMPLIANCE_EVIDENCE_ADDED", "ComplianceEvaluation", id,
            before, after);
        return after;
    }

    @Transactional
    public void removeEvidence(UUID id, UUID linkId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> before = get(id);
        int changed = jdbc.update("""
            delete from compliance_evidence_link
            where id = ? and compliance_evaluation_id = ? and organization_id = ?
            """, linkId, id, principal.tenantId());
        if (changed != 1) {
            throw new IllegalArgumentException("Compliance evidence link not found");
        }
        UUID changeType = changeConcept(principal.tenantId(), "EVIDENCE_LINK_CHANGED");
        revision(principal, id, before, changeType);
        audit.record("COMPLIANCE_EVIDENCE_REMOVED", "ComplianceEvaluation", id,
            before, get(id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID id) {
        UUID organizationId = currentTenant.require().tenantId();
        get(id);
        return history(organizationId, id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> explanation(UUID id) {
        Map<String, Object> evaluation = get(id);
        return Map.of(
            "evaluationId", id,
            "comparisonSummary", evaluation.get("comparison_summary_json"),
            "combinedConfidence", evaluation.get("combined_confidence"),
            "groundingStatus", evaluation.get("grounding_status"),
            "evidence", evaluation.get("evidence")
        );
    }

    private void enforcePositiveDecision(UUID organizationId, UUID evaluationId) {
        Integer usableSupport = jdbc.queryForObject("""
            select count(*)
            from compliance_evidence_link link
            join evidence_fragment fragment
              on fragment.id = link.evidence_fragment_id
             and fragment.organization_id = link.organization_id
            join lateral (
                select concept.metadata_json
                from evidence_validity_assessment assessment
                join ontology_concept concept on concept.id = assessment.status_concept_id
                where assessment.organization_id = link.organization_id
                  and assessment.evidence_fragment_id = link.evidence_fragment_id
                order by assessment.assessed_at desc limit 1
            ) validity on true
            join compliance_evaluation evaluation
              on evaluation.id = link.compliance_evaluation_id
             and evaluation.organization_id = link.organization_id
            join ontology_concept grounding
              on grounding.id = evaluation.grounding_status_concept_id
            where link.organization_id = ?
              and link.compliance_evaluation_id = ?
              and link.support_strength > 0
              and link.contradiction_strength = 0
              and (fragment.valid_until is null or fragment.valid_until > now())
              and coalesce((validity.metadata_json ->> 'usable')::boolean, false)
              and coalesce((grounding.metadata_json ->> 'grounded')::boolean, false)
            """, Integer.class, organizationId, evaluationId);
        Integer contradictions = jdbc.queryForObject("""
            select count(*) from compliance_evidence_link
            where organization_id = ? and compliance_evaluation_id = ?
              and contradiction_strength > 0
            """, Integer.class, organizationId, evaluationId);
        if (usableSupport == null || usableSupport == 0) {
            throw new IllegalStateException(
                "Positive decision requires active, valid and grounded evidence");
        }
        if (contradictions != null && contradictions > 0) {
            throw new IllegalStateException(
                "Positive final decision is blocked by contradictory evidence");
        }
    }

    private List<Map<String, Object>> evidence(UUID organizationId, UUID evaluationId) {
        return jdbc.queryForList("""
            select link.id, link.evidence_fragment_id, link.evidence_claim_id,
                   role.concept_code as relation_role,
                   link.relevance_score, link.validity_score, link.support_strength,
                   link.contradiction_strength, link.selected_by_type,
                   fragment.document_id, fragment.document_version_id,
                   fragment.page_number, fragment.fragment_text,
                   fragment.bounding_boxes_json::text,
                   fragment.valid_from, fragment.valid_until,
                   claim.claim_text
            from compliance_evidence_link link
            join evidence_fragment fragment on fragment.id = link.evidence_fragment_id
             and fragment.organization_id = link.organization_id
            left join evidence_claim claim on claim.id = link.evidence_claim_id
             and claim.organization_id = link.organization_id
            join ontology_concept role on role.id = link.relation_role_concept_id
            where link.organization_id = ? and link.compliance_evaluation_id = ?
            order by link.contradiction_strength desc, link.support_strength desc,
                     link.relevance_score desc
            """, organizationId, evaluationId);
    }

    private List<Map<String, Object>> history(UUID organizationId, UUID evaluationId) {
        return jdbc.queryForList("""
            select revision.id, revision.revision_number,
                   revision.snapshot_json::text as snapshot_json,
                   revision.change_type_concept_id,
                   concept.concept_code as change_type,
                   revision.created_by, revision.created_at
            from compliance_evaluation_revision revision
            join ontology_concept concept on concept.id = revision.change_type_concept_id
            where revision.organization_id = ?
              and revision.compliance_evaluation_id = ?
            order by revision.revision_number desc
            """, organizationId, evaluationId);
    }

    private Decision decision(UUID organizationId, UUID id) {
        return jdbc.query("""
            select concept_code,
                   coalesce((metadata_json ->> 'positive')::boolean, false)
            from ontology_concept
            where id = ? and active = true and concept_type = 'DECISION'
              and (organization_id = ? or organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException(
                        "Active compliance decision concept not found");
                }
                return new Decision(id, result.getString(1), result.getBoolean(2));
            }, id, organizationId);
    }

    private void revision(TenantPrincipal principal, UUID evaluationId, Object snapshot,
                          UUID changeTypeConceptId) {
        Integer number = jdbc.queryForObject("""
            select coalesce(max(revision_number), 0) + 1
            from compliance_evaluation_revision
            where organization_id = ? and compliance_evaluation_id = ?
            """, Integer.class, principal.tenantId(), evaluationId);
        jdbc.update("""
            insert into compliance_evaluation_revision (
                id, organization_id, compliance_evaluation_id, revision_number,
                snapshot_json, change_type_concept_id, created_by, created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?, now())
            """, UUID.randomUUID(), principal.tenantId(), evaluationId, number,
            json(snapshot), changeTypeConceptId, principal.subject());
    }

    private void feedback(TenantPrincipal principal, UUID evaluationId,
                          Object before, Object after, UUID feedbackTypeConceptId,
                          String reason, UUID fallbackConceptId) {
        Map<String, Object> evaluation = jdbc.queryForMap("""
            select project_id, analysis_profile_id, matching_policy_version_id,
                   prompt_package_version_id
            from compliance_evaluation where id = ? and organization_id = ?
            """, evaluationId, principal.tenantId());
        UUID conceptId = feedbackTypeConceptId == null
            ? fallbackConceptId : requireConcept(principal.tenantId(), feedbackTypeConceptId);
        String conceptCode = jdbc.queryForObject("""
            select concept_code from ontology_concept where id = ?
            """, String.class, conceptId);
        jdbc.update("""
            insert into expert_feedback (
                id, organization_id, project_id, entity_type, entity_id,
                feedback_type, feedback_type_concept_id, original_snapshot_json,
                corrected_snapshot_json, reason, reviewer_id, analysis_profile_id,
                policy_version_id, prompt_version_id, approved_for_learning, created_at
            ) values (?, ?, ?, 'COMPLIANCE_EVALUATION', ?, ?, ?, ?::jsonb, ?::jsonb,
                      ?, ?, ?, ?, ?, false, now())
            """, UUID.randomUUID(), principal.tenantId(), evaluation.get("project_id"),
            evaluationId, conceptCode, conceptId, json(before), json(after), reason,
            principal.subject(), evaluation.get("analysis_profile_id"),
            evaluation.get("matching_policy_version_id"),
            evaluation.get("prompt_package_version_id"));
    }

    private UUID changeConcept(UUID organizationId, String provider) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where active = true and metadata_json ->> 'changeProvider' = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            provider, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Change type concept is not configured");
        }
        return ids.getFirst();
    }

    private UUID requireConcept(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from ontology_concept
            where id = ? and active = true
              and (organization_id = ? or organization_id is null)
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active ontology concept not found");
        }
        return id;
    }

    private void requireEvidence(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from evidence_fragment
            where id = ? and organization_id = ?
              and (valid_until is null or valid_until > now())
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active evidence fragment not found");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Evaluation revision is not serializable",
                exception);
        }
    }

    private record Decision(UUID id, String code, boolean positive) {
    }
}
