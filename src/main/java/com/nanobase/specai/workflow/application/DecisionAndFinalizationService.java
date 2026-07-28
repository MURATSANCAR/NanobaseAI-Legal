package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.workflow.api.DecisionContracts.CreateDecisionSupportRequest;
import com.nanobase.specai.workflow.api.DecisionContracts.DecisionSupportResponse;
import com.nanobase.specai.workflow.api.DecisionContracts.ExecutiveDecisionRequest;
import com.nanobase.specai.workflow.api.DecisionContracts.FinalizeProjectRequest;
import com.nanobase.specai.workflow.api.DecisionContracts.ReopenProjectRequest;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportContext;
import com.nanobase.specai.workflow.application.WorkflowModels.DecisionSupportFactor;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
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
public class DecisionAndFinalizationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final WorkflowConditionEngine conditions;
    private final DecisionSupportPolicyEngine decisionEngine;
    private final ConfigurablePolicyGate finalizationGate;
    private final AuditService audit;
    private final OutboxService outbox;

    public DecisionAndFinalizationService(JdbcTemplate jdbc, ObjectMapper mapper,
                                          CurrentTenant currentTenant,
                                          WorkflowConditionEngine conditions,
                                          DecisionSupportPolicyEngine decisionEngine,
                                          ConfigurablePolicyGate finalizationGate,
                                          AuditService audit, OutboxService outbox) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.conditions = conditions;
        this.decisionEngine = decisionEngine;
        this.finalizationGate = finalizationGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public DecisionSupportResponse createCase(UUID projectId,
                                              CreateDecisionSupportRequest request) {
        TenantPrincipal principal = currentTenant.require();
        UUID snapshotId = request.dataSnapshotId();
        if (snapshotId == null) {
            snapshotId = jdbc.queryForObject("""
                select id from report_data_snapshot
                 where project_id = ? order by created_at desc limit 1
                """, UUID.class, projectId);
        }
        JsonNode snapshot = jdbc.queryForObject("""
            select snapshot_payload_json::text from report_data_snapshot where id = ?
            """, (result, row) -> parse(result.getString(1)), snapshotId);
        JsonNode policy = jdbc.queryForObject("""
            select configuration_json::text from decision_policy_version
             where id = ? and status = 'ACTIVE'
            """, (result, row) -> parse(result.getString(1)),
            request.decisionPolicyVersionId());
        Map<String, Object> facts = mapper.convertValue(snapshot,
            new TypeReference<Map<String, Object>>() { });
        List<DecisionSupportFactor> factors = new ArrayList<>();
        for (JsonNode rule : policy.path("factorRules")) {
            if (conditions.evaluate(new WorkflowConditionContext(facts),
                rule.path("condition")).matched()) {
                factors.add(new DecisionSupportFactor(
                    rule.path("factorConceptCode").asText(),
                    rule.path("effectScore").asDouble(),
                    rule.path("weight").asDouble(1),
                    rule.path("description").asText(),
                    mapper.convertValue(rule.path("sourceReference"),
                        new TypeReference<Map<String, Object>>() { })));
            }
        }
        var result = decisionEngine.evaluate(new DecisionSupportContext(facts, factors),
            policy);
        UUID recommended = concept(result.recommendedDecisionConceptCode(),
            "EXECUTIVE_DECISION");
        UUID status = concept("DECISION_SUPPORT_READY", "DECISION_SUPPORT_STATUS");
        UUID id = UUID.randomUUID();
        String caseCode = "DS-" + id.toString().substring(0, 8).toUpperCase();
        JsonNode explanation = mapper.valueToTree(Map.of(
            "recommendedDecisionConcept", result.recommendedDecisionConceptCode(),
            "summary", result.explanation(),
            "sourceReferences", factors.stream()
                .map(DecisionSupportFactor::sourceReference).toList(),
            "confidence", result.confidence(),
            "requiresExecutiveReview", result.requiresExecutiveReview()));
        jdbc.update("""
            insert into decision_support_case (
                id, organization_id, project_id, workflow_instance_id, case_code,
                decision_policy_version_id, data_snapshot_id,
                recommended_decision_concept_id, confidence, status_concept_id,
                explanation_json, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
            """, id, principal.tenantId(), projectId, request.workflowInstanceId(),
            caseCode, request.decisionPolicyVersionId(), snapshotId, recommended,
            result.confidence(), status, json(explanation));
        for (DecisionSupportFactor factor : factors) {
            jdbc.update("""
                insert into decision_support_factor (
                    id, organization_id, decision_support_case_id, factor_concept_id,
                    effect_score, weight, description, source_reference_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """, UUID.randomUUID(), principal.tenantId(), id,
                concept(factor.factorConceptCode(), "DECISION_SUPPORT_FACTOR"),
                factor.effectScore(), factor.weight(), factor.description(),
                json(factor.sourceReference()));
        }
        DecisionSupportResponse created = getCase(id);
        audit.record("decision.support.completed.v1", "DecisionSupportCase", id,
            null, created);
        outbox.publish(principal.tenantId(), "DecisionSupportCase", id,
            "decision.support.completed.v1", "decision.support.completed.v1",
            Map.of("decisionSupportCaseId", id, "projectId", projectId), null);
        return created;
    }

    @Transactional(readOnly = true)
    public DecisionSupportResponse getCase(UUID id) {
        DecisionSupportResponse base = jdbc.queryForObject("""
            select d.id, d.project_id, d.workflow_instance_id, d.case_code,
                   d.decision_policy_version_id, d.data_snapshot_id,
                   d.recommended_decision_concept_id, rc.concept_code as recommended_code,
                   d.final_decision_concept_id, fc.concept_code as final_code,
                   d.confidence, d.status_concept_id, sc.concept_code as status_code,
                   d.explanation_json::text
              from decision_support_case d
              left join ontology_concept rc on rc.id = d.recommended_decision_concept_id
              left join ontology_concept fc on fc.id = d.final_decision_concept_id
              join ontology_concept sc on sc.id = d.status_concept_id
             where d.id = ?
            """, (result, row) -> new DecisionSupportResponse(
                result.getObject("id", UUID.class),
                result.getObject("project_id", UUID.class),
                result.getObject("workflow_instance_id", UUID.class),
                result.getString("case_code"),
                result.getObject("decision_policy_version_id", UUID.class),
                result.getObject("data_snapshot_id", UUID.class),
                result.getObject("recommended_decision_concept_id", UUID.class),
                result.getString("recommended_code"),
                result.getObject("final_decision_concept_id", UUID.class),
                result.getString("final_code"), result.getDouble("confidence"),
                result.getObject("status_concept_id", UUID.class),
                result.getString("status_code"), parse(result.getString(14)),
                List.of(), List.of()), id);
        List<Map<String, Object>> factors = jdbc.query("""
            select f.id, c.concept_code, f.effect_score, f.weight, f.description,
                   f.source_reference_json::text
              from decision_support_factor f
              join ontology_concept c on c.id = f.factor_concept_id
             where f.decision_support_case_id = ? order by abs(f.effect_score * f.weight) desc
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", result.getObject("id", UUID.class));
                value.put("factorConceptCode", result.getString("concept_code"));
                value.put("effectScore", result.getDouble("effect_score"));
                value.put("weight", result.getDouble("weight"));
                value.put("description", result.getString("description"));
                value.put("sourceReference", parse(result.getString(6)));
                return value;
            }, id);
        List<Map<String, Object>> executive = jdbc.query("""
            select e.id, e.decision_concept_id, c.concept_code, e.decision_text,
                   e.conditions_json::text, e.decided_by, e.decided_at
              from executive_decision e
              join ontology_concept c on c.id = e.decision_concept_id
             where e.decision_support_case_id = ? order by e.decided_at
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", result.getObject("id", UUID.class));
                value.put("decisionConceptId",
                    result.getObject("decision_concept_id", UUID.class));
                value.put("decisionConceptCode", result.getString("concept_code"));
                value.put("decisionText", result.getString("decision_text"));
                value.put("conditions", parse(result.getString(5)));
                value.put("decidedBy", result.getString("decided_by"));
                value.put("decidedAt", result.getTimestamp("decided_at").toInstant());
                return value;
            }, id);
        return new DecisionSupportResponse(base.id(), base.projectId(),
            base.workflowInstanceId(), base.caseCode(), base.decisionPolicyVersionId(),
            base.dataSnapshotId(), base.recommendedDecisionConceptId(),
            base.recommendedDecisionConceptCode(), base.finalDecisionConceptId(),
            base.finalDecisionConceptCode(), base.confidence(), base.statusConceptId(),
            base.statusConceptCode(), base.explanation(), factors, executive);
    }

    @Transactional
    public Map<String, Object> decide(UUID caseId, ExecutiveDecisionRequest request) {
        TenantPrincipal principal = currentTenant.require();
        DecisionSupportResponse support = getCase(caseId);
        String code = conceptCode(request.decisionConceptId(), "EXECUTIVE_DECISION");
        UUID id = UUID.randomUUID();
        JsonNode decisionConditions = request.conditions() == null
            ? JsonNodeFactory.instance.arrayNode() : request.conditions();
        jdbc.update("""
            insert into executive_decision (
                id, organization_id, decision_support_case_id, decision_concept_id,
                decision_text, conditions_json, decided_by, decided_at, created_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, ?, now(), now())
            """, id, principal.tenantId(), caseId, request.decisionConceptId(),
            request.decisionText(), json(decisionConditions), principal.subject());
        jdbc.update("""
            update decision_support_case set final_decision_concept_id = ?,
                   updated_at = now(), version = version + 1 where id = ?
            """, request.decisionConceptId(), caseId);
        Map<String, Object> result = Map.of("id", id, "decisionSupportCaseId", caseId,
            "decisionConceptId", request.decisionConceptId(), "decisionConceptCode", code,
            "decidedBy", principal.subject(), "decidedAt", Instant.now());
        audit.record("executive.decision.recorded.v1", "DecisionSupportCase", caseId,
            support, result);
        outbox.publish(principal.tenantId(), "DecisionSupportCase", caseId,
            "executive.decision.recorded.v1", "executive.decision.recorded.v1",
            Map.of("decisionSupportCaseId", caseId, "executiveDecisionId", id), null);
        return result;
    }

    @Transactional
    public Map<String, Object> finalizeProject(UUID projectId,
                                              FinalizeProjectRequest request) {
        TenantPrincipal principal = currentTenant.require();
        requireEffect(request.targetStatusConceptId(), "FINALIZATION_STATUS", "finalize");
        JsonNode policy = jdbc.queryForObject("""
            select configuration_json::text from finalization_policy_version
             where id = ? and status = 'ACTIVE'
            """, (result, row) -> parse(result.getString(1)),
            request.finalizationPolicyVersionId());
        Map<String, Object> facts = finalizationFacts(projectId, request);
        var validation = finalizationGate.evaluate(Map.of("project", facts), policy);
        if (!validation.passed()) {
            throw new IllegalStateException("Finalization policy failed: "
                + String.join(", ", validation.failedRuleCodes()));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into project_finalization_record (
                id, organization_id, project_id, workflow_instance_id,
                finalization_policy_version_id, decision_support_case_id,
                executive_decision_id, final_report_artifact_id, status_concept_id,
                validation_result_json, finalized_by, finalized_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now(), now(), now())
            """, id, principal.tenantId(), projectId, request.workflowInstanceId(),
            request.finalizationPolicyVersionId(), request.decisionSupportCaseId(),
            request.executiveDecisionId(), request.finalReportArtifactId(),
            request.targetStatusConceptId(), json(validation), principal.subject());
        Map<String, Object> result = finalization(id);
        audit.record("project.finalized.v1", "TenderProject", projectId, null, result);
        outbox.publish(principal.tenantId(), "TenderProject", projectId,
            "project.finalized.v1", "project.finalized.v1",
            Map.of("projectId", projectId, "finalizationRecordId", id), null);
        return result;
    }

    @Transactional
    public Map<String, Object> reopen(UUID projectId, ReopenProjectRequest request) {
        TenantPrincipal principal = currentTenant.require();
        requireEffect(request.targetStatusConceptId(), "FINALIZATION_STATUS", "reopen");
        Map<String, Object> before = jdbc.queryForObject("""
            select id from project_finalization_record
             where project_id = ? and finalized_at is not null and reopened_at is null
             order by finalized_at desc limit 1
            """, (result, row) -> finalization(result.getObject(1, UUID.class)), projectId);
        UUID id = (UUID) before.get("id");
        jdbc.update("""
            update project_finalization_record set status_concept_id = ?,
                   reopened_at = now(), reopen_reason = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, request.targetStatusConceptId(), request.reason(), id);
        jdbc.update("""
            update report_artifact set stale_at = now()
             where id in (
                select final_report_artifact_id from project_finalization_record
                 where project_id = ?
             )
            """, projectId);
        Map<String, Object> after = finalization(id);
        audit.record("project.reopened.v1", "TenderProject", projectId, before, after);
        outbox.publish(principal.tenantId(), "TenderProject", projectId,
            "project.reopened.v1", "project.reopened.v1",
            Map.of("projectId", projectId, "finalizationRecordId", id), null);
        return after;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID projectId) {
        return jdbc.query("""
            select id from project_finalization_record
             where project_id = ? order by created_at desc
            """, (result, row) -> finalization(result.getObject(1, UUID.class)), projectId);
    }

    private Map<String, Object> finalizationFacts(UUID projectId,
                                                  FinalizeProjectRequest request) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("executiveDecisionRecorded", count("""
            select count(*) from executive_decision
             where id = ? and decision_support_case_id = ?
            """, request.executiveDecisionId(), request.decisionSupportCaseId()) == 1);
        facts.put("finalReportPresent", count("""
            select count(*) from report_artifact a
              join report_generation_job j on j.id = a.report_generation_job_id
             where a.id = ? and j.project_id = ? and a.stale_at is null
            """, request.finalReportArtifactId(), projectId) == 1);
        facts.put("pendingMandatoryApprovalCount", count("""
            select count(*) from approval_request where project_id = ? and completed_at is null
            """, projectId));
        facts.put("unhandledStaleCount", count("""
            select count(*) from analysis_staleness_record
             where resolved_at is null and entity_id in (
               select id from requirement where project_id = ?
               union select id from risk_record where project_id = ?
               union select id from conflict_record where project_id = ?
             )
            """, projectId, projectId, projectId));
        facts.put("pendingTaskCount", count("""
            select count(*) from task_record
             where project_id = ? and completed_at is null and cancelled_at is null
            """, projectId));
        facts.put("openClarificationCount", count("""
            select count(*) from clarification_request
             where project_id = ? and answered_at is null
            """, projectId));
        facts.put("completedWorkflowCount", count("""
            select count(*) from workflow_instance
             where project_id = ? and completed_at is not null
            """, projectId));
        facts.put("requiredDocumentCount", count("""
            select count(*) from document where project_id = ? and included_in_analysis = true
            """, projectId));
        return Map.copyOf(facts);
    }

    private Map<String, Object> finalization(UUID id) {
        return jdbc.queryForObject("""
            select f.id, f.project_id, f.workflow_instance_id,
                   f.finalization_policy_version_id, f.decision_support_case_id,
                   f.executive_decision_id, f.final_report_artifact_id,
                   f.status_concept_id, c.concept_code as status,
                   f.validation_result_json::text, f.finalized_by, f.finalized_at,
                   f.reopened_at, f.reopen_reason, f.created_at, f.version
              from project_finalization_record f
              join ontology_concept c on c.id = f.status_concept_id
             where f.id = ?
            """, (result, row) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", result.getObject("id", UUID.class));
                value.put("projectId", result.getObject("project_id", UUID.class));
                value.put("workflowInstanceId",
                    result.getObject("workflow_instance_id", UUID.class));
                value.put("finalizationPolicyVersionId",
                    result.getObject("finalization_policy_version_id", UUID.class));
                value.put("decisionSupportCaseId",
                    result.getObject("decision_support_case_id", UUID.class));
                value.put("executiveDecisionId",
                    result.getObject("executive_decision_id", UUID.class));
                value.put("finalReportArtifactId",
                    result.getObject("final_report_artifact_id", UUID.class));
                value.put("statusConceptId",
                    result.getObject("status_concept_id", UUID.class));
                value.put("statusConceptCode", result.getString("status"));
                value.put("validationResult", parse(result.getString(10)));
                value.put("finalizedBy", result.getString("finalized_by"));
                value.put("finalizedAt", instant(result, "finalized_at"));
                value.put("reopenedAt", instant(result, "reopened_at"));
                value.put("reopenReason", result.getString("reopen_reason"));
                value.put("createdAt", result.getTimestamp("created_at").toInstant());
                value.put("version", result.getLong("version"));
                return value;
            }, id);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void requireConcept(UUID id, String type) {
        conceptCode(id, type);
    }

    private void requireEffect(UUID id, String type, String effect) {
        String actual = jdbc.queryForObject("""
            select metadata_json ->> 'actionEffect' from ontology_concept
             where id = ? and concept_type = ? and active = true
            """, String.class, id, type);
        if (!effect.equals(actual)) {
            throw new IllegalArgumentException("Selected status does not support " + effect);
        }
    }

    private String conceptCode(UUID id, String type) {
        return jdbc.queryForObject("""
            select concept_code from ontology_concept
             where id = ? and concept_type = ? and active = true
            """, String.class, id, type);
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
            throw new IllegalArgumentException("Decision data cannot be serialized", exception);
        }
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored decision JSON is invalid", exception);
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
