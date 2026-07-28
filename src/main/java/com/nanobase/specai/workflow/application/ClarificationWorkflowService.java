package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.workflow.api.ClarificationContracts.ClarificationAnswerRequest;
import com.nanobase.specai.workflow.api.ClarificationContracts.ClarificationResponse;
import com.nanobase.specai.workflow.api.ClarificationContracts.RevisionRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClarificationWorkflowService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final AuditService audit;
    private final OutboxService outbox;

    public ClarificationWorkflowService(JdbcTemplate jdbc, ObjectMapper mapper,
                                        CurrentTenant currentTenant,
                                        AuditService audit, OutboxService outbox) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(UUID projectId) {
        List<ClarificationResponse> requests = jdbc.query("""
            select r.*, c.concept_code as status_code
              from clarification_request r
              join ontology_concept c on c.id = r.status_concept_id
             where r.project_id = ? order by r.created_at desc
            """, this::response, projectId).stream().map(this::details).toList();
        List<Map<String, Object>> candidates = jdbc.query("""
            select id, source_entity_type, source_entity_id, question, reason,
                   source_ids_json::text, priority_concept_id, requires_legal_review,
                   review_status, created_at
              from clarification_candidate
             where project_id = ?
               and not exists (select 1 from clarification_request r where r.id = clarification_candidate.id)
             order by created_at desc
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", result.getObject("id", UUID.class));
                value.put("sourceType", result.getString("source_entity_type"));
                value.put("sourceId", result.getObject("source_entity_id", UUID.class));
                value.put("question", result.getString("question"));
                value.put("reason", result.getString("reason"));
                value.put("sourceIds", parse(result.getString(6)));
                value.put("priorityConceptId",
                    result.getObject("priority_concept_id", UUID.class));
                value.put("requiresLegalReview", result.getBoolean("requires_legal_review"));
                value.put("reviewStatus", result.getString("review_status"));
                value.put("createdAt", result.getTimestamp("created_at").toInstant());
                return value;
            }, projectId);
        return Map.of("requests", requests, "candidates", candidates);
    }

    @Transactional
    public ClarificationResponse revise(UUID id, RevisionRequest request) {
        TenantPrincipal principal = currentTenant.require();
        ensureRequest(id, principal);
        ClarificationResponse before = get(id);
        int revision = jdbc.queryForObject("""
            select coalesce(max(revision_number), 0) + 1
              from clarification_revision where clarification_request_id = ?
            """, Integer.class, id);
        UUID revisionId = UUID.randomUUID();
        jdbc.update("""
            insert into clarification_revision (
                id, organization_id, clarification_request_id, revision_number,
                question_text, reason, source_snapshot_json, edited_by, created_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
            """, revisionId, principal.tenantId(), id, revision, request.questionText(),
            request.reason(), json(request.sourceSnapshot()), principal.subject());
        jdbc.update("""
            update clarification_request set question_text = ?, reason = ?,
                   updated_at = now(), version = version + 1 where id = ?
            """, request.questionText(), request.reason(), id);
        ClarificationResponse after = get(id);
        audit.record("clarification.revised.v1", "ClarificationRequest", id, before, after);
        return after;
    }

    @Transactional
    public ClarificationResponse changeStatus(UUID id, UUID targetStatusConceptId,
                                              String effect) {
        TenantPrincipal principal = currentTenant.require();
        ensureRequest(id, principal);
        requireEffect(targetStatusConceptId, effect);
        ClarificationResponse before = get(id);
        if ("approve".equals(effect)) {
            UUID revision = jdbc.queryForObject("""
                select id from clarification_revision
                 where clarification_request_id = ?
                 order by revision_number desc limit 1
                """, UUID.class, id);
            jdbc.update("""
                update clarification_request set status_concept_id = ?,
                       approved_version_id = ?, updated_at = now(), version = version + 1
                 where id = ?
                """, targetStatusConceptId, revision, id);
        } else if ("send".equals(effect)) {
            int updated = jdbc.update("""
                update clarification_request set status_concept_id = ?, sent_at = now(),
                       updated_at = now(), version = version + 1
                 where id = ? and approved_version_id is not null
                """, targetStatusConceptId, id);
            if (updated != 1) {
                throw new IllegalStateException(
                    "Clarification requires an approved human-reviewed revision");
            }
        } else {
            jdbc.update("""
                update clarification_request set status_concept_id = ?,
                       updated_at = now(), version = version + 1 where id = ?
                """, targetStatusConceptId, id);
        }
        ClarificationResponse after = get(id);
        String event = switch (effect) {
            case "review" -> "clarification.review.requested.v1";
            case "approve" -> "clarification.approved.v1";
            case "send" -> "clarification.sent.v1";
            default -> "clarification.status.changed.v1";
        };
        audit.record(event, "ClarificationRequest", id, before, after);
        outbox.publish(principal.tenantId(), "ClarificationRequest", id, event, event,
            Map.of("clarificationRequestId", id, "projectId", after.projectId()), null);
        return after;
    }

    @Transactional
    public ClarificationResponse answer(UUID id, ClarificationAnswerRequest request) {
        TenantPrincipal principal = currentTenant.require();
        ensureRequest(id, principal);
        requireEffect(request.targetStatusConceptId(), "answer");
        ClarificationResponse before = get(id);
        UUID answerId = UUID.randomUUID();
        jdbc.update("""
            insert into clarification_answer (
                id, organization_id, clarification_request_id, document_id,
                document_version_id, impact_analysis_job_id, received_by,
                received_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
            """, answerId, principal.tenantId(), id, request.documentId(),
            request.documentVersionId(), request.impactAnalysisJobId(), principal.subject());
        jdbc.update("""
            update clarification_request set status_concept_id = ?, answered_at = now(),
                   updated_at = now(), version = version + 1 where id = ?
            """, request.targetStatusConceptId(), id);
        outbox.publish(principal.tenantId(), "ClarificationRequest", id,
            "clarification.answer.received.v1", "clarification.answer.received.v1",
            Map.of("clarificationRequestId", id, "answerDocumentId", request.documentId(),
                "projectId", before.projectId()), null);
        ClarificationResponse after = get(id);
        audit.record("clarification.answer.received.v1", "ClarificationRequest", id,
            before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public ClarificationResponse get(UUID id) {
        ClarificationResponse base = jdbc.queryForObject("""
            select r.*, c.concept_code as status_code
              from clarification_request r
              join ontology_concept c on c.id = r.status_concept_id
             where r.id = ?
            """, this::response, id);
        return details(base);
    }

    private void ensureRequest(UUID id, TenantPrincipal principal) {
        Integer exists = jdbc.queryForObject("""
            select count(*) from clarification_request where id = ?
            """, Integer.class, id);
        if (exists != null && exists == 1) {
            return;
        }
        Map<String, Object> candidate = jdbc.queryForObject("""
            select id, project_id, source_entity_type, source_entity_id, question,
                   reason, priority_concept_id, requires_legal_review, source_ids_json::text
              from clarification_candidate where id = ?
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("projectId", result.getObject("project_id", UUID.class));
                value.put("sourceType", result.getString("source_entity_type"));
                value.put("sourceId", result.getObject("source_entity_id", UUID.class));
                value.put("question", result.getString("question"));
                value.put("reason", result.getString("reason"));
                value.put("priority", result.getObject("priority_concept_id", UUID.class));
                value.put("legal", result.getBoolean("requires_legal_review"));
                value.put("sourceIds", parse(result.getString(9)));
                return value;
            }, id);
        UUID status = concept("CLARIFICATION_CANDIDATE", "CLARIFICATION_STATUS");
        String code = "CLR-" + id.toString().substring(0, 8).toUpperCase();
        jdbc.update("""
            insert into clarification_request (
                id, organization_id, project_id, source_type, source_id, question_code,
                question_text, reason, priority_concept_id, status_concept_id,
                requires_legal_review, requires_technical_review, external_recipient_json,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, '{}'::jsonb, now(), now())
            """, id, principal.tenantId(), candidate.get("projectId"),
            candidate.get("sourceType"), candidate.get("sourceId"), code,
            candidate.get("question"), candidate.get("reason"), candidate.get("priority"),
            status, candidate.get("legal"));
        UUID revisionId = UUID.randomUUID();
        jdbc.update("""
            insert into clarification_revision (
                id, organization_id, clarification_request_id, revision_number,
                question_text, reason, source_snapshot_json, edited_by, created_at
            ) values (?, ?, ?, 1, ?, ?, ?::jsonb, ?, now())
            """, revisionId, principal.tenantId(), id, candidate.get("question"),
            candidate.get("reason"), json(Map.of("candidateSourceIds",
                candidate.get("sourceIds"))), principal.subject());
    }

    private ClarificationResponse details(ClarificationResponse base) {
        List<Map<String, Object>> revisions = jdbc.query("""
            select id, revision_number, question_text, reason, source_snapshot_json::text,
                   edited_by, created_at
              from clarification_revision where clarification_request_id = ?
             order by revision_number
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", result.getObject("id", UUID.class));
                value.put("revisionNumber", result.getInt("revision_number"));
                value.put("questionText", result.getString("question_text"));
                value.put("reason", result.getString("reason"));
                value.put("sourceSnapshot", parse(result.getString(5)));
                value.put("editedBy", result.getString("edited_by"));
                value.put("createdAt", result.getTimestamp("created_at").toInstant());
                return value;
            }, base.id());
        List<Map<String, Object>> sources = jdbc.query("""
            select id, document_id, document_version_id, clause_id, requirement_id,
                   risk_id, conflict_id, ambiguity_id, source_text, page_number,
                   bounding_boxes_json::text
              from clarification_source where clarification_request_id = ?
             order by created_at
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", result.getObject("id", UUID.class));
                value.put("documentId", result.getObject("document_id", UUID.class));
                value.put("documentVersionId",
                    result.getObject("document_version_id", UUID.class));
                value.put("clauseId", result.getObject("clause_id", UUID.class));
                value.put("requirementId", result.getObject("requirement_id", UUID.class));
                value.put("riskId", result.getObject("risk_id", UUID.class));
                value.put("conflictId", result.getObject("conflict_id", UUID.class));
                value.put("ambiguityId", result.getObject("ambiguity_id", UUID.class));
                value.put("sourceText", result.getString("source_text"));
                value.put("pageNumber", result.getObject("page_number"));
                value.put("boundingBoxes", parse(result.getString(11)));
                return value;
            }, base.id());
        return new ClarificationResponse(base.id(), base.projectId(),
            base.workflowInstanceId(), base.sourceType(), base.sourceId(),
            base.questionCode(), base.questionText(), base.reason(),
            base.priorityConceptId(), base.statusConceptId(), base.statusConceptCode(),
            base.requiresLegalReview(), base.requiresTechnicalReview(),
            base.externalRecipient(), base.approvedVersionId(), base.sentAt(),
            base.answeredAt(), base.version(), revisions, sources);
    }

    private ClarificationResponse response(ResultSet result, int row) throws SQLException {
        return new ClarificationResponse(result.getObject("id", UUID.class),
            result.getObject("project_id", UUID.class),
            result.getObject("workflow_instance_id", UUID.class),
            result.getString("source_type"), result.getObject("source_id", UUID.class),
            result.getString("question_code"), result.getString("question_text"),
            result.getString("reason"), result.getObject("priority_concept_id", UUID.class),
            result.getObject("status_concept_id", UUID.class),
            result.getString("status_code"), result.getBoolean("requires_legal_review"),
            result.getBoolean("requires_technical_review"),
            parse(result.getString("external_recipient_json")),
            result.getObject("approved_version_id", UUID.class),
            instant(result, "sent_at"), instant(result, "answered_at"),
            result.getLong("version"), List.of(), List.of());
    }

    private void requireEffect(UUID conceptId, String effect) {
        String actual = jdbc.queryForObject("""
            select metadata_json ->> 'actionEffect' from ontology_concept
             where id = ? and concept_type = 'CLARIFICATION_STATUS' and active = true
            """, String.class, conceptId);
        if (!effect.equals(actual)) {
            throw new IllegalArgumentException("Selected clarification status is not valid");
        }
    }

    private UUID concept(String code, String type) {
        return jdbc.queryForObject("""
            select id from ontology_concept
             where concept_code = ? and concept_type = ? and active = true
             order by organization_id nulls last limit 1
            """, UUID.class, code, type);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Clarification JSON cannot be serialized",
                exception);
        }
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored clarification JSON is invalid", exception);
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
