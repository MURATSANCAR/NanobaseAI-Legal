package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.compliance.application.ComplianceAiGateway.SemanticRequest;
import com.nanobase.specai.compliance.application.ComplianceModels.CandidateEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ComparisonResult;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceContext;
import com.nanobase.specai.compliance.application.ComplianceModels.ConfidenceResult;
import com.nanobase.specai.compliance.application.ComplianceModels.EvidenceRerankingContext;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.RankedEvidenceResult;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.decision.application.TenderSummaryService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.ClaimOutcome;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.JobClaimResult;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.JobFinalizationResult;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ComplianceAnalysisProcessor {
    private static final Logger log = LoggerFactory.getLogger(ComplianceAnalysisProcessor.class);
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);

    private final TenantDatabaseContext tenantDatabase;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EvidenceCandidateRetriever retriever;
    private final EvidenceReranker reranker;
    private final ComparisonStrategyRegistry comparisons;
    private final PolicyComplianceConfidenceEngine confidenceEngine;
    private final ComplianceSemanticRouter semanticRouter;
    private final ComplianceDecisionSafetyGuard decisionSafetyGuard;
    private final ComplianceJobService jobs;
    private final ComplianceJobTransactionService transactionService;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;
    private final FeatureFlagService featureFlags;
    private final DeterministicComplianceEvaluator deterministicEvaluator;
    private final CompliancePostAssessmentHooks postAssessmentHooks;
    private final TenderSummaryService tenderSummaryService;
    private final AuditService audit;
    private final int maxOutputTokens;
    private final int evaluationParallelism;

    public ComplianceAnalysisProcessor(
        TenantDatabaseContext tenantDatabase,
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        EvidenceCandidateRetriever retriever,
        EvidenceReranker reranker,
        ComparisonStrategyRegistry comparisons,
        PolicyComplianceConfidenceEngine confidenceEngine,
        ComplianceSemanticRouter semanticRouter,
        ComplianceJobService jobs,
        ComplianceJobTransactionService transactionService,
        OutboxService outbox,
        PlatformMetrics metrics,
        FeatureFlagService featureFlags,
        DeterministicComplianceEvaluator deterministicEvaluator,
        CompliancePostAssessmentHooks postAssessmentHooks,
        TenderSummaryService tenderSummaryService,
        AuditService audit,
        @org.springframework.beans.factory.annotation.Value(
            "${specai.ai-orchestrator.compliance-max-output-tokens:1024}") int maxOutputTokens,
        @org.springframework.beans.factory.annotation.Value(
            "${specai.compliance.evaluation-parallelism:1}") int evaluationParallelism
    ) {
        this.tenantDatabase = tenantDatabase;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.retriever = retriever;
        this.reranker = reranker;
        this.comparisons = comparisons;
        this.confidenceEngine = confidenceEngine;
        this.semanticRouter = semanticRouter;
        this.decisionSafetyGuard = new ComplianceDecisionSafetyGuard(mapper);
        this.jobs = jobs;
        this.transactionService = transactionService;
        this.outbox = outbox;
        this.metrics = metrics;
        this.featureFlags = featureFlags;
        this.deterministicEvaluator = deterministicEvaluator;
        this.postAssessmentHooks = postAssessmentHooks;
        this.tenderSummaryService = tenderSummaryService;
        this.audit = audit;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
        this.evaluationParallelism = evaluationParallelism <= 0 ? 1 : evaluationParallelism;
        if (this.evaluationParallelism != 1) {
            log.warn(
                "compliance_evaluation_parallelism_override requested={} enforced=1 "
                    + "(production V1 is single-slot sequential)",
                evaluationParallelism);
        }
        log.info("compliance_evaluation_policy parallelism=1 maxOutputTokens={}",
            this.maxOutputTokens);
    }

    /**
     * Orchestrates compliance analysis without owning a long-lived database
     * transaction. Claim/heartbeat/finalize use short transactions via
     * {@link ComplianceJobTransactionService}. LLM and slot waits run outside TX.
     */
    public void process(UUID organizationId, UUID jobId, UUID correlationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "ComplianceAnalysisProcessor.process must not run inside a transaction");
        }
        tenantDatabase.apply(organizationId);
        Instant claimStarted = Instant.now();
        String workerId = ComplianceJobTransactionService.normalizeWorkerId(
            "worker-" + jobId + "-" + UUID.randomUUID());
        Instant leaseExpiresAt = Instant.now()
            .plus(ComplianceJobTransactionService.DEFAULT_LEASE);
        log.info("event=COMPLIANCE_JOB_CLAIM_ATTEMPTED jobId={} workerId={} correlationId={}",
            jobId, workerId, correlationId);
        JobClaimResult claim = transactionService.claimJob(
            organizationId, jobId, workerId, leaseExpiresAt);
        long claimDurationMs = Duration.between(claimStarted, Instant.now()).toMillis();
        metrics.complianceJobClaim(claim.claimed(), claimDurationMs);
        if (claim.claimed() && claim.attemptCount() > 1) {
            metrics.complianceJobReclaimed();
            log.info("event=COMPLIANCE_JOB_RECLAIMED jobId={} workerId={}", jobId, workerId);
        }
        if (!claim.claimed()) {
            String errorCode = ComplianceJobTransactionService.orchestrationErrorCode(
                claim.outcome());
            log.info("event=COMPLIANCE_JOB_CLAIM_SKIPPED jobId={} outcome={} errorCode={} "
                    + "claimDurationMs={}",
                jobId, claim.outcome(), errorCode, claimDurationMs);
            return;
        }
        metrics.complianceAnalysis();
        metrics.complianceJobRunning();
        Job job = Job.fromClaim(claim);
        jobs.event(organizationId, jobId, "STARTED", 0,
            "Compliance analysis started",
            Map.of("requirementCount", job.totalRequirementCount(),
                "workerId", workerId, "claimDurationMs", claimDurationMs));
        outbox.publish(organizationId, "ComplianceAnalysis", jobId,
            "ComplianceAnalysisStarted", "compliance.analysis.started.v1",
            Map.of("jobId", jobId, "claimDurationMs", claimDurationMs), correlationId);

        AtomicReference<UUID> activeTaskId = new AtomicReference<>();
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "compliance-heartbeat-" + jobId);
                thread.setDaemon(true);
                return thread;
            });
        ScheduledFuture<?> heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                tenantDatabase.apply(organizationId);
                transactionService.heartbeat(
                    organizationId, jobId, activeTaskId.get(), workerId,
                    Instant.now().plus(ComplianceJobTransactionService.DEFAULT_LEASE));
            } catch (RuntimeException heartbeatFailure) {
                metrics.complianceHeartbeatFailure();
                log.warn("event=COMPLIANCE_HEARTBEAT_FAILED jobId={} taskId={} error={}",
                    jobId, activeTaskId.get(), heartbeatFailure.toString());
            }
        }, HEARTBEAT_INTERVAL.toSeconds(), HEARTBEAT_INTERVAL.toSeconds(), TimeUnit.SECONDS);

        Instant jobStarted = Instant.now();
        int processed = 0;
        int completed = 0;
        int reviews = 0;
        int failed = 0;
        try {
            if (transactionService.cancellationState(organizationId, jobId).cancelRequested()) {
                transactionService.cancelRemainingTasks(organizationId, jobId);
                metrics.complianceCancelRequest();
            } else {
                List<Task> pending = tasks(organizationId, jobId);
                for (Task task : pending) {
                    if (transactionService.cancellationState(organizationId, jobId)
                        .cancelRequested()) {
                        transactionService.cancelRemainingTasks(organizationId, jobId);
                        metrics.complianceCancelRequest();
                        break;
                    }
                    Instant taskLease = Instant.now()
                        .plus(ComplianceJobTransactionService.DEFAULT_LEASE);
                    var taskClaim = transactionService.claimTask(
                        organizationId, jobId, task.id(), workerId, taskLease);
                    if (!taskClaim.claimed()) {
                        continue;
                    }
                    activeTaskId.set(task.id());
                    Instant taskStarted = Instant.now();
                    metrics.complianceTaskRunning();
                    try {
                        if (transactionService.cancellationState(organizationId, jobId)
                            .cancelRequested()) {
                            transactionService.cancelRemainingTasks(organizationId, jobId);
                            metrics.complianceCancelRequest();
                            break;
                        }
                        TaskResult result = evaluate(organizationId, job, task, correlationId,
                            workerId);
                        if (transactionService.cancellationState(organizationId, jobId)
                            .cancelRequested()) {
                            // Cooperative cancel after model: do not treat as success progress
                            // if job was cancelled; remaining tasks are cancelled below.
                            transactionService.cancelRemainingTasks(organizationId, jobId);
                            metrics.complianceCancelRequest();
                            break;
                        }
                        completed++;
                        if (result.requiresReview()) {
                            reviews++;
                        }
                        log.info("event=COMPLIANCE_TASK_COMPLETED jobId={} taskId={} "
                                + "requirementId={} taskDurationMs={}",
                            jobId, task.id(), task.requirementId(),
                            Duration.between(taskStarted, Instant.now()).toMillis());
                    } catch (SemanticEvaluationException failure) {
                        if (failure.failureCode()
                            == SemanticEvaluationFailureCode.LLM_CANCELLED) {
                            transactionService.cancelRemainingTasks(organizationId, jobId);
                            metrics.complianceCancelRequest();
                            break;
                        }
                        failed++;
                        failTask(organizationId, task.id(), failure.failureCode().name(),
                            failure.getMessage());
                        log.warn(
                            "event=COMPLIANCE_TASK_FAILED complianceRunId={} requirementId={} "
                                + "failureCode={} retryAttempt={}",
                            jobId, task.requirementId(), failure.failureCode(),
                            failure.retryAttempt());
                    } catch (RuntimeException failure) {
                        failed++;
                        failTask(organizationId, task.id(), "EVALUATION_ERROR",
                            failure.getMessage());
                        log.warn("event=COMPLIANCE_TASK_FAILED jobId={} taskId={} errorCode={}",
                            jobId, task.id(), failure.getClass().getSimpleName());
                    } finally {
                        activeTaskId.set(null);
                        metrics.complianceTaskDuration(
                            Duration.between(taskStarted, Instant.now()));
                    }
                    processed++;
                    int progress = job.totalRequirementCount() == 0 ? 100
                        : (int) Math.round(processed * 100d / job.totalRequirementCount());
                    jdbc.update("""
                        update compliance_analysis_job
                        set processed_requirement_count = ?, completed_count = ?,
                            manual_review_count = ?, failed_count = ?,
                            updated_at = clock_timestamp(),
                            version = version + 1
                        where id = ? and organization_id = ?
                          and status = 'RUNNING'
                        """, processed, completed, reviews, failed, jobId, organizationId);
                    jobs.event(organizationId, jobId, "PROGRESS", progress,
                        "Compliance analysis progressed",
                        Map.of("processed", processed, "completed", completed,
                            "manualReview", reviews, "failed", failed));
                    outbox.publish(organizationId, "ComplianceAnalysis", jobId,
                        "ComplianceAnalysisProgress", "compliance.analysis.progress.v1",
                        Map.of("jobId", jobId, "progress", progress,
                            "processed", processed, "completed", completed,
                            "manualReview", reviews, "failed", failed), correlationId);
                }
            }

            log.info("event=COMPLIANCE_JOB_FINALIZATION_STARTED jobId={} workerId={}",
                jobId, workerId);
            JobFinalizationResult finalization =
                transactionService.finalizeJob(organizationId, jobId);
            String terminal = finalization.status();
            metrics.complianceJobDuration(Duration.between(jobStarted, Instant.now()));
            if ("PARTIALLY_COMPLETED".equals(terminal)) {
                metrics.complianceJobPartial();
            }
            jobs.event(organizationId, jobId, terminal, 100,
                "Compliance analysis finished",
                Map.of("completed", finalization.completed(),
                    "manualReview", reviews, "failed", finalization.failed(),
                    "claimDurationMs", claimDurationMs));
            outbox.publish(organizationId, "ComplianceAnalysis", jobId,
                "FAILED".equals(terminal)
                    ? "ComplianceAnalysisFailed" : "ComplianceAnalysisCompleted",
                "FAILED".equals(terminal)
                    ? "compliance.analysis.failed.v1" : "compliance.analysis.completed.v1",
                Map.of("jobId", jobId, "completed", finalization.completed(),
                    "manualReview", reviews, "failed", finalization.failed(),
                    "status", terminal, "claimDurationMs", claimDurationMs), correlationId);
            if (!"FAILED".equals(terminal) && !"CANCELLED".equals(terminal)
                && featureFlags.enabled(organizationId, job.projectId(),
                TenderIntelligenceFlags.TENDER_DOMAIN_V2)) {
                try {
                    Map<String, Object> summary = tenderSummaryService.rebuild(
                        organizationId, job.projectId());
                    audit.recordSystem(organizationId, "system", "TENDER_SUMMARY_REBUILT",
                        "TenderAssessmentSummary", job.projectId(), null,
                        Map.of("jobId", jobId, "status", summary.getOrDefault(
                            "overall_compliance_status", "REVIEW_REQUIRED")));
                } catch (RuntimeException summaryFailure) {
                    log.warn("tender_summary_rebuild_failed jobId={} projectId={} error={}",
                        jobId, job.projectId(), summaryFailure.toString());
                    metrics.summaryRebuildFailure();
                    jobs.event(organizationId, jobId, "SUMMARY_FAILED", 100,
                        "Tender summary rebuild failed",
                        Map.of("error", truncate(summaryFailure.getMessage())));
                }
            }
        } finally {
            heartbeatFuture.cancel(true);
            heartbeatExecutor.shutdownNow();
        }
    }

    private void failTask(UUID organizationId, UUID taskId, String errorCode, String message) {
        jdbc.update("""
            update requirement_matching_task
            set status = 'FAILED', error_code = ?, error_message = ?,
                last_error_code = ?, last_error_message = ?,
                completed_at = clock_timestamp(), updated_at = clock_timestamp(),
                version = version + 1
            where id = ? and organization_id = ?
              and status = 'RUNNING'
            """, errorCode, truncate(message), errorCode, truncate(message),
            taskId, organizationId);
        metrics.complianceTaskFailed(errorCode);
        if (errorCode != null && errorCode.contains("TIMEOUT")) {
            metrics.complianceTaskTimeout();
        }
    }

    private TaskResult evaluate(UUID organizationId, Job job, Task task,
                                UUID correlationId, String workerId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "Model evaluation must not run inside an active database transaction");
        }
        // Task is already claimed RUNNING by ComplianceJobTransactionService.claimTask.
        jdbc.update("""
            update requirement_matching_task
            set heartbeat_at = clock_timestamp(),
                updated_at = clock_timestamp(),
                version = version + 1
            where id = ? and organization_id = ? and claimed_by = ?
            """, task.id(), organizationId, workerId);
        PolicyVersion retrievalPolicy = retrievalPolicy(
            organizationId, job.retrievalPolicyVersionId());
        Snapshot snapshot = snapshot(organizationId, job.snapshotId());
        var retrievalTimer = metrics.retrievalStarted();
        List<CandidateEvidence> candidates;
        try {
            candidates = retriever.retrieve(
                organizationId, task.requirementId(), task.targetEntityId(),
                snapshot.entityCutoff(), snapshot.evidenceCutoff(), retrievalPolicy);
        } finally {
            metrics.retrievalCompleted(retrievalTimer);
        }
        metrics.retrievalCandidates(candidates.size());
        Double topScore = candidates.stream()
            .map(CandidateEvidence::lexicalScore)
            .max(Double::compareTo)
            .orElse(null);
        Map<String, Object> retrievalTrace = new LinkedHashMap<>();
        retrievalTrace.put("complianceRunId", job.id());
        retrievalTrace.put("requirementId", task.requirementId());
        retrievalTrace.put("organizationId", organizationId);
        retrievalTrace.put("projectId", job.projectId());
        retrievalTrace.put("targetEntityId", task.targetEntityId());
        retrievalTrace.put("vectorCollection", null);
        retrievalTrace.put("queryEmbeddingDimension", null);
        retrievalTrace.put("keywordCandidateCount", candidates.size());
        retrievalTrace.put("finalCandidateCount", candidates.size());
        retrievalTrace.put("topScore", topScore);
        retrievalTrace.put("documentScopeLocked", false);
        try {
            log.info("evidence_retrieval {}", mapper.writeValueAsString(retrievalTrace));
        } catch (JsonProcessingException ignored) {
            log.info("evidence_retrieval {}", retrievalTrace);
        }
        var rerankingTimer = metrics.rerankingStarted();
        RankedEvidenceResult ranked;
        try {
            ranked = reranker.rerank(
                new EvidenceRerankingContext(organizationId, task.requirementId(),
                    task.targetEntityId(), candidates, Map.of()), retrievalPolicy);
        } finally {
            metrics.rerankingCompleted(rerankingTimer);
        }
        // Persist retrieval counts before LLM comparison so timeouts do not hide
        // successful candidate discovery.
        jdbc.update("""
            update requirement_matching_task
            set candidate_count = ?, reranked_candidate_count = ?,
                updated_at = clock_timestamp(),
                version = version + 1
            where id = ? and organization_id = ?
            """, candidates.size(), ranked.ranked().size(), task.id(), organizationId);
        Requirement requirement = requirement(organizationId, task.requirementId());
        EvaluationOutcome outcome;
        if (ranked.ranked().isEmpty()) {
            // Still allow deterministic capability matching when no lexical evidence exists.
            EvaluationOutcome deterministic = tryDeterministic(organizationId, job, requirement,
                List.of());
            if (deterministic != null) {
                outcome = deterministic;
                metrics.complianceDeterministic();
                metrics.deterministicEvaluation();
            } else {
                outcome = missingOutcome(organizationId, job, requirement);
                metrics.complianceMissingEvidence();
            }
        } else {
            EvaluationOutcome deterministic = tryDeterministic(organizationId, job, requirement,
                selectedEvidencePayload(organizationId, ranked.ranked()));
            if (deterministic != null) {
                outcome = deterministic;
                metrics.complianceDeterministic();
                metrics.deterministicEvaluation();
            } else {
                String provider = comparisonProvider(organizationId, requirement.attributes());
                if (provider == null || "manual-only".equals(provider)) {
                    outcome = semanticOutcome(
                        organizationId, job, requirement, ranked, correlationId);
                    metrics.complianceLlm();
                } else {
                    outcome = deterministicOutcome(
                        organizationId, job, requirement, ranked, provider);
                    metrics.complianceDeterministic();
                    metrics.comparisonStrategy(provider);
                }
            }
        }
        UUID evaluationId = persistEvaluation(organizationId, job, task, requirement,
            ranked, outcome);
        try {
            postAssessmentHooks.afterAssessment(organizationId, job.projectId(),
                requirement.id(), evaluationId, outcome.decisionCode(),
                outcome.gapHint(), outcome.missingElements(),
                outcome.ambiguousRequirement(), "system");
        } catch (RuntimeException hookFailure) {
            log.warn("post_assessment_hook_failed evaluationId={} error={}",
                evaluationId, hookFailure.toString());
        }
        if (outcome.evaluationSource() != null) {
            jdbc.update("""
                update compliance_evaluation
                   set evaluation_source = ?,
                       missing_requirement_elements_json = ?::jsonb,
                       explicit_contradiction = ?,
                       reasoning_summary = ?,
                       assessment_status = 'COMPLETED',
                       updated_at = clock_timestamp()
                 where id = ? and organization_id = ?
                """, outcome.evaluationSource(),
                json(outcome.missingElements() == null ? List.of() : outcome.missingElements()),
                outcome.contradiction(),
                outcome.summary().path("reasonCode").asText(null),
                evaluationId, organizationId);
        }
        if (transactionService.cancellationState(organizationId, job.id()).cancelRequested()) {
            jdbc.update("""
                update requirement_matching_task
                set status = 'CANCELLED',
                    candidate_count = ?,
                    reranked_candidate_count = ?,
                    completed_at = clock_timestamp(),
                    updated_at = clock_timestamp(),
                    version = version + 1
                where id = ? and organization_id = ?
                  and status = 'RUNNING'
                """, candidates.size(), ranked.ranked().size(), task.id(), organizationId);
            log.info("event=COMPLIANCE_TASK_CANCELLED jobId={} taskId={} "
                    + "(late cancel after model, result not completed)",
                job.id(), task.id());
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_CANCELLED,
                "Task cancelled after model response", 0);
        }
        jdbc.update("""
            update requirement_matching_task
            set status = 'COMPLETED', candidate_count = ?,
                reranked_candidate_count = ?, selected_evidence_count = ?,
                evaluation_count = 1, model_run_id = ?,
                completed_at = clock_timestamp(),
                updated_at = clock_timestamp(), version = version + 1
            where id = ? and organization_id = ?
              and status = 'RUNNING'
            """, candidates.size(), ranked.ranked().size(), outcome.selectedEvidence().size(),
            outcome.modelRunId(), task.id(), organizationId);
        if (outcome.requiresReview()) {
            metrics.complianceManualReview();
            outbox.publish(organizationId, "ComplianceEvaluation", evaluationId,
                "ComplianceReviewRequired", "compliance.review.required.v1",
                Map.of("evaluationId", evaluationId,
                    "requirementId", task.requirementId()), correlationId);
        }
        if (outcome.contradiction()) {
            metrics.complianceContradictoryEvidence();
        }
        metrics.complianceEvaluation();
        return new TaskResult(outcome.requiresReview());
    }

    private EvaluationOutcome tryDeterministic(UUID organizationId, Job job,
                                               Requirement requirement,
                                               List<Map<String, Object>> evidencePayload) {
        var decision = deterministicEvaluator.evaluate(organizationId, job.projectId(),
            requirement.id(), requirement.text(), requirement.evaluationMethod(),
            evidencePayload);
        if (decision == null) {
            return null;
        }
        UUID decisionId = decisionByCode(organizationId, decision.decisionCode());
        if (decisionId == null) {
            return null;
        }
        ObjectNode summary = decision.summary();
        summary.put("decisionCode", decision.decisionCode());
        audit.recordSystem(organizationId, "system", "DETERMINISTIC_ASSESSMENT_COMPLETED",
            "Requirement", requirement.id(), null,
            Map.of("projectId", job.projectId(), "decision", decision.decisionCode(),
                "evaluationSource", "DETERMINISTIC"));
        String gapHint = null;
        if ("NON_COMPLIANT".equals(decision.decisionCode())
            && decision.missingElements() != null
            && decision.missingElements().stream().anyMatch(item ->
            item.toUpperCase().contains("NUMERIC"))) {
            gapHint = "NUMERIC_SHORTFALL";
        } else if ("NON_COMPLIANT".equals(decision.decisionCode())) {
            gapHint = "MISSING_CAPABILITY";
        } else if ("INSUFFICIENT_INFORMATION".equals(decision.decisionCode())) {
            gapHint = "INSUFFICIENT_EVIDENCE";
        }
        return new EvaluationOutcome(decisionId, decision.decisionCode(), summary, 0.95,
            decision.requiresReview(), null, List.of(), decision.contradiction(), null,
            "DETERMINISTIC", decision.missingElements(), false, gapHint);
    }

    private EvaluationOutcome deterministicOutcome(
        UUID organizationId, Job job, Requirement requirement,
        RankedEvidenceResult ranked, String provider
    ) {
        RankedEvidence best = ranked.ranked().getFirst();
        CandidateEvidence evidence = best.evidence();
        JsonNode attributes = requirement.attributes();
        ComparisonContext context = new ComparisonContext(
            provider,
            attributes.path("operator").asText("GREATER_THAN_OR_EQUAL"),
            decimal(attributes, "requiredValue"),
            decimal(attributes, "requiredValueEnd"),
            uuid(attributes, "requiredUnitConceptId"),
            evidence.numericValue(),
            evidence.numericValueEnd(),
            evidence.unitConceptId(),
            booleanValue(attributes, "requiredBoolean"),
            evidence.booleanValue(),
            instant(attributes, "requiredDate"),
            evidence.metadata().get("validUntil") instanceof Instant value ? value : null,
            attributes.path("conditionExpression"),
            Map.of("organizationId", organizationId)
        );
        ComparisonResult comparison = comparisons.compare(context);
        String outcome = switch (comparison.status()) {
            case "SATISFIED" -> "SATISFIED";
            case "NOT_SATISFIED" -> "NOT_SATISFIED";
            default -> "INDETERMINATE";
        };
        UUID decision = decisionByOutcome(organizationId, outcome);
        JsonNode confidencePolicy = policyConfiguration(
            organizationId, job.confidencePolicyVersionId());
        boolean contradiction = "NOT_SATISFIED".equals(comparison.status());
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of(
                "relevance", best.score(),
                "validity", evidence.validityScore(),
                "authority", evidence.authorityScore(),
                "grounding", 1d,
                "deterministic", 1d,
                "entityResolution", evidence.entityId() == null ? 0.5 : 1d,
                "freshness", best.factors().getOrDefault("freshness", 0.5),
                "historicalAcceptance", evidence.historicalScore()
            ), false, contradiction, confidencePolicy));
        ObjectNode summary = mapper.createObjectNode();
        summary.set("comparison", comparison.explanation());
        summary.put("strategyProvider", provider);
        summary.put("status", comparison.status());
        summary.set("confidence", mapper.valueToTree(confidence));
        String decisionCode = switch (outcome) {
            case "SATISFIED" -> "COMPLIANT";
            case "NOT_SATISFIED" -> "NON_COMPLIANT";
            default -> "INSUFFICIENT_INFORMATION";
        };
        return new EvaluationOutcome(decision, decisionCode, summary, confidence.score(),
            confidence.requiresReview(), null, List.of(best), contradiction, null,
            "DETERMINISTIC", List.of(), false,
            "NOT_SATISFIED".equals(comparison.status()) ? "NUMERIC_SHORTFALL" : null);
    }

    private EvaluationOutcome semanticOutcome(
        UUID organizationId, Job job, Requirement requirement,
        RankedEvidenceResult ranked, UUID correlationId
    ) {
        Prompt prompt = prompt(organizationId, job.promptPackageVersionId());
        List<Map<String, Object>> evidence = selectedEvidencePayload(
            organizationId, ranked.ranked());
        List<Decision> decisions = decisions(organizationId);
        boolean contradiction = evidence.stream().anyMatch(item ->
            number(item.get("contradictionStrength")) > 0);
        SemanticRequest request = new SemanticRequest(
            organizationId, "nanobase-spec-ai", modelProfile(organizationId),
            prompt.components(), prompt.schema(),
            Map.of("id", requirement.id(), "text", requirement.text(),
                "attributes", requirement.attributes(),
                "evaluationVersion", "v1"),
            ontology(organizationId, job.analysisProfileId()), evidence,
            decisions.stream().map(Decision::code).toList(), maxOutputTokens, correlationId);
        ComplianceSemanticRouter.RoutedEvaluation routed =
            semanticRouter.evaluate(request, contradiction);
        var response = routed.response();
        ObjectNode safeOutput = decisionSafetyGuard.normalize(
            response.output(), requirement.text(), evidence);
        String rawDecision = safeOutput.path("recommendedDecisionConcept").asText();
        if (rawDecision == null || rawDecision.isBlank()) {
            rawDecision = safeOutput.path("decision").asText();
        }
        if (rawDecision == null || rawDecision.isBlank()) {
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Semantic evaluator returned an empty decision concept", 0);
        }
        final String decisionCode = rawDecision;
        UUID decisionId = decisions.stream()
            .filter(item -> item.code().equals(decisionCode))
            .map(Decision::id).findFirst()
            .orElseThrow(() -> new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE,
                "Semantic evaluator returned an unsupported decision concept: " + decisionCode,
                0));
        Set<String> allowed = evidence.stream()
            .map(item -> String.valueOf(item.get("id")))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allowedDecisions = decisions.stream()
            .map(Decision::code)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        StructuredComplianceResponseValidator.ValidationResult validation =
            new StructuredComplianceResponseValidator()
                .validate(safeOutput, allowed, allowedDecisions);
        if (!validation.valid()) {
            throw new SemanticEvaluationException(
                validation.failureCode() == null
                    ? SemanticEvaluationFailureCode.LLM_INVALID_RESPONSE
                    : validation.failureCode(),
                validation.message(), 0);
        }
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of("relevance", averageScore(ranked.ranked()),
                "validity", averageValidity(ranked.ranked()),
                "authority", averageAuthority(ranked.ranked()),
                "grounding", 1d, "deterministic", 0d,
                "entityResolution", 0.8, "freshness", 0.7,
                "historicalAcceptance", 0.5),
            false, contradiction,
            policyConfiguration(organizationId, job.confidencePolicyVersionId())));
        double modelConfidence = safeOutput.path("confidence").asDouble(0);
        double combined = (confidence.score() + modelConfidence) / 2d;
        boolean review = safeOutput.path("requiresManualReview").asBoolean()
            || confidence.requiresReview() || contradiction
            || "NON_COMPLIANT".equals(decisionCode)
            || "INSUFFICIENT_INFORMATION".equals(decisionCode);
        ObjectNode summary = mapper.createObjectNode();
        summary.put("strategyProvider", "llm-semantic-evaluation");
        summary.put("resultLabel", "AI Ön Değerlendirmesi");
        summary.set("semanticEvaluation", safeOutput);
        summary.set("confidence", mapper.valueToTree(confidence));
        summary.set("modelRouting", mapper.valueToTree(routed.routing()));
        return new EvaluationOutcome(decisionId, decisionCode, summary, combined, review,
            response.modelRunId(), ranked.ranked(), contradiction, routed.routing(),
            "LLM", List.of(), false, null);
    }

    private EvaluationOutcome missingOutcome(UUID organizationId, Job job,
                                             Requirement requirement) {
        UUID decision = decisionByOutcome(organizationId, "INDETERMINATE");
        ConfidenceResult confidence = confidenceEngine.evaluate(new ConfidenceContext(
            Map.of(), true, false,
            policyConfiguration(organizationId, job.confidencePolicyVersionId())));
        ObjectNode summary = mapper.createObjectNode();
        summary.put("status", "MISSING_EVIDENCE");
        summary.put("requirementId", requirement.id().toString());
        summary.set("confidence", mapper.valueToTree(confidence));
        return new EvaluationOutcome(decision, "INSUFFICIENT_INFORMATION", summary,
            confidence.score(), true, null, List.of(), false, null, "HYBRID",
            List.of("MISSING_EVIDENCE"), false, "INSUFFICIENT_EVIDENCE");
    }

    private UUID persistEvaluation(
        UUID organizationId, Job job, Task task, Requirement requirement,
        RankedEvidenceResult ranked, EvaluationOutcome outcome
    ) {
        UUID evaluationId = UUID.randomUUID();
        UUID grounding = conceptByMetadata(organizationId, "grounded",
            !outcome.selectedEvidence().isEmpty());
        ComplianceSemanticRouter.RoutingTrace routing = outcome.routing();
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
                      now(), now())
            """, evaluationId, organizationId, job.projectId(), requirement.id(),
            task.targetEntityId() == null ? "{}"
                : json(Map.of("targetEntityId", task.targetEntityId())),
            job.id(), job.snapshotId(), outcome.decisionConceptId(),
            outcome.summary().toString(),
            outcome.confidence(), grounding,
            outcome.requiresReview() ? "REQUIRES_REVIEW" : "AI_RECOMMENDATION",
            job.analysisProfileId(), job.retrievalPolicyVersionId(),
            job.matchingPolicyVersionId(), job.comparisonPolicyVersionId(),
            job.confidencePolicyVersionId(), job.promptPackageVersionId(),
            routing == null ? null : routing.liveProfile(),
            routing == null ? null : routing.shadowProfile(),
            routing == null || routing.shadowResult() == null
                ? null : routing.shadowResult().toString(),
            routing == null || routing.comparison() == null
                ? null : routing.comparison().toString(),
            routing == null ? null : routing.escalationReason());
        for (RankedEvidence selected : outcome.selectedEvidence()) {
            boolean contradiction = outcome.contradiction();
            UUID role = conceptByMetadata(organizationId, "polarity",
                contradiction ? -1 : 1);
            jdbc.update("""
                insert into compliance_evidence_link (
                    id, organization_id, compliance_evaluation_id,
                    evidence_fragment_id, evidence_claim_id, relation_role_concept_id,
                    relevance_score, validity_score, support_strength,
                    contradiction_strength, selected_by_type, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, UUID.randomUUID(), organizationId, evaluationId,
                selected.evidence().fragmentId(), selected.evidence().claimId(), role,
                selected.score(), selected.evidence().validityScore(),
                contradiction ? 0 : selected.score(),
                contradiction ? selected.score() : 0,
                outcome.modelRunId() == null ? "DETERMINISTIC" : "MODEL");
        }
        return evaluationId;
    }

    private List<Map<String, Object>> selectedEvidencePayload(
        UUID organizationId, List<RankedEvidence> ranked
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (RankedEvidence item : ranked) {
            Map<String, Object> row = jdbc.queryForMap("""
                select id, fragment_text as text, page_number,
                       valid_from, valid_until
                from evidence_fragment where id = ? and organization_id = ?
                """, item.evidence().fragmentId(), organizationId);
            Map<String, Object> value = new LinkedHashMap<>(row);
            value.put("relevanceScore", item.score());
            value.put("validityScore", item.evidence().validityScore());
            value.put("authorityScore", item.evidence().authorityScore());
            value.put("contradictionStrength", 0);
            payload.add(value);
        }
        return List.copyOf(payload);
    }

    private Job job(UUID organizationId, UUID id) {
        return jdbc.query("""
            select job.*, snapshot.entity_version_cutoff,
                   snapshot.evidence_version_cutoff
            from compliance_analysis_job job
            join knowledge_snapshot snapshot on snapshot.id = job.knowledge_snapshot_id
             and snapshot.organization_id = job.organization_id
            where job.id = ? and job.organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Compliance analysis job not found");
                }
                return new Job(result.getObject("id", UUID.class),
                    result.getObject("project_id", UUID.class), result.getString("status"),
                    result.getObject("analysis_profile_id", UUID.class),
                    result.getObject("knowledge_snapshot_id", UUID.class),
                    result.getObject("retrieval_policy_version_id", UUID.class),
                    result.getObject("matching_policy_version_id", UUID.class),
                    result.getObject("comparison_policy_version_id", UUID.class),
                    result.getObject("confidence_policy_version_id", UUID.class),
                    result.getObject("prompt_package_version_id", UUID.class),
                    result.getInt("total_requirement_count"));
            }, id, organizationId);
    }

    private List<Task> tasks(UUID organizationId, UUID jobId) {
        return jdbc.query("""
            select task.id, task.requirement_id,
                   (requirement.attributes_json ->> 'targetEntityId')::uuid target_entity_id
            from requirement_matching_task task
            join requirement on requirement.id = task.requirement_id
             and requirement.organization_id = task.organization_id
            where task.organization_id = ? and task.compliance_job_id = ?
              and task.status = 'QUEUED'
            order by task.created_at, task.id
            """, (result, row) -> new Task(result.getObject(1, UUID.class),
                result.getObject(2, UUID.class), result.getObject(3, UUID.class)),
            organizationId, jobId);
    }

    private Snapshot snapshot(UUID organizationId, UUID id) {
        return jdbc.query("""
            select entity_version_cutoff, evidence_version_cutoff
            from knowledge_snapshot where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Knowledge snapshot is missing");
                }
                return new Snapshot(result.getTimestamp(1).toInstant(),
                    result.getTimestamp(2).toInstant());
            }, id, organizationId);
    }

    private Requirement requirement(UUID organizationId, UUID id) {
        return jdbc.query("""
            select id, requirement_code, requirement_text, attributes_json::text,
                   evaluation_method
            from requirement where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Requirement not found");
                }
                return new Requirement(result.getObject(1, UUID.class),
                    result.getString(2), result.getString(3), tree(result.getString(4)),
                    result.getString(5));
            }, id, organizationId);
    }

    private PolicyVersion retrievalPolicy(UUID organizationId, UUID id) {
        return jdbc.query("""
            select definition.policy_code, version.configuration_json::text
            from retrieval_policy_version version
            join retrieval_policy_definition definition
              on definition.id = version.policy_definition_id
            where version.id = ?
              and (version.organization_id = ? or version.organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Retrieval policy snapshot is missing");
                }
                return new PolicyVersion(id, result.getString(1), tree(result.getString(2)));
            }, id, organizationId);
    }

    private JsonNode policyConfiguration(UUID organizationId, UUID id) {
        return jdbc.query("""
            select configuration_json::text from policy_version
            where id = ? and (organization_id = ? or organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Policy snapshot is missing");
                }
                return tree(result.getString(1));
            }, id, organizationId);
    }

    private String comparisonProvider(UUID organizationId, JsonNode attributes) {
        String strategyCode = attributes.path("comparisonStrategy").asText(null);
        if (strategyCode == null) {
            return null;
        }
        List<String> providers = jdbc.query("""
            select provider_code from comparison_strategy_definition
            where strategy_code = ? and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc limit 1
            """, (result, row) -> result.getString(1), strategyCode, organizationId);
        return providers.isEmpty() ? null : providers.getFirst();
    }

    private UUID decisionByOutcome(UUID organizationId, String outcome) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where concept_type = 'DECISION' and active = true
              and metadata_json ->> 'outcome' = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class), outcome, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Decision concept missing for outcome " + outcome);
        }
        return ids.getFirst();
    }

    private UUID decisionByCode(UUID organizationId, String code) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where concept_type = 'DECISION' and active = true
              and concept_code = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class), code, organizationId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private UUID conceptByMetadata(UUID organizationId, String key, Object value) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where active = true and metadata_json ->> ? = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            key, String.valueOf(value), organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Required ontology metadata is not configured");
        }
        return ids.getFirst();
    }

    private List<Decision> decisions(UUID organizationId) {
        return jdbc.query("""
            select id, concept_code from ontology_concept
            where concept_type = 'DECISION' and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order
            """, (result, row) -> new Decision(result.getObject(1, UUID.class),
                result.getString(2)), organizationId);
    }

    private Prompt prompt(UUID organizationId, UUID versionId) {
        return jdbc.query("""
            select version.component_configuration_json::text,
                   schema.json_schema::text
            from prompt_package_version version
            join output_schema_version schema on schema.id = version.output_schema_id
            where version.id = ?
              and (version.organization_id = ? or version.organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Compliance prompt snapshot is missing");
                }
                JsonNode config = tree(result.getString(1));
                List<String> components = new ArrayList<>();
                for (JsonNode code : config.path("components")) {
                    components.add(jdbc.queryForObject("""
                        select content_template from prompt_component
                        where component_code = ?
                          and (organization_id = ? or organization_id is null)
                        order by (organization_id is not null) desc limit 1
                        """, String.class, code.asText(), organizationId));
                }
                return new Prompt(List.copyOf(components), tree(result.getString(2)));
            }, versionId, organizationId);
    }

    private List<Map<String, Object>> ontology(UUID organizationId,
                                               UUID analysisProfileId) {
        return jdbc.queryForList("""
            select concept.concept_code as code, concept.name,
                   concept.concept_type as type, concept.metadata_json::text as metadata
            from analysis_profile profile
            join ontology_concept concept
              on concept.ontology_version_id = profile.ontology_version_id
            where profile.id = ? and profile.organization_id = ?
              and concept.active = true
              and (concept.organization_id = ? or concept.organization_id is null)
            order by concept.sort_order, concept.concept_code
            """, analysisProfileId, organizationId, organizationId);
    }

    private String modelProfile(UUID organizationId) {
        List<String> profiles = jdbc.query("""
            select profile_code from model_profile
            where active = true and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc,
                     case profile_code when 'BALANCED' then 0 when 'FAST' then 1 else 2 end,
                     profile_code
            """, (result, index) -> result.getString("profile_code"), organizationId);
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No active model profile configured");
        }
        return profiles.getFirst();
    }

    private double averageScore(List<RankedEvidence> values) {
        return values.stream().mapToDouble(RankedEvidence::score).average().orElse(0);
    }

    private double averageValidity(List<RankedEvidence> values) {
        return values.stream().mapToDouble(value -> value.evidence().validityScore())
            .average().orElse(0);
    }

    private double averageAuthority(List<RankedEvidence> values) {
        return values.stream().mapToDouble(value -> value.evidence().authorityScore())
            .average().orElse(0);
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
    }

    private Boolean booleanValue(JsonNode node, String field) {
        return node.path(field).isBoolean() ? node.path(field).booleanValue() : null;
    }

    private UUID uuid(JsonNode node, String field) {
        return node.path(field).isTextual()
            ? UUID.fromString(node.path(field).asText()) : null;
    }

    private Instant instant(JsonNode node, String field) {
        return node.path(field).isTextual()
            ? Instant.parse(node.path(field).asText()) : null;
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored compliance JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Compliance value is not serializable", exception);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "Compliance task failed";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record Job(
        UUID id,
        UUID projectId,
        String status,
        UUID analysisProfileId,
        UUID snapshotId,
        UUID retrievalPolicyVersionId,
        UUID matchingPolicyVersionId,
        UUID comparisonPolicyVersionId,
        UUID confidencePolicyVersionId,
        UUID promptPackageVersionId,
        int totalRequirementCount
    ) {
        static Job fromClaim(JobClaimResult claim) {
            return new Job(
                claim.jobId(),
                claim.projectId(),
                claim.status(),
                claim.analysisProfileId(),
                claim.knowledgeSnapshotId(),
                claim.retrievalPolicyVersionId(),
                claim.matchingPolicyVersionId(),
                claim.comparisonPolicyVersionId(),
                claim.confidencePolicyVersionId(),
                claim.promptPackageVersionId(),
                claim.totalRequirementCount()
            );
        }
    }

    private record Task(UUID id, UUID requirementId, UUID targetEntityId) {
    }

    private record Snapshot(Instant entityCutoff, Instant evidenceCutoff) {
    }

    private record Requirement(UUID id, String code, String text, JsonNode attributes,
                               String evaluationMethod) {
    }

    private record Decision(UUID id, String code) {
    }

    private record Prompt(List<String> components, JsonNode schema) {
    }

    private record EvaluationOutcome(
        UUID decisionConceptId,
        String decisionCode,
        JsonNode summary,
        double confidence,
        boolean requiresReview,
        UUID modelRunId,
        List<RankedEvidence> selectedEvidence,
        boolean contradiction,
        ComplianceSemanticRouter.RoutingTrace routing,
        String evaluationSource,
        List<String> missingElements,
        boolean ambiguousRequirement,
        String gapHint
    ) {
    }

    private record TaskResult(boolean requiresReview) {
    }
}
