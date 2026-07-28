package com.nanobase.specai.shared.observability;

import com.nanobase.specai.integration.outbox.OutboxEventRepository;
import com.nanobase.specai.integration.outbox.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PlatformMetrics {
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
        registry.timer("risk_analysis_duration_seconds");
        registry.timer("conflict_analysis_duration_seconds");
        registry.timer("impact_analysis_duration_seconds");
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
}
