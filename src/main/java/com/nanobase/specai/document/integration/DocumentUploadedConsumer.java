package com.nanobase.specai.document.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.application.DocumentProcessingService;
import com.nanobase.specai.document.integration.DocumentEvents.DocumentUploaded;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentProcessingCommand;
import com.nanobase.specai.integration.outbox.ProcessedEvent;
import com.nanobase.specai.integration.outbox.ProcessedEventRepository;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadedConsumer {
    private static final TypeReference<EventEnvelope<DocumentUploaded>> EVENT_TYPE =
        new TypeReference<>() { };
    private final ObjectMapper objectMapper;
    private final DocumentIntelligencePort intelligence;
    private final DocumentProcessingService processing;
    private final ProcessedEventRepository processedEvents;
    private final RabbitTemplate rabbit;
    private final PlatformMetrics metrics;
    private final Clock clock = Clock.systemUTC();

    public DocumentUploadedConsumer(ObjectMapper objectMapper,
                                    DocumentIntelligencePort intelligence,
                                    DocumentProcessingService processing,
                                    ProcessedEventRepository processedEvents,
                                    RabbitTemplate rabbit,
                                    PlatformMetrics metrics) {
        this.objectMapper = objectMapper;
        this.intelligence = intelligence;
        this.processing = processing;
        this.processedEvents = processedEvents;
        this.rabbit = rabbit;
        this.metrics = metrics;
    }

    @RabbitListener(queues = RabbitConfiguration.DOCUMENT_QUEUE)
    public void consume(Message message) {
        EventEnvelope<DocumentUploaded> event = null;
        Timer.Sample sample = metrics.processingStarted();
        try {
            event = objectMapper.readValue(message.getBody(), EVENT_TYPE);
            if (processedEvents.existsById(event.eventId())) {
                return;
            }
            DocumentUploaded payload = event.payload();
            processing.start(event.organizationId(), payload.documentVersionId());
            var result = intelligence.process(new DocumentProcessingCommand(
                event.organizationId(), payload.projectId(), payload.documentId(),
                payload.documentVersionId(), payload.objectStorageKey(),
                payload.mimeType(), payload.fileSize()));
            processing.complete(event.organizationId(), payload.documentVersionId(), result);
            processedEvents.save(new ProcessedEvent(event.eventId(), event.organizationId(),
                event.eventType(), clock.instant()));
            metrics.processingCompleted(sample);
        } catch (Exception exception) {
            metrics.processingFailed(sample);
            retryOrDeadLetter(message, event, exception);
        }
    }

    private void retryOrDeadLetter(Message original, EventEnvelope<DocumentUploaded> event,
                                   Exception exception) {
        int retryCount = retryCount(original);
        metrics.consumerRetried();
        if (retryCount >= 3) {
            if (event != null && event.payload() != null) {
                processing.fail(event.organizationId(), event.payload().documentVersionId(),
                    "DOCUMENT_PROCESSING_FAILED", exception.getMessage());
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
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
