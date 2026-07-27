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
    private final Counter rabbitConsumerRetry;
    private final Timer documentProcessingDuration;

    public PlatformMetrics(MeterRegistry registry, OutboxEventRepository outbox) {
        this.registry = registry;
        documentUpload = registry.counter("document.upload.total");
        documentUploadFailed = registry.counter("document.upload.failed.total");
        documentProcessing = registry.counter("document.processing.total");
        documentProcessingFailed = registry.counter("document.processing.failed.total");
        outboxPublishFailed = registry.counter("outbox.publish.failed.total");
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
    public void consumerRetried() { rabbitConsumerRetry.increment(); }
}
