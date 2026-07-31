package com.nanobase.specai.document.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.application.DocumentExtractionPersistenceService;
import com.nanobase.specai.document.application.DocumentV11EnrichmentService;
import com.nanobase.specai.document.application.ProcessingJobService;
import com.nanobase.specai.document.domain.DocumentProcessingJob;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.integration.DocumentEvents.DocumentUploaded;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentProcessingCommand;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExternalProcessingReference;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ProcessingStatusResult;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ProcessingSubmission;
import com.nanobase.specai.document.segmentation.ClauseSegmentationService;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

@Component
public class DocumentUploadedConsumer {
    private static final Logger log = LoggerFactory.getLogger(DocumentUploadedConsumer.class);
    static final String CONSUMER_NAME = "document-processing-consumer-v1";
    private static final TypeReference<EventEnvelope<DocumentUploaded>> EVENT_TYPE =
        new TypeReference<>() { };
    private final ObjectMapper objectMapper;
    private final DocumentIntelligencePort intelligence;
    private final ProcessingJobService processing;
    private final DocumentExtractionPersistenceService extraction;
    private final DocumentV11EnrichmentService v11Enrichment;
    private final ClauseSegmentationService clauseSegmentation;
    private final ConsumerIdempotencyService idempotency;
    private final RabbitTemplate rabbit;
    private final PlatformMetrics metrics;
    private final int maximumRetries;
    private final double minimumQualityScore;
    private final Duration pollInterval;
    private final Duration orchestrationTimeout;
    private final Duration stallTimeout;

    public DocumentUploadedConsumer(
        ObjectMapper objectMapper,
        DocumentIntelligencePort intelligence,
        ProcessingJobService processing,
        DocumentExtractionPersistenceService extraction,
        DocumentV11EnrichmentService v11Enrichment,
        ClauseSegmentationService clauseSegmentation,
        ConsumerIdempotencyService idempotency,
        RabbitTemplate rabbit,
        PlatformMetrics metrics,
        @Value("${specai.messaging.maximum-consumer-retries:3}") int maximumRetries,
        @Value("${specai.document-intelligence.minimum-quality-score:0.50}")
        double minimumQualityScore,
        @Value("${specai.document-intelligence.poll-interval:PT15S}") Duration pollInterval,
        @Value("${specai.document-intelligence.orchestration-timeout:PT12H}")
        Duration orchestrationTimeout,
        @Value("${specai.document-intelligence.stall-timeout:PT45M}") Duration stallTimeout) {
        this.objectMapper = objectMapper;
        this.intelligence = intelligence;
        this.processing = processing;
        this.extraction = extraction;
        this.v11Enrichment = v11Enrichment;
        this.clauseSegmentation = clauseSegmentation;
        this.idempotency = idempotency;
        this.rabbit = rabbit;
        this.metrics = metrics;
        this.maximumRetries = maximumRetries;
        this.minimumQualityScore = minimumQualityScore;
        this.pollInterval = pollInterval;
        this.orchestrationTimeout = orchestrationTimeout;
        this.stallTimeout = stallTimeout;
    }

    @RabbitListener(queues = RabbitConfiguration.DOCUMENT_QUEUE)
    public void consume(Message message) {
        EventEnvelope<DocumentUploaded> event = null;
        Timer.Sample sample = metrics.processingStarted();
        boolean claimed = false;
        try {
            event = objectMapper.readValue(message.getBody(), EVENT_TYPE);
            validate(event);
            claimed = idempotency.claim(CONSUMER_NAME, event.eventId());
            if (!claimed) {
                metrics.processingCompleted(sample);
                return;
            }
            process(event);
            idempotency.complete(CONSUMER_NAME, event.eventId());
            metrics.processingCompleted(sample);
        } catch (Exception exception) {
            metrics.processingFailed(sample);
            log.error("Document processing consumer failed eventId={} jobId={} message={}",
                event == null ? null : event.eventId(),
                event == null || event.payload() == null ? null : event.payload().processingJobId(),
                exception.getMessage(), exception);
            if (claimed && event != null) {
                idempotency.failed(CONSUMER_NAME, event.eventId());
            }
            retryOrDeadLetter(message, event, exception);
        }
    }

