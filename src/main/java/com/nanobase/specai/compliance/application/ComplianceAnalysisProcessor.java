package com.nanobase.specai.compliance.application;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.decision.application.TenderSummaryService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.operations.application.FeatureFlagService;
import com.nanobase.specai.operations.application.TenderIntelligenceFlags;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.JobClaimResult;
import com.nanobase.specai.compliance.application.ComplianceJobTransactionService.JobFinalizationResult;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates compliance analysis without owning a long-lived database transaction.
 * Prepare / execute / persist boundaries live in dedicated services.
 */
@Service
public class ComplianceAnalysisProcessor {
    private static final Logger log = LoggerFactory.getLogger(ComplianceAnalysisProcessor.class);
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(20);

    private final TenantDatabaseContext tenantDatabase;
    private final JdbcTemplate jdbc;
    private final ComplianceJobService jobs;
    private final ComplianceJobTransactionService transactionService;
    private final ComplianceTaskPreparationService preparationService;
    private final ComplianceTaskModelExecutionService modelExecutionService;
    private final ComplianceTaskPersistenceService persistenceService;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;
    private final FeatureFlagService featureFlags;
    private final TenderSummaryService tenderSummaryService;
    private final AuditService audit;
    private final ComplianceFaultInjection faultInjection;
    private final TransactionTemplate tenantTx;
    private final int maxOutputTokens;

