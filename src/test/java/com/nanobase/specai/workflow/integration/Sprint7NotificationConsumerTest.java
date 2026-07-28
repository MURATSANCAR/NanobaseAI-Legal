package com.nanobase.specai.workflow.integration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

class Sprint7NotificationConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validEventIsProcessedAndCompletedExactlyOnce() throws Exception {
        ConsumerIdempotencyService idempotency = mock(ConsumerIdempotencyService.class);
        Sprint7NotificationEventProcessor processor =
            mock(Sprint7NotificationEventProcessor.class);
        UUID eventId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        when(idempotency.claim(Sprint7NotificationConsumer.CONSUMER_NAME, eventId))
            .thenReturn(true);
        Message message = message("""
            {"eventId":"%s","organizationId":"%s","eventType":"task.created.v1",
             "payload":{"taskId":"%s","documentText":"must-be-sanitized"}}
            """.formatted(eventId, organizationId, UUID.randomUUID()));

        new Sprint7NotificationConsumer(mapper, idempotency, processor).consume(message);

        verify(processor).process(
            org.mockito.ArgumentMatchers.eq(organizationId),
            org.mockito.ArgumentMatchers.eq(eventId),
            org.mockito.ArgumentMatchers.eq("task.created.v1"),
            org.mockito.ArgumentMatchers.any());
        verify(idempotency).complete(Sprint7NotificationConsumer.CONSUMER_NAME, eventId);
    }

    @Test
    void duplicateEventIsIgnoredBeforeDispatch() throws Exception {
        ConsumerIdempotencyService idempotency = mock(ConsumerIdempotencyService.class);
        Sprint7NotificationEventProcessor processor =
            mock(Sprint7NotificationEventProcessor.class);
        UUID eventId = UUID.randomUUID();
        when(idempotency.claim(Sprint7NotificationConsumer.CONSUMER_NAME, eventId))
            .thenReturn(false);
        Message message = message("""
            {"eventId":"%s","organizationId":"%s","eventType":"task.created.v1",
             "payload":{"taskId":"%s"}}
            """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID()));

        new Sprint7NotificationConsumer(mapper, idempotency, processor).consume(message);

        verify(processor, never()).process(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    private Message message(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8));
    }
}
