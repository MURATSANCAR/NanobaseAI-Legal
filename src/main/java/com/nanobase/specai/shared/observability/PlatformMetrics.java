package com.nanobase.specai.shared.observability;

import com.nanobase.specai.integration.outbox.OutboxEventRepository;
import com.nanobase.specai.integration.outbox.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PlatformMetrics {
    private static final Set<String> SPRINT_7_METRICS = Set.of(
        "workflow_instance_total", "workflow_instance_failed_total",
        "workflow_node_execution_total", "workflow_transition_total",
        "workflow_dead_end_total", "task_created_total", "task_completed_total",
        "task_sla_breached_total", "task_escalated_total",
        "approval_requested_total", "approval_rejected_total",
        "clarification_created_total", "clarification_sent_total",
        "report_generated_total", "report_generation_failed_total",
        "decision_support_total", "project_finalized_total",
        "project_reopened_total", "notification_sent_total",
        "notification_failed_total");
    private static final Set<String> SPRINT_9_METRICS = Set.of(
        "pilot_feedback_total", "feedback_blocker_total",
        "feedback_resolution_duration_seconds", "root_cause_distribution",
        "regression_case_total", "regression_failure_total",
        "experiment_total", "experiment_failure_total",
        "quality_gate_failure_total", "shadow_disagreement_rate",
        "canary_error_rate", "configuration_rollback_total",
        "release_gate_failure_total", "release_deployment_total",
        "release_rollback_total", "go_live_no_go_total",
        "stabilization_incident_total", "user_satisfaction_score",
        "manual_time_saved_minutes");
    private static final Set<String> GO_LIVE_METRICS = Set.of(
        "llm_unavailable_total",
        "classification_failure_total",
        "deterministic_evaluation_total",
        "compliance_gap_created_total",
        "duplicate_gap_rejected_total",
        "summary_rebuild_failure_total",
        "feature_gate_denied_total",
        "compliance_failed_total",
        "compliance_job_duration_ms");
    private final MeterRegistry registry;
    private final Counter documentUpload;
    private final Counter documentUploadFailed;
    private final Counter documentProcessingJob;
    private final Counter documentProcessingJobFailed;
    private final Counter documentProcessingAttempt;
    private final Counter outboxPublishFailed;
    private final Counter outboxClaim;
    private final Counter outboxReclaimed;
    private final Counter outboxDead;
    private final Counter orphanDetected;
    private final Counter orphanDeleted;
    private final Counter parserWarning;
    private final Counter pageExtracted;
    private final Counter clauseExtracted;
    private final Counter manualReview;
    private final Counter sseConnection;
    private final AtomicInteger sseActive = new AtomicInteger();
    private final Counter rabbitConsumerRetry;
    private final Timer documentProcessingDuration;
    private final Counter riskAnalysis;
    private final Counter riskRecordCreated;
    private final Counter riskManualReview;
    private final Counter riskPropagationCandidate;
    private final Counter ambiguityDetected;
    private final Counter conflictCandidate;
    private final Counter conflictConfirmed;
    private final Counter documentChangeItem;
    private final Counter impactAnalysis;
    private final Counter analysisStale;
    private final Counter reanalysisRequired;
    private final Counter clarificationCandidate;
    private final Counter mitigationCandidate;
    private final Counter knowledgeEntityCreated;
    private final Counter knowledgeAttributeExtracted;
    private final Counter knowledgeRelationExtracted;
    private final Counter capabilityExtracted;
    private final Counter evidenceFragmentCreated;
    private final Counter evidenceInvalid;
    private final Counter entityResolutionAmbiguous;
    private final Counter complianceAnalysis;
    private final Counter complianceEvaluation;
    private final Counter complianceDeterministic;
    private final Counter complianceLlm;
    private final Counter complianceManualReview;
    private final Counter complianceMissingEvidence;
    private final Counter complianceContradictoryEvidence;
    private final Counter retrievalCandidate;
    private final Counter comparisonStrategy;
    private final Counter complianceShadowAttempt;
    private final Counter complianceShadowAgreement;
    private final Counter complianceShadowDisagreement;
    private final Counter complianceFalseCompliant;
    private final Timer retrievalDuration;
    private final Timer rerankingDuration;

    public PlatformMetrics(MeterRegistry registry, OutboxEventRepository outbox) {
        this.registry = registry;
        documentUpload = registry.counter("document.upload.total");
        documentUploadFailed = registry.counter("document.upload.failed.total");
        documentProcessingJob = registry.counter("document.processing.job");
        documentProcessingJobFailed = registry.counter("document.processing.job.failed");
        documentProcessingAttempt = registry.counter("document.processing.attempt");
        outboxPublishFailed = registry.counter("outbox.publish.failed.total");
        outboxClaim = registry.counter("outbox.claim.total");
        outboxReclaimed = registry.counter("outbox.reclaimed.total");
        outboxDead = registry.counter("outbox.dead.total");
        orphanDetected = registry.counter("orphan.object.detected.total");
        orphanDeleted = registry.counter("orphan.object.deleted.total");
        parserWarning = registry.counter("document.parser.warning.total");
        pageExtracted = registry.counter("document.page.extracted.total");
        clauseExtracted = registry.counter("document.clause.extracted.total");
        manualReview = registry.counter("document.processing.manual.review.total");
        sseConnection = registry.counter("sse.connection.total");
        Gauge.builder("sse.connection.active", sseActive, AtomicInteger::get)
            .register(registry);
        rabbitConsumerRetry = registry.counter("rabbitmq.consumer.retry.total");
        documentProcessingDuration = registry.timer("document.processing.duration");
        riskAnalysis = registry.counter("risk_analysis_total");
        riskRecordCreated = registry.counter("risk_record_created_total");
        riskManualReview = registry.counter("risk_manual_review_total");
        riskPropagationCandidate = registry.counter("risk_propagation_candidate_total");
        ambiguityDetected = registry.counter("ambiguity_detected_total");
        conflictCandidate = registry.counter("conflict_candidate_total");
        conflictConfirmed = registry.counter("conflict_confirmed_total");
        documentChangeItem = registry.counter("document_change_item_total");
        impactAnalysis = registry.counter("impact_analysis_total");
        analysisStale = registry.counter("analysis_stale_total");
        reanalysisRequired = registry.counter("reanalysis_required_total");
        clarificationCandidate = registry.counter("clarification_candidate_total");
        mitigationCandidate = registry.counter("mitigation_candidate_total");
        knowledgeEntityCreated = registry.counter("knowledge_entity_created_total");
        knowledgeAttributeExtracted =
            registry.counter("knowledge_attribute_extracted_total");
        knowledgeRelationExtracted =
            registry.counter("knowledge_relation_extracted_total");
        capabilityExtracted = registry.counter("capability_extracted_total");
        evidenceFragmentCreated = registry.counter("evidence_fragment_created_total");
        evidenceInvalid = registry.counter("evidence_invalid_total");
        entityResolutionAmbiguous =
            registry.counter("entity_resolution_ambiguous_total");
        complianceAnalysis = registry.counter("compliance_analysis_total");
        complianceEvaluation = registry.counter("compliance_evaluation_total");
        complianceDeterministic = registry.counter("compliance_deterministic_total");
        complianceLlm = registry.counter("compliance_llm_total");
        complianceManualReview = registry.counter("compliance_manual_review_total");
        complianceMissingEvidence =
            registry.counter("compliance_missing_evidence_total");
        complianceContradictoryEvidence =
            registry.counter("compliance_contradictory_evidence_total");
        retrievalCandidate = registry.counter("retrieval_candidate_total");
        comparisonStrategy = registry.counter("comparison_strategy_total");
        complianceShadowAttempt = registry.counter("compliance_shadow_total");
        complianceShadowAgreement =
            registry.counter("compliance_shadow_agreement_total");
        complianceShadowDisagreement =
            registry.counter("compliance_shadow_disagreement_total");
        complianceFalseCompliant =
            registry.counter("compliance_false_compliant_total");
        retrievalDuration = registry.timer("retrieval_duration_seconds");
        rerankingDuration = registry.timer("reranking_duration_seconds");
        registry.timer("risk_analysis_duration_seconds");
        registry.timer("conflict_analysis_duration_seconds");
        registry.timer("impact_analysis_duration_seconds");
        SPRINT_7_METRICS.forEach(registry::counter);
        SPRINT_9_METRICS.forEach(registry::counter);
        GO_LIVE_METRICS.forEach(name -> {
            if (name.endsWith("_ms") || name.endsWith("_seconds")) {
                registry.timer(name);
            } else {
                registry.counter(name);
            }
        });
        Gauge.builder("llm_active_requests", new AtomicInteger(0), AtomicInteger::get)
            .register(registry);
        Gauge.builder("llm_queue_depth", new AtomicInteger(0), AtomicInteger::get)
            .register(registry);
        registry.timer("llm_queue_wait_ms");
        registry.timer("llm_generation_ms");
        Gauge.builder("outbox.pending.total", outbox,
                repository -> repository.countByStatus(OutboxStatus.PENDING))
            .register(registry);
    }

    public void uploadSucceeded() { documentUpload.increment(); }
    public void uploadFailed() { documentUploadFailed.increment(); }
    public void processingJobCreated() { documentProcessingJob.increment(); }
    public void processingJobFailed() { documentProcessingJobFailed.increment(); }
    public Timer.Sample processingStarted() {
        documentProcessingAttempt.increment();
        return Timer.start(registry);
    }
    public void processingCompleted(Timer.Sample sample) {
        sample.stop(documentProcessingDuration);
    }
    public void processingFailed(Timer.Sample sample) {
        sample.stop(documentProcessingDuration);
    }
    public void processingDuration(Duration duration) {
        registry.timer("document.processing.duration").record(duration);
    }
    public void processingStageDuration(String stage, Duration duration) {
        registry.timer("document.processing.stage.duration", "stage", stage)
            .record(duration);
    }
    public void parserRouted(String provider, String decision, String ocrMode) {
        registry.counter("document.parser.route", "provider", provider,
            "decision", decision, "ocr", ocrMode).increment();
    }
    public void outboxPublishFailed() { outboxPublishFailed.increment(); }
    public void outboxClaimed(int claimed, int reclaimed) {
        outboxClaim.increment(claimed);
        outboxReclaimed.increment(reclaimed);
    }
    public void outboxDead() { outboxDead.increment(); }
    public void orphanDetected() { orphanDetected.increment(); }
    public void orphanDeleted() { orphanDeleted.increment(); }
    public void parserWarnings(int count) { parserWarning.increment(count); }
    public void pagesExtracted(int count) { pageExtracted.increment(count); }
    public void clausesExtracted(int count) { clauseExtracted.increment(count); }
    public void manualReview() { manualReview.increment(); }
    public void sseConnected() { sseConnection.increment(); }
    public void sseOpened() {
        sseConnected();
        sseActive.incrementAndGet();
    }
    public void sseClosed() {
        sseActive.updateAndGet(value -> Math.max(0, value - 1));
    }
    public void consumerRetried() { rabbitConsumerRetry.increment(); }
    public void riskAnalysis() { riskAnalysis.increment(); }
    public void riskCreated() { riskRecordCreated.increment(); }
    public void riskManualReview() { riskManualReview.increment(); }
    public void riskPropagationCandidates(int count) {
        riskPropagationCandidate.increment(count);
    }
    public void ambiguityDetected() { ambiguityDetected.increment(); }
    public void conflictCandidates(int count) { conflictCandidate.increment(count); }
    public void conflictConfirmed() { conflictConfirmed.increment(); }
    public void documentChangeItems(int count) { documentChangeItem.increment(count); }
    public void impactAnalysis() { impactAnalysis.increment(); }
    public void staleDetected(int count) { analysisStale.increment(count); }
    public void reanalysisRequired(int count) { reanalysisRequired.increment(count); }
    public void clarificationCandidate() { clarificationCandidate.increment(); }
    public void mitigationCandidate() { mitigationCandidate.increment(); }
    public void knowledgeEntitiesCreated(int count) {
        knowledgeEntityCreated.increment(count);
    }
    public void knowledgeAttributesExtracted(int count) {
        knowledgeAttributeExtracted.increment(count);
    }
    public void knowledgeRelationsExtracted(int count) {
        knowledgeRelationExtracted.increment(count);
    }
    public void capabilitiesExtracted(int count) {
        capabilityExtracted.increment(count);
    }
    public void evidenceFragmentsCreated(int count) {
        evidenceFragmentCreated.increment(count);
    }
    public void evidenceInvalid() { evidenceInvalid.increment(); }
    public void entityResolutionAmbiguous() { entityResolutionAmbiguous.increment(); }
    public void complianceAnalysis() { complianceAnalysis.increment(); }
    public void complianceEvaluation() { complianceEvaluation.increment(); }
    public void complianceDeterministic() { complianceDeterministic.increment(); }
    public void complianceLlm() { complianceLlm.increment(); }
    public void complianceManualReview() { complianceManualReview.increment(); }
    public void complianceMissingEvidence() { complianceMissingEvidence.increment(); }
    public void complianceContradictoryEvidence() {
        complianceContradictoryEvidence.increment();
    }
    public void retrievalCandidates(int count) { retrievalCandidate.increment(count); }
    public Timer.Sample retrievalStarted() { return Timer.start(registry); }
    public void retrievalCompleted(Timer.Sample sample) { sample.stop(retrievalDuration); }
    public Timer.Sample rerankingStarted() { return Timer.start(registry); }
    public void rerankingCompleted(Timer.Sample sample) { sample.stop(rerankingDuration); }
    public void comparisonStrategy(String provider) {
        comparisonStrategy.increment();
        registry.counter("comparison_strategy_provider_total", "provider", provider)
            .increment();
    }

    public void complianceLlmProfile(String profile, boolean success, Duration duration) {
        String safeProfile = profile == null || profile.isBlank() ? "unknown" : profile;
        registry.counter("compliance_llm_profile_total",
            "profile", safeProfile,
            "result", success ? "success" : "failure").increment();
        if (duration != null) {
            registry.timer("compliance_llm_latency_seconds", "profile", safeProfile)
                .record(duration);
        }
    }

    public void complianceShadowAttempt() {
        complianceShadowAttempt.increment();
    }

    public void complianceShadowAgreement(boolean agreement) {
        if (agreement) {
            complianceShadowAgreement.increment();
        } else {
            complianceShadowDisagreement.increment();
        }
    }

    public void complianceShadowFailure(String failureCode) {
        registry.counter("compliance_shadow_failure_total",
            "failure_code", failureCode == null ? "unknown" : failureCode).increment();
    }

    public void complianceFastEscalation(String reason) {
        registry.counter("compliance_fast_escalation_total",
            "reason", reason == null ? "unknown" : reason).increment();
    }

    public void complianceStructuredJsonFailure(String profile) {
        registry.counter("compliance_structured_json_failure_total",
            "profile", profile == null || profile.isBlank() ? "unknown" : profile)
            .increment();
    }

    public void complianceFalseCompliant() {
        complianceFalseCompliant.increment();
    }

    public void featureGateDenied(String featureCode, String reason) {
        registry.counter("feature_gate_denied_total",
            "feature", featureCode == null ? "unknown" : featureCode,
            "reason", reason == null ? "unknown" : reason).increment();
    }

    public void classificationFailure() {
        registry.counter("classification_failure_total").increment();
    }

    public void deterministicEvaluation() {
        registry.counter("deterministic_evaluation_total").increment();
    }

    public void complianceGapCreated() {
        registry.counter("compliance_gap_created_total").increment();
    }

    public void duplicateGapRejected() {
        registry.counter("duplicate_gap_rejected_total").increment();
    }

    public void summaryRebuildFailure() {
        registry.counter("summary_rebuild_failure_total").increment();
    }

    public void llmUnavailable() {
        registry.counter("llm_unavailable_total").increment();
    }

    public void complianceFailed() {
        registry.counter("compliance_failed_total").increment();
    }

    public void complianceJobDuration(Duration duration) {
        if (duration != null) {
            registry.timer("compliance_job_duration_ms").record(duration);
        }
    }

    public void sprint7(String metricName) {
        if (!SPRINT_7_METRICS.contains(metricName)) {
            throw new IllegalArgumentException("Unknown Sprint 7 metric");
        }
        registry.counter(metricName).increment();
    }

    public void sprint9(String metricName) {
        if (!SPRINT_9_METRICS.contains(metricName)) {
            throw new IllegalArgumentException("Unknown Sprint 9 metric");
        }
        registry.counter(metricName).increment();
    }
}
