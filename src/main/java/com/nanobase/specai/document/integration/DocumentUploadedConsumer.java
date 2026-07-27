package com.nanobase.specai.document.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.application.DocumentExtractionPersistenceService;
import com.nanobase.specai.document.application.ProcessingJobService;
import com.nanobase.specai.document.domain.DocumentProcessingJob;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.integration.DocumentEvents.DocumentUploaded;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentProcessingCommand;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExternalProcessingReference;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ProcessingSubmission;
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

@Component
public class DocumentUploadedConsumer {
    static final String CONSUMER_NAME = "document-processing-consumer-v1";
    private static final TypeReference<EventEnvelope<DocumentUploaded>> EVENT_TYPE =
        new TypeReference<>() { };
    private final ObjectMapper objectMapper;
    private final DocumentIntelligencePort intelligence;
    private final ProcessingJobService processing;
    private final DocumentExtractionPersistenceService extraction;
    private final ConsumerIdempotencyService idempotency;
    private final RabbitTemplate rabbit;
    private final PlatformMetrics metrics;
    private final int maximumRetries;
    private final double minimumQualityScore;

    public DocumentUploadedConsumer(
        ObjectMapper objectMapper,
        DocumentIntelligencePort intelligence,
        ProcessingJobService processing,
        DocumentExtractionPersistenceService extraction,
        ConsumerIdempotencyService idempotency,
        RabbitTemplate rabbit,
        PlatformMetrics metrics,
        @Value("${specai.messaging.maximum-consumer-retries:3}") int maximumRetries,
        @Value("${specai.document-intelligence.minimum-quality-score:0.50}")
        double minimumQualityScore) {
        this.objectMapper = objectMapper;
        this.intelligence = intelligence;
        this.processing = processing;
        this.extraction = extraction;
        this.idempotency = idempotency;
        this.rabbit = rabbit;
        this.metrics = metrics;
        this.maximumRetries = maximumRetries;
        this.minimumQualityScore = minimumQualityScore;
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
        var status = intelligence.getStatus(reference);
        if (!status.terminal()) {
            DocumentStatus current = processing.get(event.organizationId(), job.id()).status();
            if (status.status() != current
                && com.nanobase.specai.document.domain.ProcessingStateMachine.canTransition(
                    current, status.status())) {
                processing.transition(event.organizationId(), job.id(), status.status(),
                    safeMessage(status.message()), null, null);
            }
            throw new ProviderUnavailableException("Document parser job is still running");
        }
        if (status.status() != DocumentStatus.READY) {
            processing.transition(event.organizationId(), job.id(), status.status(),
                safeMessage(status.message()), safeCode(status.errorCode()),
                "Document parser did not complete successfully");
            return;
        }
        ensureStage(event.organizationId(), job.id(), DocumentStatus.PARSING);
        ensureStage(event.organizationId(), job.id(), DocumentStatus.STRUCTURE_DETECTION);
        var result = intelligence.getResult(reference);
        extraction.persist(event.organizationId(), job.id(), result, payload.ocrRequired());
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
            if (event != null && event.payload() != null) {
                DocumentProcessingJob job = processing.get(
                    event.organizationId(), event.payload().processingJobId());
                if (!job.status().terminal()) {
                    processing.transition(event.organizationId(), job.id(),
                        DocumentStatus.FAILED, "Doküman işleme başarısız oldu",
                        safeCode(exception), "Document processing exhausted its retries");
                }
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
