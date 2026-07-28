package com.nanobase.specai.risk.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.document.integration.DocumentEvents.EventEnvelope;
import com.nanobase.specai.integration.outbox.ConsumerIdempotencyService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.risk.application.RiskAnalysisProcessor;
import com.nanobase.specai.risk.integration.RiskEvents.RiskAnalysisRequested;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RiskAnalysisConsumer {
    static final String CONSUMER_NAME = "risk-analysis-consumer-v1";
    private static final TypeReference<EventEnvelope<RiskAnalysisRequested>> EVENT_TYPE =
        new TypeReference<>() { };

    private final ObjectMapper mapper;
    private final ConsumerIdempotencyService idempotency;
    private final RiskAnalysisProcessor processor;

    public RiskAnalysisConsumer(ObjectMapper mapper,
                                ConsumerIdempotencyService idempotency,
                                RiskAnalysisProcessor processor) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.processor = processor;
    }

    @RabbitListener(queues = RabbitConfiguration.RISK_ANALYSIS_QUEUE)
    public void consume(Message message) throws Exception {
        EventEnvelope<RiskAnalysisRequested> event =
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

    private void validate(EventEnvelope<RiskAnalysisRequested> event) {
        if (event == null || event.eventId() == null || event.organizationId() == null
            || event.correlationId() == null || event.payload() == null
            || event.payload().jobId() == null || event.payload().projectId() == null
            || event.payload().riskAnalysisProfileId() == null) {
            throw new IllegalArgumentException("Invalid risk analysis event envelope");
        }
    }
}
