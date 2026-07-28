package com.nanobase.specai.integration.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerIdempotencyService {
    private final ProcessedMessageRepository messages;
    private final Duration processingTimeout;
    private final Clock clock;

    public ConsumerIdempotencyService(
        ProcessedMessageRepository messages,
        @Value("${specai.messaging.idempotency-processing-timeout:PT30M}")
        Duration processingTimeout,
        Clock clock) {
        this.messages = messages;
        this.processingTimeout = processingTimeout;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String consumerName, UUID eventId) {
        Instant now = clock.instant();
        return messages.claim(UUID.randomUUID(), consumerName, eventId, now,
            now.minus(processingTimeout)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String consumerName, UUID eventId) {
        messages.complete(consumerName, eventId, "PROCESSED", clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String consumerName, UUID eventId) {
        messages.complete(consumerName, eventId, "FAILED", clock.instant());
    }
}
