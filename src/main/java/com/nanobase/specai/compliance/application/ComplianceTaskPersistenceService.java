package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence;
import com.nanobase.specai.compliance.application.ComplianceSemanticRouter.RoutingTrace;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceTaskPersistenceService {
    private static final Logger log =
        LoggerFactory.getLogger(ComplianceTaskPersistenceService.class);

    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext tenantDatabase;
    private final ObjectMapper mapper;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;
    private final ComplianceJobTransactionService transactionService;
    private final CompliancePostAssessmentHooks postAssessmentHooks;

    public ComplianceTaskPersistenceService(JdbcTemplate jdbc,
                                            TenantDatabaseContext tenantDatabase,
                                            ObjectMapper mapper,
                                            OutboxService outbox,
                                            PlatformMetrics metrics,
                                            ComplianceJobTransactionService transactionService,
                                            CompliancePostAssessmentHooks postAssessmentHooks) {
        this.jdbc = jdbc;
        this.tenantDatabase = tenantDatabase;
        this.mapper = mapper;
        this.outbox = outbox;
        this.metrics = metrics;
        this.transactionService = transactionService;
        this.postAssessmentHooks = postAssessmentHooks;
    }

    public record TaskPersistenceResult(
        boolean persisted,
        boolean rejectedStale,
        boolean cancelled,
        boolean requiresReview,
        UUID evaluationId,
        String errorCode
    ) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskPersistenceResult persist(PreparedComplianceTask prepared,
                                         ComplianceExecutionResult execution,
                                         UUID correlationId) {
        tenantDatabase.apply(prepared.organizationId());
        log.info("event=COMPLIANCE_TASK_PERSIST_STARTED jobId={} taskId={} leaseGeneration={}",
            prepared.jobId(), prepared.taskId(), prepared.leaseGeneration());
        long started = System.nanoTime();
        var cancel = transactionService.cancellationState(
            prepared.organizationId(), prepared.jobId());
        if (cancel.cancelRequested() || execution.cancelled()) {
            int cancelled = jdbc.update("""
                update requirement_matching_task
                   set status = 'CANCELLED',
                       candidate_count = ?,
                       reranked_candidate_count = ?,
                       completed_at = clock_timestamp(),
                       updated_at = clock_timestamp(),
                       version = version + 1
                 where id = ?
                   and organization_id = ?
                   and claimed_by = ?
                   and lease_generation = ?
                   and status in ('RUNNING', 'READY_FOR_MODEL')
                """, prepared.candidateCount(), prepared.rerankedCandidateCount(),
                prepared.taskId(), prepared.organizationId(), prepared.workerId(),
                prepared.leaseGeneration());
            metrics.compliancePersistDuration(
                java.time.Duration.ofNanos(System.nanoTime() - started));
            log.info("event=COMPLIANCE_TASK_PERSIST_COMPLETED jobId={} taskId={} result=CANCELLED "
                    + "rows={}",
                prepared.jobId(), prepared.taskId(), cancelled);
            return new TaskPersistenceResult(cancelled > 0, false, true, false, null,
                "CANCEL_REQUESTED");
        }
        if (!execution.success()) {
            int failed = jdbc.update("""
                update requirement_matching_task
                   set status = 'FAILED',
                       error_code = ?,
                       error_message = ?,
                       last_error_code = ?,
                       last_error_message = ?,
                       candidate_count = ?,
                       reranked_candidate_count = ?,
                       completed_at = clock_timestamp(),
                       updated_at = clock_timestamp(),
                       version = version + 1
                 where id = ?
                   and organization_id = ?
                   and claimed_by = ?
                   and lease_generation = ?
                   and status in ('RUNNING', 'READY_FOR_MODEL')
                """, execution.errorCode(), truncate(execution.errorMessage()),
                execution.errorCode(), truncate(execution.errorMessage()),
                prepared.candidateCount(), prepared.rerankedCandidateCount(),
                prepared.taskId(), prepared.organizationId(), prepared.workerId(),
                prepared.leaseGeneration());
            if (failed == 0) {
                metrics.complianceStaleWorkerResult();
                metrics.complianceFencingRejection();
                log.info("event=COMPLIANCE_TASK_PERSIST_REJECTED_STALE jobId={} taskId={}",
                    prepared.jobId(), prepared.taskId());
                return new TaskPersistenceResult(false, true, false, false, null,
                    "STALE_WORKER_RESULT");
            }
            metrics.compliancePersistDuration(
                java.time.Duration.ofNanos(System.nanoTime() - started));
            return new TaskPersistenceResult(true, false, false, false, null,
                execution.errorCode());
        }

        // Fence before any evaluation/evidence writes so a stale worker cannot leave orphans.
        Integer fenceOk = jdbc.query("""
            select 1
              from requirement_matching_task
             where id = ?
               and organization_id = ?
               and claimed_by = ?
               and lease_generation = ?
               and status in ('RUNNING', 'READY_FOR_MODEL')
             limit 1
            """, rs -> rs.next() ? 1 : null,
            prepared.taskId(), prepared.organizationId(), prepared.workerId(),
            prepared.leaseGeneration());
        if (fenceOk == null) {
            metrics.complianceStaleWorkerResult();
            metrics.complianceFencingRejection();
            log.info("event=COMPLIANCE_TASK_PERSIST_REJECTED_STALE jobId={} taskId={} "
                    + "leaseGeneration={}",
                prepared.jobId(), prepared.taskId(), prepared.leaseGeneration());
            return new TaskPersistenceResult(false, true, false, false, null,
                "STALE_WORKER_RESULT");
        }

        UUID evaluationId = persistEvaluation(prepared, execution);
        try {
            postAssessmentHooks.afterAssessment(prepared.organizationId(), prepared.projectId(),
                prepared.requirementId(), evaluationId, execution.decisionCode(),
                execution.gapHint(), execution.missingElements(),
                execution.ambiguousRequirement(), "system");
        } catch (RuntimeException hookFailure) {
            log.warn("post_assessment_hook_failed evaluationId={} error={}",
                evaluationId, hookFailure.toString());
        }
        if (execution.evaluationSource() != null) {
            jdbc.update("""
                update compliance_evaluation
                   set evaluation_source = ?,
                       missing_requirement_elements_json = ?::jsonb,
                       explicit_contradiction = ?,
                       reasoning_summary = ?,
                       assessment_status = 'COMPLETED',
                       updated_at = clock_timestamp()
                 where id = ? and organization_id = ?
                """, execution.evaluationSource(),
                json(execution.missingElements() == null
                    ? List.of() : execution.missingElements()),
                execution.contradiction(),
                execution.summary().path("reasonCode").asText(null),
                evaluationId, prepared.organizationId());
        }
        int completed = jdbc.update("""
            update requirement_matching_task
               set status = 'COMPLETED',
                   candidate_count = ?,
                   reranked_candidate_count = ?,
                   selected_evidence_count = ?,
                   evaluation_count = 1,
                   model_run_id = ?,
                   completed_at = clock_timestamp(),
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and claimed_by = ?
               and lease_generation = ?
               and status in ('RUNNING', 'READY_FOR_MODEL')
            """, prepared.candidateCount(), prepared.rerankedCandidateCount(),
            execution.selectedEvidence().size(), execution.modelRunId(),
            prepared.taskId(), prepared.organizationId(), prepared.workerId(),
            prepared.leaseGeneration());
        if (completed == 0) {
            // Race after pre-check: roll back the whole persist TX so evaluation is not orphaned.
            metrics.complianceStaleWorkerResult();
            metrics.complianceFencingRejection();
            log.info("event=COMPLIANCE_TASK_PERSIST_REJECTED_STALE jobId={} taskId={} "
                    + "leaseGeneration={}",
                prepared.jobId(), prepared.taskId(), prepared.leaseGeneration());
            throw new IllegalStateException(
                "STALE_WORKER_RESULT after evaluation insert; rolling back persist transaction");
        }
        if (execution.requiresReview()) {
            metrics.complianceManualReview();
            outbox.publish(prepared.organizationId(), "ComplianceEvaluation", evaluationId,
                "ComplianceReviewRequired", "compliance.review.required.v1",
                Map.of("evaluationId", evaluationId,
                    "requirementId", prepared.requirementId()), correlationId);
        }
        if (execution.contradiction()) {
            metrics.complianceContradictoryEvidence();
        }
        metrics.complianceEvaluation();
        metrics.compliancePersistDuration(
            java.time.Duration.ofNanos(System.nanoTime() - started));
        log.info("event=COMPLIANCE_TASK_PERSIST_COMPLETED jobId={} taskId={} evaluationId={}",
            prepared.jobId(), prepared.taskId(), evaluationId);
        return new TaskPersistenceResult(true, false, false, execution.requiresReview(),
            evaluationId, null);
    }

    private UUID persistEvaluation(PreparedComplianceTask prepared,
                                   ComplianceExecutionResult outcome) {
        UUID evaluationId = UUID.randomUUID();
        UUID grounding = conceptByMetadata(prepared.organizationId(), "grounded",
            !outcome.selectedEvidence().isEmpty());
        RoutingTrace routing = outcome.routing();
        var policies = prepared.policies();
        jdbc.update("""
            insert into compliance_evaluation (
                id, organization_id, project_id, requirement_id, target_scope_json,
                analysis_job_id, knowledge_snapshot_id, suggested_decision_concept_id,
                comparison_summary_json, combined_confidence,
                grounding_status_concept_id, review_status, analysis_profile_id,
                retrieval_policy_version_id, matching_policy_version_id,
                comparison_policy_version_id, confidence_policy_version_id,
                prompt_package_version_id,
                live_model_profile, shadow_model_profile, shadow_result_json,
                shadow_comparison_json, escalation_reason,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?::jsonb, ?::jsonb, ?,
                      clock_timestamp(), clock_timestamp())
            """, evaluationId, prepared.organizationId(), prepared.projectId(),
            prepared.requirementId(),
            prepared.targetEntityId() == null ? "{}"
                : json(Map.of("targetEntityId", prepared.targetEntityId())),
            prepared.jobId(), policies.knowledgeSnapshotId(), outcome.decisionConceptId(),
            outcome.summary().toString(),
            outcome.confidence(), grounding,
            outcome.requiresReview() ? "REQUIRES_REVIEW" : "AI_RECOMMENDATION",
            policies.analysisProfileId(), policies.retrievalPolicyVersionId(),
            policies.matchingPolicyVersionId(), policies.comparisonPolicyVersionId(),
            policies.confidencePolicyVersionId(), policies.promptPackageVersionId(),
            routing == null ? null : routing.liveProfile(),
            routing == null ? null : routing.shadowProfile(),
            routing == null || routing.shadowResult() == null
                ? null : routing.shadowResult().toString(),
            routing == null || routing.comparison() == null
                ? null : routing.comparison().toString(),
            routing == null ? null : routing.escalationReason());
        for (RankedEvidence selected : outcome.selectedEvidence()) {
            boolean contradiction = outcome.contradiction();
            UUID role = conceptByMetadata(prepared.organizationId(), "polarity",
                contradiction ? -1 : 1);
            jdbc.update("""
                insert into compliance_evidence_link (
                    id, organization_id, compliance_evaluation_id,
                    evidence_fragment_id, evidence_claim_id, relation_role_concept_id,
                    relevance_score, validity_score, support_strength,
                    contradiction_strength, selected_by_type, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, clock_timestamp())
                """, UUID.randomUUID(), prepared.organizationId(), evaluationId,
                selected.evidence().fragmentId(), selected.evidence().claimId(), role,
                selected.score(), selected.evidence().validityScore(),
                contradiction ? 0 : selected.score(),
                contradiction ? selected.score() : 0,
                outcome.modelRunId() == null ? "DETERMINISTIC" : "MODEL");
        }
        return evaluationId;
    }

    private UUID conceptByMetadata(UUID organizationId, String key, Object value) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
             where (organization_id = ? or organization_id is null)
               and metadata_json ->> ? = ?
             order by (organization_id is not null) desc limit 1
            """, (rs, row) -> rs.getObject(1, UUID.class),
            organizationId, key, String.valueOf(value));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Compliance value is not serializable", exception);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "Compliance task failed";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
