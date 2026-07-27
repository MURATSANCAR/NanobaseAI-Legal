package com.nanobase.specai.integration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.integration.DocumentEvents.DocumentUploaded;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.shared.web.RequestContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {
    private final OutboxEventRepository events;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public OutboxService(OutboxEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    public UUID documentUploaded(UUID organizationId, DocumentUploaded payload) {
        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();
        UUID correlationId = RequestContext.current().correlationId();
        EventEnvelope<DocumentUploaded> envelope = new EventEnvelope<>(
            eventId, "DocumentProcessingRequested", 1, organizationId, correlationId,
            correlationId, now, payload);
        events.save(new OutboxEvent(eventId, "Document", payload.documentId(),
            "DocumentProcessingRequested", 1, RabbitConfiguration.DOCUMENT_ROUTING_KEY,
            json(envelope), organizationId, correlationId, now));
        return eventId;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox event cannot be serialized", exception);
        }
    }
}
