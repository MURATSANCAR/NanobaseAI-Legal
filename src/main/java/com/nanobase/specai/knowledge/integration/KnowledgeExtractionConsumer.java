package com.nanobase.specai.knowledge.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.knowledge.application.KnowledgeExtractionJobService.KnowledgeRequested;
import com.nanobase.specai.knowledge.application.KnowledgeExtractionProcessor;
import com.nanobase.specai.knowledge.application.KnowledgeExtractionProcessor.PreparedJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeExtractionConsumer {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionConsumer.class);
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
            // Two separate transactional boundaries: RUNNING commits before the AI call.
            PreparedJob prepared = processor.prepareRunning(
                event.organizationId(), event.payload().jobId());
            processor.extractAndComplete(prepared);
            idempotency.complete(CONSUMER_NAME, event.eventId());
        } catch (RuntimeException failure) {
            log.error("Knowledge extraction consumer failed eventId={} jobId={} message={}",
                event.eventId(), event.payload().jobId(), failure.getMessage(), failure);
            idempotency.failed(CONSUMER_NAME, event.eventId());
            throw failure;
        }
    }
}
