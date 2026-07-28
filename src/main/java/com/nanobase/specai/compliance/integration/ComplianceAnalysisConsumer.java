package com.nanobase.specai.compliance.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceAnalysisProcessor;
import com.nanobase.specai.compliance.application.ComplianceJobService.ComplianceRequested;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ComplianceAnalysisConsumer {
    static final String CONSUMER_NAME = "compliance-analysis-consumer-v1";
    private static final TypeReference<EventEnvelope<ComplianceRequested>> EVENT_TYPE =
        new TypeReference<>() { };
    private final ObjectMapper mapper;
    private final ConsumerIdempotencyService idempotency;
    private final ComplianceAnalysisProcessor processor;

    public ComplianceAnalysisConsumer(ObjectMapper mapper,
                                      ConsumerIdempotencyService idempotency,
                                      ComplianceAnalysisProcessor processor) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitConfiguration.COMPLIANCE_QUEUE)
    public void consume(Message message) throws Exception {
        EventEnvelope<ComplianceRequested> event =
            mapper.readValue(message.getBody(), EVENT_TYPE);
        if (event == null || event.eventId() == null || event.organizationId() == null
            || event.payload() == null || event.payload().jobId() == null
            || event.payload().correlationId() == null) {
            throw new IllegalArgumentException("Invalid compliance analysis event");
        }
        if (!idempotency.claim(CONSUMER_NAME, event.eventId())) {
            return;
        }
        try {
            processor.process(event.organizationId(), event.payload().jobId(),
                event.payload().correlationId());
            idempotency.complete(CONSUMER_NAME, event.eventId());
        } catch (RuntimeException failure) {
            idempotency.failed(CONSUMER_NAME, event.eventId());
            throw failure;
        }
    }
}
