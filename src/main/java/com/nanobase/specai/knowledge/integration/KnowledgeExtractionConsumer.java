package com.nanobase.specai.knowledge.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.knowledge.application.KnowledgeExtractionJobService.KnowledgeRequested;
import com.nanobase.specai.knowledge.application.KnowledgeExtractionProcessor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeExtractionConsumer {
    static final String CONSUMER_NAME = "knowledge-extraction-consumer-v1";
    private static final TypeReference<EventEnvelope<KnowledgeRequested>> EVENT_TYPE =
        new TypeReference<>() { };
    private final ObjectMapper mapper;
    private final ConsumerIdempotencyService idempotency;
    private final KnowledgeExtractionProcessor processor;

    public KnowledgeExtractionConsumer(ObjectMapper mapper,
                                       ConsumerIdempotencyService idempotency,
                                       KnowledgeExtractionProcessor processor) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitConfiguration.KNOWLEDGE_QUEUE)
    public void consume(Message message) throws Exception {
        EventEnvelope<KnowledgeRequested> event = mapper.readValue(message.getBody(), EVENT_TYPE);
        if (event == null || event.eventId() == null || event.organizationId() == null
            || event.payload() == null || event.payload().jobId() == null) {
            throw new IllegalArgumentException("Invalid knowledge extraction event");
        }
        if (!idempotency.claim(CONSUMER_NAME, event.eventId())) {
            return;
        }
        try {
            processor.process(event.organizationId(), event.payload().jobId());
            idempotency.complete(CONSUMER_NAME, event.eventId());
        } catch (RuntimeException failure) {
            idempotency.failed(CONSUMER_NAME, event.eventId());
            throw failure;
        }
    }
}
