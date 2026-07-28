package com.nanobase.specai.risk.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.risk.application.RiskModels;
import com.nanobase.specai.risk.application.RiskModels.AmbiguityResult;
import com.nanobase.specai.risk.application.RiskModels.ConflictComparisonResult;
import com.nanobase.specai.risk.application.RiskModels.ConflictEntity;
import com.nanobase.specai.risk.application.RiskModels.ImpactGraphEdge;
import com.nanobase.specai.risk.application.RiskModels.PropagationCandidate;
import com.nanobase.specai.risk.application.RiskModels.RiskExposureResult;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.application.RiskModels.RiskSignalResult;
import com.nanobase.specai.risk.application.RiskPersistencePort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRiskPersistence implements RiskPersistencePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcRiskPersistence(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public UUID createProfile(UUID organizationId, UUID projectId, RiskProfileInputs inputs,
                              JsonNode snapshot, String contentHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into risk_analysis_profile (
                id, organization_id, project_id, analysis_profile_id,
                risk_taxonomy_version_id, ontology_version_id, terminology_snapshot_id,
                risk_policy_version_id, conflict_policy_version_id,
                ambiguity_policy_version_id, impact_policy_version_id,
                confidence_policy_version_id, severity_policy_version_id,
                prompt_package_version_id, model_routing_policy_id,
                document_authority_policy_id, snapshot_json, content_hash, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
            """, id, organizationId, projectId, inputs.analysisProfileId(),
            inputs.riskTaxonomyVersionId(), inputs.ontologyVersionId(),
            inputs.terminologySnapshotId(), inputs.riskPolicyVersionId(),
            inputs.conflictPolicyVersionId(), inputs.ambiguityPolicyVersionId(),
            inputs.impactPolicyVersionId(), inputs.confidencePolicyVersionId(),
            inputs.severityPolicyVersionId(), inputs.promptPackageVersionId(),
            inputs.modelRoutingPolicyId(), inputs.documentAuthorityPolicyId(),
            json(snapshot), contentHash);
        return id;
    }

    @Override
    public UUID createJob(UUID organizationId, UUID projectId, UUID profileId,
                          UUID knowledgeSnapshotId, long requirementSetVersion,
                          String createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into risk_analysis_job (
                id, organization_id, project_id, status, risk_analysis_profile_id,
                knowledge_snapshot_id, requirement_set_version, created_by, created_at, updated_at
            ) values (?, ?, ?, 'QUEUED', ?, ?, ?, ?, now(), now())
            """, id, organizationId, projectId, profileId, knowledgeSnapshotId,
            requirementSetVersion, createdBy);
        return id;
    }

    @Override
    public void startJob(UUID organizationId, UUID jobId, int totalCandidates) {
        requireUpdated(jdbc.update("""
            update risk_analysis_job set status = 'RUNNING', total_candidate_count = ?,
                started_at = now(), updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, totalCandidates, jobId, organizationId));
    }

    @Override
    public void finishJob(UUID organizationId, UUID jobId, int processed, int risks,
                          int ambiguities, int conflicts, int manualReviews, int failures) {
        requireUpdated(jdbc.update("""
            update risk_analysis_job set status = ?, processed_candidate_count = ?,
                risk_count = ?, ambiguity_count = ?, conflict_count = ?,
                manual_review_count = ?, failed_count = ?, completed_at = now(),
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, failures == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS",
            processed, risks, ambiguities, conflicts, manualReviews, failures,
            jobId, organizationId));
    }

    @Override
    public void failJob(UUID organizationId, UUID jobId, String errorCode) {
        requireUpdated(jdbc.update("""
            update risk_analysis_job set status = 'FAILED', failed_count = failed_count + 1,
                completed_at = now(), updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, jobId, organizationId));
        event(organizationId, jobId, "FAILED", 100,
            "Risk analysis failed", mapper.valueToTree(Map.of("errorCode", errorCode)));
    }

    @Override
    public void cancelJob(UUID organizationId, UUID jobId) {
        requireUpdated(jdbc.update("""
            update risk_analysis_job set status = 'CANCELLED', completed_at = now(),
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ? and status in ('QUEUED', 'RUNNING')
            """, jobId, organizationId));
    }

    @Override
    public void event(UUID organizationId, UUID jobId, String eventType, int progress,
                      String message, JsonNode metadata) {
        jdbc.update("""
            insert into risk_analysis_event (
                id, organization_id, risk_analysis_job_id, event_type, progress,
                message, metadata_json, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, now())
            """, UUID.randomUUID(), organizationId, jobId, eventType,
            Math.max(0, Math.min(100, progress)), message, json(metadata));
    }

    @Override
    public Optional<Map<String, Object>> job(UUID organizationId, UUID jobId) {
        return one("""
            select id, project_id as "projectId", status,
                   risk_analysis_profile_id as "riskAnalysisProfileId",
                   knowledge_snapshot_id as "knowledgeSnapshotId",
                   requirement_set_version as "requirementSetVersion",
                   total_candidate_count as "totalCandidateCount",
                   processed_candidate_count as "processedCandidateCount",
                   risk_count as "riskCount", ambiguity_count as "ambiguityCount",
                   conflict_count as "conflictCount", manual_review_count as "manualReviewCount",
                   failed_count as "failedCount", started_at as "startedAt",
                   completed_at as "completedAt", created_at as "createdAt"
            from risk_analysis_job where id = ? and organization_id = ?
            """, jobId, organizationId);
    }

    @Override
    public List<Map<String, Object>> jobEvents(UUID organizationId, UUID jobId) {
        return rows("""
            select id, event_type as "eventType", progress, message,
                   metadata_json::text as "metadata", occurred_at as "occurredAt"
            from risk_analysis_event
            where risk_analysis_job_id = ? and organization_id = ?
            order by occurred_at
            """, jobId, organizationId);
    }

    @Override
    public List<RequirementCandidate> requirements(UUID organizationId, UUID projectId) {
        return jdbc.query("""
            select requirement.id, requirement.project_id, requirement.document_id,
                   requirement.document_version_id, requirement.source_clause_id,
                   requirement.primary_concept_id, requirement.requirement_code,
                   requirement.title, requirement.requirement_text,
                   requirement.attributes_json, requirement.combined_confidence,
                   requirement.grounding_coverage, requirement.testability_status,
                   clause.page_start,
                   coalesce(source.bounding_boxes_json, '[]'::jsonb) as bounding_boxes_json
            from requirement
            join clause on clause.id = requirement.source_clause_id
            left join lateral (
                select bounding_boxes_json from requirement_source_fragment
                where requirement_id = requirement.id order by created_at limit 1
            ) source on true
            where requirement.organization_id = ? and requirement.project_id = ?
              and requirement.review_status <> 'REJECTED'
            order by requirement.created_at
            """, (result, row) -> new RequirementCandidate(
                result.getObject("id", UUID.class),
                result.getObject("project_id", UUID.class),
                result.getObject("document_id", UUID.class),
                result.getObject("document_version_id", UUID.class),
                result.getObject("source_clause_id", UUID.class),
                result.getObject("primary_concept_id", UUID.class),
                result.getString("requirement_code"), result.getString("title"),
                result.getString("requirement_text"),
                parse(result.getObject("attributes_json")),
                result.getDouble("combined_confidence"),
                result.getDouble("grounding_coverage"),
                result.getString("testability_status"),
                result.getInt("page_start"),
                parse(result.getObject("bounding_boxes_json"))),
            organizationId, projectId);
    }

    @Override
    public long validEvidenceCount(UUID organizationId, UUID requirementId) {
        Long count = jdbc.queryForObject("""
            select count(*) from compliance_evaluation evaluation
            join compliance_evidence_link link
              on link.compliance_evaluation_id = evaluation.id
            join evidence_validity_assessment validity
              on validity.evidence_claim_id = link.evidence_claim_id
            join ontology_concept concept on concept.id = validity.validity_concept_id
            where evaluation.organization_id = ? and evaluation.requirement_id = ?
              and concept.metadata_json @> '{"usable":true}'::jsonb
              and (validity.valid_until is null or validity.valid_until > now())
            """, Long.class, organizationId, requirementId);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<UUID> latestKnowledgeSnapshot(UUID organizationId, UUID projectId) {
        return jdbc.query("""
            select id from knowledge_snapshot
            where organization_id = ? and project_id = ?
            order by created_at desc limit 1
            """, result -> result.next()
                ? Optional.of(result.getObject(1, UUID.class)) : Optional.empty(),
            organizationId, projectId);
    }

    @Override
    public UUID createRisk(UUID organizationId, UUID projectId, UUID profileId,
                           RequirementCandidate source, UUID riskConceptId,
                           UUID statusConceptId, UUID sourceRoleConceptId,
                           RiskSignalResult signal, RiskExposureResult exposure,
                           double confidence) {
        UUID id = UUID.randomUUID();
        String riskCode = "R-" + id.toString().substring(0, 8).toUpperCase();
        jdbc.update("""
            insert into risk_record (
                id, organization_id, project_id, risk_code, risk_concept_id,
                title, description, source_entity_type, source_entity_id,
                risk_analysis_profile_id, probability_score, impact_score,
                exposure_score, severity_concept_id, confidence, review_status,
                status_concept_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, 'REQUIREMENT', ?, ?, ?, ?, ?, ?, ?,
                      'REVIEW_REQUIRED', ?, now(), now())
            """, id, organizationId, projectId, riskCode, riskConceptId,
            source.title() == null || source.title().isBlank()
                ? source.requirementCode() : source.title(),
            source.text(), source.id(), profileId, exposure.probability().score(),
            exposure.impact().score(), exposure.exposure(), exposure.severityConceptId(),
            confidence, statusConceptId);
        jdbc.update("""
            insert into risk_source (
                id, organization_id, risk_id, source_type, source_id, document_id,
                document_version_id, clause_id, requirement_id, page_number,
                source_text, bounding_boxes_json, source_role_concept_id, created_at
            ) values (?, ?, ?, 'REQUIREMENT', ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
            """, UUID.randomUUID(), organizationId, id, source.id(), source.documentId(),
            source.documentVersionId(), source.clauseId(), source.id(), source.pageNumber(),
            source.text(), json(source.boundingBoxes()), sourceRoleConceptId);
        for (var factor : signal.factors()) {
            jdbc.update("""
                insert into risk_factor (
                    id, organization_id, risk_id, factor_concept_id, factor_value,
                    effect_score, explanation, source_reference_json, created_at
                ) values (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, now())
                """, UUID.randomUUID(), organizationId, id, riskConceptId,
                json(mapper.valueToTree(Map.of("code", factor.code(), "value", factor.value()))),
                factor.effect(), factor.code(),
                json(mapper.valueToTree(Map.of("requirementId", source.id()))));
        }
        return id;
    }

    @Override
    public UUID createAmbiguity(UUID organizationId, UUID projectId, UUID profileId,
                                RequirementCandidate source, AmbiguityResult result,
                                UUID severityConceptId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into ambiguity_finding (
                id, organization_id, project_id, entity_type, entity_id,
                ambiguity_concept_id, analysis_profile_id, description, confidence,
                severity_concept_id, review_status, created_at, updated_at
            ) values (?, ?, ?, 'REQUIREMENT', ?, ?, ?, ?, ?, ?, 'REVIEW_REQUIRED',
                      now(), now())
            """, id, organizationId, projectId, source.id(), result.conceptId(), profileId,
            String.join(", ", result.missingFields()), result.confidence(), severityConceptId);
        jdbc.update("""
            insert into ambiguity_source (
                id, organization_id, ambiguity_finding_id, document_id,
                document_version_id, clause_id, requirement_id, source_text,
                page_number, bounding_boxes_json, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
            """, UUID.randomUUID(), organizationId, id, source.documentId(),
            source.documentVersionId(), source.clauseId(), source.id(), source.text(),
            source.pageNumber(), json(source.boundingBoxes()));
        return id;
    }

    @Override
    public List<ConflictEntity> conflictCandidates(UUID organizationId, UUID projectId,
                                                   RequirementCandidate seed, int limit) {
        return jdbc.query("""
            select requirement.id, requirement.document_id, requirement.document_version_id,
                   requirement.primary_concept_id, requirement.attributes_json,
                   requirement.requirement_text, clause.page_start
            from requirement
            join clause on clause.id = requirement.source_clause_id
            where requirement.organization_id = ? and requirement.project_id = ?
              and requirement.id <> ?
              and requirement.primary_concept_id is not distinct from ?
              and requirement.document_version_id <> ?
              and requirement.review_status <> 'REJECTED'
            order by requirement.created_at desc limit ?
            """, (result, row) -> new ConflictEntity(
                result.getObject("id", UUID.class), "REQUIREMENT",
                result.getObject("document_id", UUID.class),
                result.getObject("document_version_id", UUID.class),
                result.getObject("primary_concept_id", UUID.class),
                "PROJECT:" + projectId, parse(result.getObject("attributes_json")),
                result.getString("requirement_text"), result.getInt("page_start")),
            organizationId, projectId, seed.id(), seed.conceptId(),
            seed.documentVersionId(), limit);
    }

    @Override
    public UUID createConflict(UUID organizationId, UUID projectId, UUID profileId,
                               RequirementCandidate left, ConflictEntity right,
                               UUID conflictConceptId, UUID statusConceptId,
                               UUID leftSideConceptId, UUID rightSideConceptId,
                               ConflictComparisonResult result) {
        if (result.supportingSourceIds().size() < 2
            || !result.supportingSourceIds().contains(left.id())
            || !result.supportingSourceIds().contains(right.id())) {
            throw new IllegalArgumentException("Grounded conflict requires both supplied sources");
        }
        UUID id = UUID.randomUUID();
        String code = "C-" + id.toString().substring(0, 8).toUpperCase();
        jdbc.update("""
            insert into conflict_record (
                id, organization_id, project_id, conflict_code, conflict_concept_id,
                left_entity_type, left_entity_id, right_entity_type, right_entity_id,
                analysis_profile_id, comparison_strategy_code, description,
                suggested_resolution_json, confidence, review_status, status_concept_id,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, 'REQUIREMENT', ?, ?, ?, ?, ?, ?, ?::jsonb, ?,
                      'REVIEW_REQUIRED', ?, now(), now())
            """, id, organizationId, projectId, code, conflictConceptId, left.id(),
            right.entityType(), right.id(), profileId, result.strategyCode(),
            result.description(), json(result.authorityAssessment()), result.confidence(),
            statusConceptId);
        insertConflictSource(id, organizationId, leftSideConceptId, left.documentId(),
            left.documentVersionId(), left.clauseId(), left.id(), left.text(),
            left.pageNumber(), left.boundingBoxes());
        jdbc.query("""
            select source_clause_id, coalesce(source.bounding_boxes_json, '[]'::jsonb) boxes
            from requirement
            left join lateral (
                select bounding_boxes_json from requirement_source_fragment
                where requirement_id = requirement.id order by created_at limit 1
            ) source on true
            where requirement.id = ? and requirement.organization_id = ?
            """, resultSet -> {
                if (resultSet.next()) {
                    insertConflictSource(id, organizationId, rightSideConceptId,
                        right.documentId(), right.documentVersionId(),
                        resultSet.getObject("source_clause_id", UUID.class), right.id(),
                        right.sourceText(), right.pageNumber(),
                        parse(resultSet.getObject("boxes")));
                }
                return null;
            }, right.id(), organizationId);
        return id;
    }

    private void insertConflictSource(UUID conflictId, UUID organizationId,
                                      UUID sideConceptId, UUID documentId,
                                      UUID documentVersionId, UUID clauseId,
                                      UUID requirementId, String text, int page,
                                      JsonNode boxes) {
        jdbc.update("""
            insert into conflict_source (
                id, organization_id, conflict_id, side_concept_id, document_id,
                document_version_id, clause_id, requirement_id, source_text,
                page_number, bounding_boxes_json, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
            """, UUID.randomUUID(), organizationId, conflictId, sideConceptId,
            documentId, documentVersionId, clauseId, requirementId, text, page, json(boxes));
    }

    @Override
    public List<Map<String, Object>> listRisks(UUID organizationId, UUID projectId) {
        return rows("""
            select risk.id, risk.risk_code as "riskCode", risk.title, risk.description,
                   concept.concept_code as "riskConcept", severity.concept_code as severity,
                   risk.probability_score as "probabilityScore",
                   risk.impact_score as "impactScore", risk.exposure_score as "exposureScore",
                   risk.confidence, risk.review_status as "reviewStatus",
                   status.concept_code as status, risk.owner_user_id as "ownerUserId",
                   risk.due_date as "dueDate",
                   stale.concept_code as "stalenessStatus",
                   risk.created_at as "createdAt", risk.updated_at as "updatedAt"
            from risk_record risk
            join ontology_concept concept on concept.id = risk.risk_concept_id
            left join ontology_concept severity on severity.id = risk.severity_concept_id
            join ontology_concept status on status.id = risk.status_concept_id
            left join lateral (
                select concept.concept_code from analysis_staleness_record record
                join ontology_concept concept on concept.id = record.status_concept_id
                where record.organization_id = risk.organization_id
                  and record.entity_type = 'RISK' and record.entity_id = risk.id
                  and record.resolved_at is null order by record.detected_at desc limit 1
            ) stale on true
            where risk.organization_id = ? and risk.project_id = ?
            order by risk.created_at desc
            """, organizationId, projectId);
    }

    @Override
    public Optional<Map<String, Object>> risk(UUID organizationId, UUID riskId) {
        return one("""
            select risk.*, concept.concept_code as "riskConcept",
                   severity.concept_code as severity, status.concept_code as status
            from risk_record risk
            join ontology_concept concept on concept.id = risk.risk_concept_id
            left join ontology_concept severity on severity.id = risk.severity_concept_id
            join ontology_concept status on status.id = risk.status_concept_id
            where risk.id = ? and risk.organization_id = ?
            """, riskId, organizationId);
    }

    @Override
    public List<Map<String, Object>> riskSources(UUID organizationId, UUID riskId) {
        return rows("""
            select id, source_type as "sourceType", source_id as "sourceId",
                   document_id as "documentId", document_version_id as "documentVersionId",
                   clause_id as "clauseId", requirement_id as "requirementId",
                   compliance_evaluation_id as "complianceEvaluationId",
                   evidence_fragment_id as "evidenceFragmentId", page_number as "pageNumber",
                   source_text as "sourceText", bounding_boxes_json::text as "boundingBoxes",
                   source_role_concept_id as "sourceRoleConceptId"
            from risk_source where risk_id = ? and organization_id = ?
            order by created_at
            """, riskId, organizationId);
    }

    @Override
    public List<Map<String, Object>> riskFactors(UUID organizationId, UUID riskId) {
        return rows("""
            select factor.id, concept.concept_code as concept,
                   factor.factor_value::text as value, factor.effect_score as effect,
                   factor.explanation, factor.source_reference_json::text as "sourceReference"
            from risk_factor factor
            join ontology_concept concept on concept.id = factor.factor_concept_id
            where factor.risk_id = ? and factor.organization_id = ?
            order by factor.created_at
            """, riskId, organizationId);
    }

    @Override
    public List<Map<String, Object>> riskHistory(UUID organizationId, UUID riskId) {
        return rows("""
            select revision.id, revision.revision_number as "revisionNumber",
                   revision.snapshot_json::text as snapshot,
                   concept.concept_code as "changeType", revision.created_by as "createdBy",
                   revision.created_at as "createdAt"
            from risk_revision revision
            join ontology_concept concept on concept.id = revision.change_type_concept_id
            where revision.risk_id = ? and revision.organization_id = ?
            order by revision.revision_number desc
            """, riskId, organizationId);
    }

    @Override
    public void reviewRisk(UUID organizationId, UUID riskId, String reviewStatus,
                           UUID changeConceptId, String actor, JsonNode feedback) {
        snapshotRisk(organizationId, riskId, changeConceptId, actor);
        requireUpdated(jdbc.update("""
            update risk_record set review_status = ?, updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, reviewStatus, riskId, organizationId));
    }

    @Override
    public void assignRisk(UUID organizationId, UUID riskId, String ownerUserId,
                           LocalDate dueDate, UUID changeConceptId, String actor) {
        snapshotRisk(organizationId, riskId, changeConceptId, actor);
        requireUpdated(jdbc.update("""
            update risk_record set owner_user_id = ?, due_date = ?, updated_at = now(),
                version = version + 1 where id = ? and organization_id = ?
            """, ownerUserId, dueDate, riskId, organizationId));
    }

    @Override
    public void updateRisk(UUID organizationId, UUID riskId, String title, String description,
                           Double probability, Double impact, Double exposure,
                           UUID severityConceptId, UUID statusConceptId,
                           UUID changeConceptId, String actor) {
        snapshotRisk(organizationId, riskId, changeConceptId, actor);
        requireUpdated(jdbc.update("""
            update risk_record set
                title = coalesce(?, title), description = coalesce(?, description),
                probability_score = coalesce(?, probability_score),
                impact_score = coalesce(?, impact_score),
                exposure_score = coalesce(?, exposure_score),
                severity_concept_id = coalesce(?, severity_concept_id),
                status_concept_id = coalesce(?, status_concept_id),
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, title, description, probability, impact, exposure,
            severityConceptId, statusConceptId, riskId, organizationId));
    }

    private void snapshotRisk(UUID organizationId, UUID riskId, UUID changeConceptId,
                              String actor) {
        jdbc.update("""
            insert into risk_revision (
                id, organization_id, risk_id, revision_number, snapshot_json,
                change_type_concept_id, created_by, created_at
            )
            select ?, organization_id, id,
                   coalesce((select max(revision_number) + 1 from risk_revision
                             where risk_id = risk.id), 1),
                   to_jsonb(risk), ?, ?, now()
            from risk_record risk where id = ? and organization_id = ?
            """, UUID.randomUUID(), changeConceptId, actor, riskId, organizationId);
    }

    @Override
    public List<Map<String, Object>> listAmbiguities(UUID organizationId, UUID projectId) {
        return rows("""
            select finding.id, finding.entity_type as "entityType",
                   finding.entity_id as "entityId", concept.concept_code as concept,
                   finding.description, finding.confidence,
                   severity.concept_code as severity,
                   finding.review_status as "reviewStatus",
                   finding.created_at as "createdAt"
            from ambiguity_finding finding
            join ontology_concept concept on concept.id = finding.ambiguity_concept_id
            left join ontology_concept severity on severity.id = finding.severity_concept_id
            where finding.organization_id = ? and finding.project_id = ?
            order by finding.created_at desc
            """, organizationId, projectId);
    }

    @Override
    public Optional<Map<String, Object>> ambiguity(UUID organizationId, UUID ambiguityId) {
        return one("""
            select finding.*, concept.concept_code as concept,
                   severity.concept_code as severity
            from ambiguity_finding finding
            join ontology_concept concept on concept.id = finding.ambiguity_concept_id
            left join ontology_concept severity on severity.id = finding.severity_concept_id
            where finding.id = ? and finding.organization_id = ?
            """, ambiguityId, organizationId);
    }

    @Override
    public List<Map<String, Object>> ambiguitySources(UUID organizationId, UUID ambiguityId) {
        return rows("""
            select id, document_id as "documentId",
                   document_version_id as "documentVersionId", clause_id as "clauseId",
                   requirement_id as "requirementId", source_text as "sourceText",
                   page_number as "pageNumber", bounding_boxes_json::text as "boundingBoxes"
            from ambiguity_source where ambiguity_finding_id = ? and organization_id = ?
            """, ambiguityId, organizationId);
    }

    @Override
    public List<Map<String, Object>> interpretations(UUID organizationId, UUID ambiguityId) {
        return rows("""
            select id, interpretation_text as "interpretationText",
                   interpretation_attributes_json::text as attributes, confidence,
                   supporting_source_ids_json::text as "supportingSourceIds",
                   created_at as "createdAt"
            from ambiguity_interpretation
            where ambiguity_finding_id = ? and organization_id = ? order by created_at
            """, ambiguityId, organizationId);
    }

    @Override
    public UUID addInterpretation(UUID organizationId, UUID ambiguityId, String text,
                                  JsonNode attributes, double confidence, JsonNode sourceIds) {
        if (ambiguity(organizationId, ambiguityId).isEmpty()) {
            throw new IllegalArgumentException("Ambiguity finding not found");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into ambiguity_interpretation (
                id, organization_id, ambiguity_finding_id, interpretation_text,
                interpretation_attributes_json, confidence, supporting_source_ids_json,
                created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, now())
            """, id, organizationId, ambiguityId, text, json(attributes),
            confidence, json(sourceIds));
        return id;
    }

    @Override
    public void reviewAmbiguity(UUID organizationId, UUID ambiguityId, String reviewStatus) {
        requireUpdated(jdbc.update("""
            update ambiguity_finding set review_status = ?, updated_at = now(),
                version = version + 1 where id = ? and organization_id = ?
            """, reviewStatus, ambiguityId, organizationId));
    }

    @Override
    public List<Map<String, Object>> listConflicts(UUID organizationId, UUID projectId) {
        return rows("""
            select conflict.id, conflict.conflict_code as "conflictCode",
                   concept.concept_code as concept, conflict.left_entity_type as "leftEntityType",
                   conflict.left_entity_id as "leftEntityId",
                   conflict.right_entity_type as "rightEntityType",
                   conflict.right_entity_id as "rightEntityId",
                   conflict.comparison_strategy_code as "comparisonStrategyCode",
                   conflict.description, conflict.confidence,
                   severity.concept_code as severity,
                   conflict.review_status as "reviewStatus",
                   status.concept_code as status, conflict.created_at as "createdAt"
            from conflict_record conflict
            join ontology_concept concept on concept.id = conflict.conflict_concept_id
            left join ontology_concept severity on severity.id = conflict.severity_concept_id
            join ontology_concept status on status.id = conflict.status_concept_id
            where conflict.organization_id = ? and conflict.project_id = ?
            order by conflict.created_at desc
            """, organizationId, projectId);
    }

    @Override
    public Optional<Map<String, Object>> conflict(UUID organizationId, UUID conflictId) {
        return one("""
            select conflict.*, concept.concept_code as concept,
                   severity.concept_code as severity, status.concept_code as status
            from conflict_record conflict
            join ontology_concept concept on concept.id = conflict.conflict_concept_id
            left join ontology_concept severity on severity.id = conflict.severity_concept_id
            join ontology_concept status on status.id = conflict.status_concept_id
            where conflict.id = ? and conflict.organization_id = ?
            """, conflictId, organizationId);
    }

    @Override
    public List<Map<String, Object>> conflictSources(UUID organizationId, UUID conflictId) {
        return rows("""
            select source.id, side.concept_code as side, source.document_id as "documentId",
                   source.document_version_id as "documentVersionId",
                   source.clause_id as "clauseId", source.requirement_id as "requirementId",
                   source.evidence_fragment_id as "evidenceFragmentId",
                   source.source_text as "sourceText", source.page_number as "pageNumber",
                   source.bounding_boxes_json::text as "boundingBoxes",
                   source.authority_score as "authorityScore"
            from conflict_source source
            join ontology_concept side on side.id = source.side_concept_id
            where source.conflict_id = ? and source.organization_id = ?
            order by source.created_at
            """, conflictId, organizationId);
    }

    @Override
    public List<Map<String, Object>> conflictFactors(UUID organizationId, UUID conflictId) {
        return rows("""
            select factor.id, concept.concept_code as concept,
                   factor.effect_score as effect, factor.description,
                   factor.metadata_json::text as metadata
            from conflict_factor factor
            join ontology_concept concept on concept.id = factor.factor_concept_id
            where factor.conflict_id = ? and factor.organization_id = ?
            """, conflictId, organizationId);
    }

    @Override
    public List<Map<String, Object>> conflictHistory(UUID organizationId, UUID conflictId) {
        return rows("""
            select revision.id, revision.revision_number as "revisionNumber",
                   revision.snapshot_json::text as snapshot,
                   concept.concept_code as "changeType", revision.created_by as "createdBy",
                   revision.created_at as "createdAt"
            from conflict_revision revision
            join ontology_concept concept on concept.id = revision.change_type_concept_id
            where revision.conflict_id = ? and revision.organization_id = ?
            order by revision.revision_number desc
            """, conflictId, organizationId);
    }

    @Override
    public void reviewConflict(UUID organizationId, UUID conflictId, String reviewStatus,
                               UUID changeConceptId, String actor, JsonNode resolution) {
        jdbc.update("""
            insert into conflict_revision (
                id, organization_id, conflict_id, revision_number, snapshot_json,
                change_type_concept_id, created_by, created_at
            )
            select ?, organization_id, id,
                   coalesce((select max(revision_number) + 1 from conflict_revision
                             where conflict_id = conflict.id), 1),
                   to_jsonb(conflict), ?, ?, now()
            from conflict_record conflict where id = ? and organization_id = ?
            """, UUID.randomUUID(), changeConceptId, actor, conflictId, organizationId);
        requireUpdated(jdbc.update("""
            update conflict_record set review_status = ?,
                suggested_resolution_json = ?::jsonb, updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, reviewStatus, json(resolution), conflictId, organizationId));
    }

    @Override
    public List<ImpactGraphEdge> dependencyGraph(UUID organizationId, UUID projectId) {
        List<ImpactGraphEdge> edges = new ArrayList<>();
        edges.addAll(jdbc.query("""
            select dependency.source_requirement_id, dependency.target_requirement_id,
                   dependency.dependency_concept_id, dependency.confidence
            from requirement_dependency dependency
            join requirement source on source.id = dependency.source_requirement_id
            where dependency.organization_id = ? and source.project_id = ?
              and dependency.review_status <> 'REJECTED'
            """, (result, row) -> new ImpactGraphEdge(
                "REQUIREMENT", result.getObject("source_requirement_id", UUID.class),
                "REQUIREMENT", result.getObject("target_requirement_id", UUID.class),
                result.getObject("dependency_concept_id", UUID.class),
                result.getDouble("confidence")), organizationId, projectId));
        edges.addAll(jdbc.query("""
            select evaluation.requirement_id, evaluation.id, evaluation.decision_concept_id,
                   evaluation.confidence from compliance_evaluation evaluation
            where evaluation.organization_id = ? and evaluation.project_id = ?
            """, (result, row) -> new ImpactGraphEdge(
                "REQUIREMENT", result.getObject("requirement_id", UUID.class),
                "COMPLIANCE_EVALUATION", result.getObject("id", UUID.class),
                result.getObject("decision_concept_id", UUID.class),
                result.getDouble("confidence")), organizationId, projectId));
        edges.addAll(jdbc.query("""
            select risk.source_entity_id, risk.id, risk.risk_concept_id, risk.confidence
            from risk_record risk where risk.organization_id = ? and risk.project_id = ?
            """, (result, row) -> new ImpactGraphEdge(
                "REQUIREMENT", result.getObject("source_entity_id", UUID.class),
                "RISK", result.getObject("id", UUID.class),
                result.getObject("risk_concept_id", UUID.class),
                result.getDouble("confidence")), organizationId, projectId));
        return List.copyOf(edges);
    }

    @Override
    public void savePropagation(UUID organizationId, UUID riskId,
                                List<PropagationCandidate> candidates) {
        for (PropagationCandidate candidate : candidates) {
            jdbc.update("""
                insert into risk_propagation_candidate (
                    id, organization_id, source_risk_id, target_entity_type,
                    target_entity_id, propagation_concept_id, path_json, confidence,
                    review_status, created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'REVIEW_REQUIRED', now())
                """, UUID.randomUUID(), organizationId, riskId, candidate.targetEntityType(),
                candidate.targetEntityId(), candidate.propagationConceptId(),
                json(mapper.valueToTree(candidate.path())), candidate.confidence());
        }
    }

    @Override
    public List<Map<String, Object>> propagation(UUID organizationId, UUID riskId) {
        return rows("""
            select candidate.id, candidate.target_entity_type as "targetEntityType",
                   candidate.target_entity_id as "targetEntityId",
                   concept.concept_code as concept, candidate.path_json::text as path,
                   candidate.confidence, candidate.review_status as "reviewStatus",
                   candidate.created_at as "createdAt"
            from risk_propagation_candidate candidate
            join ontology_concept concept on concept.id = candidate.propagation_concept_id
            where candidate.source_risk_id = ? and candidate.organization_id = ?
            order by candidate.confidence desc
            """, riskId, organizationId);
    }

    @Override
    public List<Map<String, Object>> mitigationCandidates(UUID organizationId, UUID riskId) {
        return rows("""
            select candidate.id, concept.concept_code as "actionConcept",
                   candidate.description, candidate.confidence,
                   candidate.review_status as "reviewStatus",
                   candidate.created_at as "createdAt"
            from mitigation_candidate candidate
            join ontology_concept concept on concept.id = candidate.action_concept_id
            where candidate.risk_id = ? and candidate.organization_id = ?
            order by candidate.confidence desc
            """, riskId, organizationId);
    }

    @Override
    public void reviewMitigation(UUID organizationId, UUID riskId, UUID candidateId,
                                 String reviewStatus) {
        requireUpdated(jdbc.update("""
            update mitigation_candidate set review_status = ?
            where id = ? and risk_id = ? and organization_id = ?
            """, reviewStatus, candidateId, riskId, organizationId));
    }

    @Override
    public UUID createClarificationCandidate(UUID organizationId, UUID projectId,
                                             String entityType, UUID entityId,
                                             UUID strategyVersionId, String question,
                                             String reason, JsonNode sourceIds,
                                             UUID priorityConceptId, boolean legalReview) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into clarification_candidate (
                id, organization_id, project_id, source_entity_type, source_entity_id,
                strategy_version_id, question, reason, source_ids_json,
                priority_concept_id, requires_legal_review, review_status,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'CANDIDATE', now(), now())
            """, id, organizationId, projectId, entityType, entityId,
            strategyVersionId, question, reason, json(sourceIds),
            priorityConceptId, legalReview);
        return id;
    }

    private Optional<Map<String, Object>> one(String sql, Object... arguments) {
        return jdbc.query(sql, result -> {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(row(result));
        }, arguments);
    }

    private List<Map<String, Object>> rows(String sql, Object... arguments) {
        return jdbc.query(sql, (result, rowNumber) -> row(result), arguments);
    }

    private Map<String, Object> row(java.sql.ResultSet result) throws java.sql.SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        var metadata = result.getMetaData();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            values.put(metadata.getColumnLabel(index), result.getObject(index));
        }
        return values;
    }

    private JsonNode parse(Object value) {
        try {
            return mapper.readTree(String.valueOf(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid persisted JSON", exception);
        }
    }

    private String json(JsonNode value) {
        try {
            return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON cannot be serialized", exception);
        }
    }

    private static void requireUpdated(int count) {
        if (count != 1) {
            throw new IllegalArgumentException("Tenant-scoped risk entity was not found");
        }
    }
}
