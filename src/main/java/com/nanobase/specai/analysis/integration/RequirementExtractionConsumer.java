package com.nanobase.specai.analysis.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.application.RequirementExtractionProcessor;
import com.nanobase.specai.analysis.integration.AnalysisEvents.ExtractionRequested;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RequirementExtractionConsumer {
    static final String CONSUMER_NAME = "requirement-extraction-consumer-v1";
    private static final TypeReference<EventEnvelope<ExtractionRequested>> EVENT_TYPE =
        new TypeReference<>() { };

    private final ObjectMapper mapper;
    private final ConsumerIdempotencyService idempotency;
    private final RequirementExtractionProcessor processor;

    public RequirementExtractionConsumer(ObjectMapper mapper,
                                         ConsumerIdempotencyService idempotency,
                                         RequirementExtractionProcessor processor) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitConfiguration.REQUIREMENT_QUEUE)
    public void consume(Message message) throws Exception {
        EventEnvelope<ExtractionRequested> event =
            mapper.readValue(message.getBody(), EVENT_TYPE);
        validate(event);
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

    private void validate(EventEnvelope<ExtractionRequested> event) {
        if (event == null || event.eventId() == null || event.organizationId() == null
            || event.correlationId() == null || event.payload() == null
            || event.payload().jobId() == null
            || event.payload().analysisProfileId() == null
            || event.payload().documentVersionId() == null) {
            throw new IllegalArgumentException(
                "Invalid requirement extraction event envelope");
        }
    }
}
