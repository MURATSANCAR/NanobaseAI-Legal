package com.nanobase.specai.shared.observability;

import com.nanobase.specai.integration.outbox.OutboxEventRepository;
import com.nanobase.specai.integration.outbox.OutboxStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PlatformMetrics {
    private final MeterRegistry registry;
    private final Counter documentUpload;
    private final Counter documentUploadFailed;
    private final Counter documentProcessing;
    private final Counter documentProcessingFailed;
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
    private final Counter rabbitConsumerRetry;
    private final Timer documentProcessingDuration;

    public PlatformMetrics(MeterRegistry registry, OutboxEventRepository outbox) {
        this.registry = registry;
        documentUpload = registry.counter("document.upload.total");
        documentUploadFailed = registry.counter("document.upload.failed.total");
        documentProcessing = registry.counter("document.processing.total");
        documentProcessingFailed = registry.counter("document.processing.failed.total");
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
        rabbitConsumerRetry = registry.counter("rabbitmq.consumer.retry.total");
        documentProcessingDuration = registry.timer("document.processing.duration");
        Gauge.builder("outbox.pending.total", outbox,
                repository -> repository.countByStatus(OutboxStatus.PENDING))
            .register(registry);
    }

    public void uploadSucceeded() { documentUpload.increment(); }
    public void uploadFailed() { documentUploadFailed.increment(); }
    public Timer.Sample processingStarted() {
        documentProcessing.increment();
        return Timer.start(registry);
    }
    public void processingCompleted(Timer.Sample sample) {
        sample.stop(documentProcessingDuration);
    }
    public void processingFailed(Timer.Sample sample) {
        documentProcessingFailed.increment();
        sample.stop(documentProcessingDuration);
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
    public void consumerRetried() { rabbitConsumerRetry.increment(); }
}