    private void process(EventEnvelope<DocumentUploaded> event) {
        DocumentUploaded payload = event.payload();
        DocumentProcessingJob job = processing.get(
            event.organizationId(), payload.processingJobId());
        if (job.status() == DocumentStatus.CANCELLED) {
            return;
        }
        prepare(event.organizationId(), job);
        job = processing.get(event.organizationId(), job.id());
        ExternalProcessingReference reference;
        if (job.externalReference() == null) {
            ProcessingSubmission submission = intelligence.submit(new DocumentProcessingCommand(
                event.organizationId(), payload.projectId(), payload.documentId(),
                payload.documentVersionId(), payload.objectStorageKey(),
                payload.originalFileName(), payload.mimeType(), payload.sha256(),
                payload.languageHint(), payload.ocrRequired(), event.correlationId(),
                payload.fileSize(), false));
            reference = submission.reference();
            processing.submitted(event.organizationId(), job.id(), reference.provider(),
                reference.externalReference());
            if (submission.status() == DocumentStatus.MANUAL_REVIEW_REQUIRED) {
                processing.transition(event.organizationId(), job.id(),
                    DocumentStatus.MANUAL_REVIEW_REQUIRED,
                    "Doküman güvenli biçimde otomatik işlenemedi",
                    "MANUAL_REVIEW_REQUIRED", "Parser route requires manual review");
                return;
            }
        } else {
            reference = new ExternalProcessingReference(job.provider(), job.externalReference(),
                event.organizationId(), payload.documentVersionId());
        }
        ProcessingStatusResult status = awaitTerminalStatus(
            event.organizationId(), job.id(), reference);
        if (status.status() != DocumentStatus.READY) {
            processing.transition(event.organizationId(), job.id(), status.status(),
                safeMessage(status.message()), safeCode(status.errorCode()),
                "Document parser did not complete successfully");
            return;
        }
        ensureStage(event.organizationId(), job.id(), DocumentStatus.PARSING);
        ensureStage(event.organizationId(), job.id(), DocumentStatus.STRUCTURE_DETECTION);
        var enriched = clauseSegmentation.enrich(intelligence.getResult(reference));
        extraction.persist(event.organizationId(), job.id(), enriched, payload.ocrRequired());
        v11Enrichment.enrich(
            event.organizationId(),
            payload.projectId(),
            payload.documentVersionId(),
            enriched.result(),
            payload.ocrRequired());
        var result = enriched.result();
        if (result.textQualityScore() < minimumQualityScore) {
            processing.transition(event.organizationId(), job.id(),
                DocumentStatus.MANUAL_REVIEW_REQUIRED,
                "Metin kalitesi manuel inceleme eşiğinin altında",
                "LOW_TEXT_QUALITY", "Extracted text quality is below the configured threshold");
            return;
        }
        processing.transition(event.organizationId(), job.id(), DocumentStatus.INDEXING,
            null, null, null);
        processing.transition(event.organizationId(), job.id(), DocumentStatus.READY,
            null, null, null);
    }

    private ProcessingStatusResult awaitTerminalStatus(
        java.util.UUID organizationId,
        java.util.UUID jobId,
        ExternalProcessingReference reference) {
        Instant started = Instant.now();
        Instant lastProgressAt = started;
        String lastFingerprint = null;
        while (true) {
            DocumentProcessingJob current = processing.get(organizationId, jobId);
            if (current.status() == DocumentStatus.CANCELLED) {
                intelligence.cancel(reference);
                return new ProcessingStatusResult(
                    DocumentStatus.CANCELLED,
                    "CANCELLED",
                    100,
                    "Document processing cancelled",
                    "CANCELLED");
            }
            ProcessingStatusResult status = intelligence.getStatus(reference);
            if (status.terminal()) {
                return status;
            }
            DocumentStatus mapped = status.status();
            if (mapped != current.status()
                && com.nanobase.specai.document.domain.ProcessingStateMachine.canTransition(
                    current.status(), mapped)) {
                processing.transition(organizationId, jobId, mapped,
                    safeMessage(status.message()), null, null);
            }
            String fingerprint = status.progress() + "|" + safeMessage(status.message())
                + "|" + String.valueOf(status.errorCode());
            if (!fingerprint.equals(lastFingerprint)) {
                lastFingerprint = fingerprint;
                lastProgressAt = Instant.now();
            }
            Duration elapsed = Duration.between(started, Instant.now());
            if (elapsed.compareTo(orchestrationTimeout) > 0) {
                throw new ProviderUnavailableException(
                    "Document parser orchestration timeout exceeded");
            }
            if (Duration.between(lastProgressAt, Instant.now()).compareTo(stallTimeout) > 0) {
                throw new ProviderUnavailableException(
                    "Document parser progress stalled");
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ProviderUnavailableException("Document parser wait interrupted");
            }
        }
    }

