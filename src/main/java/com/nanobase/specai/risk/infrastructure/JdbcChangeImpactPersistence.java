package com.nanobase.specai.risk.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.risk.application.ChangeImpactPersistencePort;
import com.nanobase.specai.risk.application.DocumentChangeMatcher.Match;
import com.nanobase.specai.risk.application.RiskModels.AffectedEntity;
import com.nanobase.specai.risk.application.RiskModels.ChangeItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcChangeImpactPersistence implements ChangeImpactPersistencePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcChangeImpactPersistence(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public UUID createChangeSet(UUID organizationId, UUID projectId, UUID baseVersionId,
                                UUID targetVersionId, UUID policyVersionId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into document_change_set (
                id, organization_id, project_id, base_document_version_id,
                target_document_version_id, change_profile_id, status,
                summary_json, created_at
            ) values (?, ?, ?, ?, ?, ?, 'ANALYZING', '{}'::jsonb, now())
            """, id, organizationId, projectId, baseVersionId, targetVersionId,
            policyVersionId);
        return id;
    }

    @Override
    public void saveMatches(UUID organizationId, UUID changeSetId, List<Match> matches,
                            Function<String, UUID> conceptResolver) {
        for (Match match : matches) {
            UUID conceptId = conceptResolver.apply(match.changeConceptCode());
            if (conceptId == null) {
                throw new IllegalArgumentException(
                    "Change policy references unknown concept " + match.changeConceptCode());
            }
            jdbc.update("""
                insert into document_change_item (
                    id, organization_id, change_set_id, change_type_concept_id,
                    base_clause_id, target_clause_id, similarity_score,
                    change_attributes_json, confidence, review_status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'REVIEW_REQUIRED', now())
                """, UUID.randomUUID(), organizationId, changeSetId, conceptId,
                match.baseClauseId(), match.targetClauseId(), match.similarity(),
                json(match.attributes()), match.confidence());
        }
    }

    @Override
    public void completeChangeSet(UUID organizationId, UUID changeSetId, JsonNode summary) {
        required(jdbc.update("""
            update document_change_set set status = 'COMPLETED',
                summary_json = ?::jsonb, completed_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, json(summary), changeSetId, organizationId));
    }

    @Override
    public Map<String, Object> changeSet(UUID organizationId, UUID changeSetId) {
        return one("""
            select change_set.id, change_set.project_id as "projectId",
                   change_set.base_document_version_id as "baseDocumentVersionId",
                   change_set.target_document_version_id as "targetDocumentVersionId",
                   change_set.change_profile_id as "changeProfileId", change_set.status,
                   change_set.summary_json::text as summary,
                   change_set.created_at as "createdAt",
                   change_set.completed_at as "completedAt"
            from document_change_set change_set
            where change_set.id = ? and change_set.organization_id = ?
            """, changeSetId, organizationId);
    }

    @Override
    public List<Map<String, Object>> changeItems(UUID organizationId, UUID changeSetId) {
        return rows("""
            select item.id, concept.concept_code as "changeType",
                   item.change_type_concept_id as "changeTypeConceptId",
                   item.base_clause_id as "baseClauseId",
                   item.target_clause_id as "targetClauseId",
                   item.base_requirement_id as "baseRequirementId",
                   item.target_requirement_id as "targetRequirementId",
                   item.similarity_score as "similarityScore",
                   item.change_attributes_json::text as attributes,
                   item.confidence, item.review_status as "reviewStatus",
                   item.created_at as "createdAt"
            from document_change_item item
            join ontology_concept concept on concept.id = item.change_type_concept_id
            where item.change_set_id = ? and item.organization_id = ?
            order by item.created_at
            """, changeSetId, organizationId);
    }

    @Override
    public List<ChangeItem> changeItemModels(UUID organizationId, UUID changeSetId) {
        return jdbc.query("""
            select id, change_type_concept_id, base_clause_id, target_clause_id,
                   base_requirement_id, target_requirement_id, confidence
            from document_change_item where change_set_id = ? and organization_id = ?
            """, (result, row) -> {
                UUID targetRequirement = result.getObject("target_requirement_id", UUID.class);
                UUID baseRequirement = result.getObject("base_requirement_id", UUID.class);
                UUID targetClause = result.getObject("target_clause_id", UUID.class);
                UUID baseClause = result.getObject("base_clause_id", UUID.class);
                UUID source = baseRequirement == null ? baseClause : baseRequirement;
                UUID target = targetRequirement == null ? targetClause : targetRequirement;
                return new ChangeItem(result.getObject("id", UUID.class),
                    result.getObject("change_type_concept_id", UUID.class),
                    baseRequirement == null ? "CLAUSE" : "REQUIREMENT", source,
                    targetRequirement == null ? "CLAUSE" : "REQUIREMENT", target,
                    result.getDouble("confidence"));
            }, changeSetId, organizationId);
    }

    @Override
    public void correctMatch(UUID organizationId, UUID itemId, UUID baseClauseId,
                             UUID targetClauseId, UUID changeTypeConceptId,
                             String reviewStatus, JsonNode attributes) {
        required(jdbc.update("""
            update document_change_item set base_clause_id = ?, target_clause_id = ?,
                change_type_concept_id = ?, review_status = ?,
                change_attributes_json = change_attributes_json || ?::jsonb
            where id = ? and organization_id = ?
            """, baseClauseId, targetClauseId, changeTypeConceptId, reviewStatus,
            json(attributes), itemId, organizationId));
    }

    @Override
    public UUID createImpactJob(UUID organizationId, UUID projectId, UUID changeSetId,
                                UUID impactPolicyVersionId, int totalItems) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into impact_analysis_job (
                id, organization_id, project_id, change_set_id, status,
                impact_policy_version_id, total_item_count, started_at,
                created_at, updated_at
            ) values (?, ?, ?, ?, 'RUNNING', ?, ?, now(), now(), now())
            """, id, organizationId, projectId, changeSetId,
            impactPolicyVersionId, totalItems);
        event(organizationId, id, "STARTED", 0, "Impact analysis started");
        return id;
    }

    @Override
    public void completeImpactJob(UUID organizationId, UUID jobId,
                                  List<AffectedEntity> affectedEntities) {
        int requirements = 0;
        int evaluations = 0;
        int risks = 0;
        for (AffectedEntity affected : affectedEntities) {
            jdbc.update("""
                insert into impact_analysis_result (
                    id, organization_id, impact_analysis_job_id, entity_type,
                    entity_id, impact_concept_id, reason_codes_json, confidence, created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                """, UUID.randomUUID(), organizationId, jobId, affected.entityType(),
                affected.entityId(), affected.impactConceptId(),
                json(mapper.valueToTree(affected.reasonCodes())), affected.confidence());
            requirements += "REQUIREMENT".equals(affected.entityType()) ? 1 : 0;
            evaluations += "COMPLIANCE_EVALUATION".equals(affected.entityType()) ? 1 : 0;
            risks += "RISK".equals(affected.entityType()) ? 1 : 0;
        }
        required(jdbc.update("""
            update impact_analysis_job set status = 'COMPLETED',
                processed_item_count = total_item_count,
                affected_requirement_count = ?, affected_evaluation_count = ?,
                affected_risk_count = ?, completed_at = now(), updated_at = now(),
                version = version + 1 where id = ? and organization_id = ?
            """, requirements, evaluations, risks, jobId, organizationId));
        event(organizationId, jobId, "COMPLETED", 100, "Impact analysis completed");
    }

    @Override
    public Map<String, Object> impactJob(UUID organizationId, UUID jobId) {
        Map<String, Object> result = one("""
            select id, project_id as "projectId", change_set_id as "changeSetId",
                   status, impact_policy_version_id as "impactPolicyVersionId",
                   total_item_count as "totalItemCount",
                   processed_item_count as "processedItemCount",
                   affected_requirement_count as "affectedRequirementCount",
                   affected_evaluation_count as "affectedEvaluationCount",
                   affected_risk_count as "affectedRiskCount",
                   affected_report_count as "affectedReportCount",
                   started_at as "startedAt", completed_at as "completedAt"
            from impact_analysis_job where id = ? and organization_id = ?
            """, jobId, organizationId);
        result.put("affectedEntities", impactResults(organizationId, jobId));
        return result;
    }

    @Override
    public List<Map<String, Object>> impactResults(UUID organizationId, UUID jobId) {
        return rows("""
            select result.id, result.entity_type as "entityType",
                   result.entity_id as "entityId", concept.concept_code as "impactConcept",
                   result.reason_codes_json::text as "reasonCodes",
                   result.confidence, result.created_at as "createdAt"
            from impact_analysis_result result
            join ontology_concept concept on concept.id = result.impact_concept_id
            where result.impact_analysis_job_id = ? and result.organization_id = ?
            order by result.created_at
            """, jobId, organizationId);
    }

    @Override
    public List<Map<String, Object>> impactEvents(UUID organizationId, UUID jobId) {
        return rows("""
            select id, event_type as "eventType", progress, message,
                   occurred_at as "occurredAt"
            from impact_analysis_event where impact_analysis_job_id = ?
              and organization_id = ? order by occurred_at
            """, jobId, organizationId);
    }

    @Override
    public void markStale(UUID organizationId, List<AffectedEntity> affectedEntities,
                          UUID statusConceptId, UUID triggerConceptId, UUID triggerEntityId) {
        for (AffectedEntity affected : affectedEntities) {
            if (!List.of("COMPLIANCE_EVALUATION", "RISK", "CONFLICT", "REPORT")
                .contains(affected.entityType())) {
                continue;
            }
            jdbc.update("""
                insert into analysis_staleness_record (
                    id, organization_id, entity_type, entity_id, status_concept_id,
                    trigger_type_concept_id, trigger_entity_id, reason,
                    detected_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                on conflict (organization_id, entity_type, entity_id, status_concept_id)
                    where resolved_at is null do nothing
                """, UUID.randomUUID(), organizationId, affected.entityType(),
                affected.entityId(), statusConceptId, triggerConceptId, triggerEntityId,
                String.join(",", affected.reasonCodes()));
        }
    }

    private void event(UUID organizationId, UUID jobId, String type,
                       int progress, String message) {
        jdbc.update("""
            insert into impact_analysis_event (
                id, organization_id, impact_analysis_job_id, event_type,
                progress, message, occurred_at
            ) values (?, ?, ?, ?, ?, ?, now())
            """, UUID.randomUUID(), organizationId, jobId, type, progress, message);
    }

    private Map<String, Object> one(String sql, Object... arguments) {
        return jdbc.query(sql, result -> {
            if (!result.next()) {
                throw new IllegalArgumentException("Tenant-scoped change entity not found");
            }
            return row(result);
        }, arguments);
    }

    private List<Map<String, Object>> rows(String sql, Object... arguments) {
        return jdbc.query(sql, (result, index) -> row(result), arguments);
    }

    private Map<String, Object> row(java.sql.ResultSet result) throws java.sql.SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        var metadata = result.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            values.put(metadata.getColumnLabel(index), result.getObject(index));
        }
        return values;
    }

    private String json(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON cannot be serialized", exception);
        }
    }

    private static void required(int count) {
        if (count != 1) {
            throw new IllegalArgumentException("Tenant-scoped change entity not found");
        }
    }
}