    public ComplianceAnalysisProcessor(
        TenantDatabaseContext tenantDatabase,
        JdbcTemplate jdbc,
        ComplianceJobService jobs,
        ComplianceJobTransactionService transactionService,
        ComplianceTaskPreparationService preparationService,
        ComplianceTaskModelExecutionService modelExecutionService,
        ComplianceTaskPersistenceService persistenceService,
        OutboxService outbox,
        PlatformMetrics metrics,
        FeatureFlagService featureFlags,
        TenderSummaryService tenderSummaryService,
        AuditService audit,
        ComplianceFaultInjection faultInjection,
        PlatformTransactionManager transactionManager,
        @org.springframework.beans.factory.annotation.Value(
            "${specai.ai-orchestrator.compliance-max-output-tokens:1024}") int maxOutputTokens,
        @org.springframework.beans.factory.annotation.Value(
            "${specai.compliance.evaluation-parallelism:1}") int evaluationParallelism
    ) {
        this.tenantDatabase = tenantDatabase;
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.transactionService = transactionService;
        this.preparationService = preparationService;
        this.modelExecutionService = modelExecutionService;
        this.persistenceService = persistenceService;
        this.outbox = outbox;
        this.metrics = metrics;
        this.featureFlags = featureFlags;
        this.tenderSummaryService = tenderSummaryService;
        this.audit = audit;
        this.faultInjection = faultInjection;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenantTx = template;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
        int requestedParallelism = evaluationParallelism <= 0 ? 1 : evaluationParallelism;
        if (requestedParallelism != 1) {
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
        Instant claimStarted = Instant.now();
        String workerId = ComplianceJobTransactionService.normalizeWorkerId(
            "worker-" + jobId + "-" + UUID.randomUUID());
        Instant leaseExpiresAt = Instant.now()
            .plus(transactionService.leaseDuration());
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
        inTenant(organizationId, () -> {
            jobs.event(organizationId, jobId, "STARTED", 0,
                "Compliance analysis started",
                Map.of("requirementCount", job.totalRequirementCount(),
                    "workerId", workerId, "claimDurationMs", claimDurationMs));
            outbox.publish(organizationId, "ComplianceAnalysis", jobId,
                "ComplianceAnalysisStarted", "compliance.analysis.started.v1",
                Map.of("jobId", jobId, "claimDurationMs", claimDurationMs), correlationId);
        });

        AtomicReference<UUID> activeTaskId = new AtomicReference<>();
        AtomicReference<Long> activeLeaseGeneration = new AtomicReference<>(0L);
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "compliance-heartbeat-" + jobId);
                thread.setDaemon(true);
                return thread;
            });
        ScheduledFuture<?> heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (faultInjection.shouldSuppressHeartbeat(jobId)) {
                    log.info("event=COMPLIANCE_HEARTBEAT_SUPPRESSED_FOR_FAULT_PAUSE jobId={}",
                        jobId);
                    return;
                }
                var outcome = transactionService.heartbeat(
                    organizationId, jobId, activeTaskId.get(), workerId,
                    activeLeaseGeneration.get() == null ? 0L : activeLeaseGeneration.get(),
                    Instant.now().plus(transactionService.leaseDuration()));
                if (outcome == ComplianceJobTransactionService.HeartbeatOutcome.LEASE_LOST) {
                    metrics.complianceStaleWorkerResult();
                    log.warn("event=COMPLIANCE_HEARTBEAT_LEASE_LOST jobId={} taskId={}",
                        jobId, activeTaskId.get());
                }
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
                List<Task> pending = inTenant(organizationId,
                    () -> tasks(organizationId, jobId));
                for (Task task : pending) {
                    if (transactionService.cancellationState(organizationId, jobId)
                        .cancelRequested()) {
                        transactionService.cancelRemainingTasks(organizationId, jobId);
                        metrics.complianceCancelRequest();
                        break;
                    }
                    Instant taskLease = Instant.now()
                        .plus(transactionService.leaseDuration());
                    var taskClaim = transactionService.claimTask(
                        organizationId, jobId, task.id(), workerId, taskLease);
                    if (!taskClaim.claimed()) {
                        continue;
                    }
                    activeTaskId.set(task.id());
                    activeLeaseGeneration.set(taskClaim.leaseGeneration());
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
                            workerId, taskClaim.leaseGeneration());
                        if (transactionService.cancellationState(organizationId, jobId)
                            .cancelRequested()) {
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
                        failTask(organizationId, task.id(),
                            domainErrorCode(failure.failureCode()), failure.getMessage());
                        log.warn(
                            "event=COMPLIANCE_TASK_FAILED complianceRunId={} requirementId={} "
                                + "failureCode={} domainErrorCode={} retryAttempt={}",
                            jobId, task.requirementId(), failure.failureCode(),
                            domainErrorCode(failure.failureCode()), failure.retryAttempt());
                    } catch (RuntimeException failure) {
                        failed++;
                        failTask(organizationId, task.id(), "EVALUATION_ERROR",
                            failure.getMessage());
                        log.warn("event=COMPLIANCE_TASK_FAILED jobId={} taskId={} errorCode={}",
                            jobId, task.id(), failure.getClass().getSimpleName());
                    } finally {
                        activeTaskId.set(null);
                        activeLeaseGeneration.set(0L);
                        metrics.complianceTaskDuration(
                            Duration.between(taskStarted, Instant.now()));
                    }
                    processed++;
                    int progress = job.totalRequirementCount() == 0 ? 100
                        : (int) Math.round(processed * 100d / job.totalRequirementCount());
                    final int processedSnapshot = processed;
                    final int completedSnapshot = completed;
                    final int reviewsSnapshot = reviews;
                    final int failedSnapshot = failed;
                    inTenant(organizationId, () -> {
                        jdbc.update("""
                            update compliance_analysis_job
                            set processed_requirement_count = ?, completed_count = ?,
                                manual_review_count = ?, failed_count = ?,
                                updated_at = clock_timestamp(),
                                version = version + 1
                            where id = ? and organization_id = ?
                              and status = 'RUNNING'
                            """, processedSnapshot, completedSnapshot, reviewsSnapshot,
                            failedSnapshot, jobId, organizationId);
                        jobs.event(organizationId, jobId, "PROGRESS", progress,
                            "Compliance analysis progressed",
                            Map.of("processed", processedSnapshot, "completed", completedSnapshot,
                                "manualReview", reviewsSnapshot, "failed", failedSnapshot));
                        outbox.publish(organizationId, "ComplianceAnalysis", jobId,
                            "ComplianceAnalysisProgress", "compliance.analysis.progress.v1",
                            Map.of("jobId", jobId, "progress", progress,
                                "processed", processedSnapshot, "completed", completedSnapshot,
                                "manualReview", reviewsSnapshot, "failed", failedSnapshot),
                            correlationId);
                    });
                }
            }

            log.info("event=COMPLIANCE_JOB_FINALIZATION_STARTED jobId={} workerId={}",
                jobId, workerId);
            JobFinalizationResult finalization =
                transactionService.finalizeJob(organizationId, jobId);
            String terminal = finalization.status();
            if ("AGGREGATION_DEFERRED".equals(terminal)) {
                metrics.complianceAggregationDeferred();
                log.info("event=AGGREGATION_DEFERRED jobId={} workerId={} "
                        + "job remains RUNNING until active tasks complete",
                    jobId, workerId);
                return;
            }
            metrics.complianceJobDuration(Duration.between(jobStarted, Instant.now()));
            if ("PARTIALLY_COMPLETED".equals(terminal)) {
                metrics.complianceJobPartial();
            }
            final int reviewsSnapshot = reviews;
            inTenant(organizationId, () -> {
                jobs.event(organizationId, jobId, terminal, 100,
                    "Compliance analysis finished",
                    Map.of("completed", finalization.completed(),
                        "manualReview", reviewsSnapshot, "failed", finalization.failed(),
                        "claimDurationMs", claimDurationMs));
                outbox.publish(organizationId, "ComplianceAnalysis", jobId,
                    "FAILED".equals(terminal)
                        ? "ComplianceAnalysisFailed" : "ComplianceAnalysisCompleted",
                    "FAILED".equals(terminal)
                        ? "compliance.analysis.failed.v1" : "compliance.analysis.completed.v1",
                    Map.of("jobId", jobId, "completed", finalization.completed(),
                        "manualReview", reviewsSnapshot, "failed", finalization.failed(),
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
            });
        } finally {
            heartbeatFuture.cancel(true);
            heartbeatExecutor.shutdownNow();
        }
    }

    private void failTask(UUID organizationId, UUID taskId, String errorCode, String message) {
        inTenant(organizationId, () -> jdbc.update("""
            update requirement_matching_task
            set status = 'FAILED', error_code = ?, error_message = ?,
                last_error_code = ?, last_error_message = ?,
                completed_at = clock_timestamp(), updated_at = clock_timestamp(),
                version = version + 1
            where id = ? and organization_id = ?
              and status in ('RUNNING', 'READY_FOR_MODEL')
            """, errorCode, truncate(message), errorCode, truncate(message),
            taskId, organizationId));
        metrics.complianceTaskFailed(errorCode);
        if (errorCode != null && errorCode.contains("TIMEOUT")) {
            metrics.complianceTaskTimeout();
        }
    }

    private <T> T inTenant(UUID organizationId, java.util.function.Supplier<T> work) {
        return tenantTx.execute(status -> {
            tenantDatabase.apply(organizationId);
            return work.get();
        });
    }

    private void inTenant(UUID organizationId, Runnable work) {
        tenantTx.executeWithoutResult(status -> {
            tenantDatabase.apply(organizationId);
            work.run();
        });
    }

    private TaskResult evaluate(UUID organizationId, Job job, Task task,
                                UUID correlationId, String workerId, long leaseGeneration) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "Task evaluate orchestration must not run inside a transaction");
        }
        PreparedComplianceTask prepared = preparationService.prepare(
            organizationId,
            job.id(), job.projectId(),
            job.analysisProfileId(), job.snapshotId(),
            job.retrievalPolicyVersionId(), job.matchingPolicyVersionId(),
            job.comparisonPolicyVersionId(), job.confidencePolicyVersionId(),
            job.promptPackageVersionId(),
            task.id(), task.requirementId(), task.targetEntityId(),
            workerId, leaseGeneration, correlationId, maxOutputTokens);
        faultInjection.maybePause(
            ComplianceFaultInjection.PAUSE_AFTER_PREPARE,
            correlationId, job.id(), task.id());
        // Prepare TX is committed; DB connection returned to pool before execute.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            metrics.complianceConnectionHeldDuringModel();
            throw new IllegalStateException(
                "Active transaction remains after prepare commit");
        }
        if (transactionService.cancellationState(organizationId, job.id()).cancelRequested()) {
            transactionService.cancelRemainingTasks(organizationId, job.id());
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_CANCELLED, "Cancel requested", 0);
        }
        ComplianceExecutionResult execution;
        if (prepared.needsModelExecution()) {
            Instant lease = Instant.now().plus(transactionService.leaseDuration());
            if (!transactionService.markTaskRunningForModel(
                organizationId, task.id(), workerId, leaseGeneration, lease)) {
                metrics.complianceStaleWorkerResult();
                throw new SemanticEvaluationException(
                    SemanticEvaluationFailureCode.EVALUATION_ERROR,
                    "STALE_WORKER_RESULT", 0);
            }
            execution = modelExecutionService.execute(prepared);
            faultInjection.maybePause(
                ComplianceFaultInjection.PAUSE_AFTER_MODEL_RESPONSE,
                correlationId, job.id(), task.id());
        } else {
            execution = ComplianceExecutionResult.fromPrecomputed(prepared.precomputedOutcome());
        }
        if (transactionService.cancellationState(organizationId, job.id()).cancelRequested()
            && execution.success()) {
            execution = ComplianceExecutionResult.cancelledResult();
        }
        faultInjection.maybePause(
            ComplianceFaultInjection.PAUSE_BEFORE_PERSIST,
            correlationId, job.id(), task.id());
        var persist = persistenceService.persist(prepared, execution, correlationId);
        if (persist.rejectedStale()) {
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.EVALUATION_ERROR, "STALE_WORKER_RESULT", 0);
        }
        if (persist.cancelled()) {
            throw new SemanticEvaluationException(
                SemanticEvaluationFailureCode.LLM_CANCELLED, "Task cancelled", 0);
        }
        if (!persist.persisted() && persist.errorCode() != null) {
            throw new SemanticEvaluationException(
                mapPersistFailure(persist.errorCode()), persist.errorCode(), 0);
        }
        return new TaskResult(persist.requiresReview());
    }

    private SemanticEvaluationFailureCode mapPersistFailure(String code) {
        try {
            return SemanticEvaluationFailureCode.valueOf(code);
        } catch (RuntimeException ignored) {
            return SemanticEvaluationFailureCode.EVALUATION_ERROR;
        }
    }

    private static String domainErrorCode(SemanticEvaluationFailureCode code) {
        return switch (code) {
            case LLM_TIMEOUT, LLM_GENERATION_TIMEOUT -> "MODEL_TIMEOUT";
            case LLM_UNAVAILABLE, LLM_CONNECT_TIMEOUT -> "MODEL_UNAVAILABLE";
            case LLM_OVERLOADED, LLM_QUEUE_TIMEOUT -> "SLOT_WAIT_TIMEOUT";
            case CAPACITY_FULL -> "CAPACITY_FULL";
            case CAPACITY_WAIT_TIMEOUT -> "CAPACITY_WAIT_TIMEOUT";
            case CAPACITY_LEASE_LOST -> "CAPACITY_LEASE_LOST";
            case CAPACITY_PROVIDER_UNAVAILABLE -> "CAPACITY_PROVIDER_UNAVAILABLE";
            case LLM_CANCELLED -> "CANCEL_REQUESTED";
            default -> code.name();
        };
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

    private record TaskResult(boolean requiresReview) {
    }
}
