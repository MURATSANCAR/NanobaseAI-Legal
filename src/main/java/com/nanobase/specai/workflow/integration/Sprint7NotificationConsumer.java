package com.nanobase.specai.workflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class Sprint7NotificationConsumer {
    static final String CONSUMER_NAME = "sprint7-notification-consumer-v1";

    private final ObjectMapper mapper;
    private final ConsumerIdempotencyService idempotency;
    private final Sprint7NotificationEventProcessor processor;

    public Sprint7NotificationConsumer(ObjectMapper mapper,
                                       ConsumerIdempotencyService idempotency,
                                       Sprint7NotificationEventProcessor processor) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitConfiguration.SPRINT7_NOTIFICATION_QUEUE)
    public void consume(Message message) throws Exception {
        JsonNode envelope = mapper.readTree(message.getBody());
        UUID eventId = uuid(envelope, "eventId");
        UUID organizationId = uuid(envelope, "organizationId");
        String eventType = text(envelope, "eventType");
        JsonNode payload = envelope.path("payload");
        if (payload.isMissingNode() || payload.isNull() || !payload.isObject()) {
            throw new IllegalArgumentException("Notification event payload must be an object");
        }
        if (!idempotency.claim(CONSUMER_NAME, eventId)) {
            return;
        }
        try {
            processor.process(organizationId, eventId, eventType, payload);
            idempotency.complete(CONSUMER_NAME, eventId);
        } catch (RuntimeException failure) {
            idempotency.failed(CONSUMER_NAME, eventId);
            throw failure;
        }
    }

    private static UUID uuid(JsonNode envelope, String field) {
        try {
            return UUID.fromString(text(envelope, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "Notification event " + field + " must be a UUID", exception);
        }
    }

    private static String text(JsonNode envelope, String field) {
        String value = envelope.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Notification event " + field + " is required");
        }
        return value;
    }
}