    private void prepare(java.util.UUID organizationId, DocumentProcessingJob job) {
        ensureStage(organizationId, job.id(), DocumentStatus.VIRUS_SCANNING);
        ensureStage(organizationId, job.id(), DocumentStatus.CLASSIFYING);
        ensureStage(organizationId, job.id(), DocumentStatus.QUEUED);
    }

    private void ensureStage(java.util.UUID organizationId, java.util.UUID jobId,
                             DocumentStatus target) {
        DocumentProcessingJob job = processing.get(organizationId, jobId);
        if (job.status() == target || job.status().terminal()) {
            return;
        }
        if (com.nanobase.specai.document.domain.ProcessingStateMachine.canTransition(
            job.status(), target)) {
            processing.transition(organizationId, jobId, target, null, null, null);
        }
    }

    private void validate(EventEnvelope<DocumentUploaded> event) {
        if (event == null || event.eventId() == null || event.organizationId() == null
            || event.correlationId() == null || event.payload() == null
            || event.payload().processingJobId() == null
            || event.payload().documentVersionId() == null) {
            throw new IllegalArgumentException("Invalid document processing event envelope");
        }
        String expectedPrefix = "specai-original/" + event.organizationId() + "/";
        if (!event.payload().objectStorageKey().startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                "Object key does not match the event organization");
        }
    }

    private void retryOrDeadLetter(Message original, EventEnvelope<DocumentUploaded> event,
                                   Exception exception) {
        int retryCount = retryCount(original);
        metrics.consumerRetried();
        if (retryCount >= maximumRetries) {
            try {
                if (event != null && event.payload() != null) {
                    DocumentProcessingJob job = processing.get(
                        event.organizationId(), event.payload().processingJobId());
                    if (!job.status().terminal()) {
                        processing.transition(event.organizationId(), job.id(),
                            DocumentStatus.FAILED, "Doküman işleme başarısız oldu",
                            safeCode(exception), "Document processing exhausted its retries");
                    }
                }
            } catch (RuntimeException ignored) {
                // A malformed poison message is still routed to the DLQ.
            }
            rabbit.send(RabbitConfiguration.DEAD_LETTER_EXCHANGE,
                RabbitConfiguration.DEAD_ROUTING_KEY, copy(original, retryCount + 1));
            return;
        }
        String routingKey = switch (retryCount) {
            case 0 -> RabbitConfiguration.RETRY_30;
            case 1 -> RabbitConfiguration.RETRY_120;
            default -> RabbitConfiguration.RETRY_600;
        };
        rabbit.send(RabbitConfiguration.RETRY_EXCHANGE, routingKey,
            copy(original, retryCount + 1));
    }

    private Message copy(Message original, int retryCount) {
        return MessageBuilder.withBody(original.getBody())
            .setContentType("application/json")
            .setMessageId(original.getMessageProperties().getMessageId())
            .setHeader("x-retry-count", retryCount)
            .build();
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeader("x-retry-count");
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String safeCode(Object value) {
        if (value instanceof String string && string.matches("[A-Z0-9_]{1,100}")) {
            return string;
        }
        return "DOCUMENT_PROCESSING_FAILED";
    }

    private String safeMessage(String value) {
        return value == null || value.isBlank()
            ? "Doküman parser tarafından işleniyor"
            : value.substring(0, Math.min(500, value.length()));
    }
}
