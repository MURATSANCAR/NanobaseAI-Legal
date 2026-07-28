package com.nanobase.specai.integration.outbox;

import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxStore {
    private final OutboxEventRepository events;
    private final Clock clock;
    private final String publisherId;
    private final int batchSize;
    private final int maximumRetries;
    private final Duration claimTimeout;
    private final Duration baseBackoff;
    private final Duration maximumBackoff;

    @Autowired
    public OutboxStore(OutboxEventRepository events,
                       @Value("${specai.outbox.publisher-id:${HOSTNAME:local}-${random.uuid}}")
                       String publisherId,
                       @Value("${specai.outbox.batch-size:50}") int batchSize,
                       @Value("${specai.outbox.maximum-retries:10}") int maximumRetries,
                       @Value("${specai.outbox.claim-timeout:PT2M}") Duration claimTimeout,
                       @Value("${specai.outbox.base-backoff:PT1S}") Duration baseBackoff,
                       @Value("${specai.outbox.maximum-backoff:PT5M}") Duration maximumBackoff) {
        this(events, publisherId, batchSize, maximumRetries, claimTimeout, baseBackoff,
            maximumBackoff, Clock.systemUTC());
    }

    OutboxStore(OutboxEventRepository events, String publisherId, int batchSize,
                int maximumRetries, Duration claimTimeout, Duration baseBackoff,
                Duration maximumBackoff, Clock clock) {
        this.events = events;
        this.publisherId = publisherId;
        this.batchSize = batchSize;
        this.maximumRetries = maximumRetries;
        this.claimTimeout = claimTimeout;
        this.baseBackoff = baseBackoff;
        this.maximumBackoff = maximumBackoff;
        this.clock = clock;
    }

    @Transactional
    public ClaimBatch claimBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> claimed = new ArrayList<>(batchSize);
        List<OutboxEvent> reclaimed = events.lockExpiredClaims(
            now.minus(claimTimeout), batchSize);
        claimed.addAll(reclaimed);
        int remaining = batchSize - claimed.size();
        if (remaining > 0) {
            claimed.addAll(events.lockPending(now, remaining));
        }
        remaining = batchSize - claimed.size();
        if (remaining > 0) {
            claimed.addAll(events.lockRetryable(now, remaining));
        }
        claimed.forEach(event -> event.claim(publisherId, now));
        return new ClaimBatch(List.copyOf(claimed), reclaimed.size());
    }

    @Transactional
    public void published(UUID eventId) {
        events.findById(eventId).ifPresent(event -> event.published(clock.instant()));
    }

    @Transactional
    public OutboxStatus failed(UUID eventId, String error) {
        return events.findById(eventId).map(event -> {
            event.failed(clock.instant(), error, maximumRetries,
                retryDelay(event.retryCount() + 1));
            return event.status();
        }).orElse(OutboxStatus.DEAD);
    }

    Duration retryDelay(int attempt) {
        long exponent = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        long cappedMillis = Math.min(maximumBackoff.toMillis(),
            Math.multiplyExact(baseBackoff.toMillis(), exponent));
        long jitterBound = Math.max(1L, cappedMillis / 4L);
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound);
        return Duration.ofMillis(Math.min(maximumBackoff.toMillis(), cappedMillis + jitter));
    }

    public record ClaimBatch(List<OutboxEvent> events, int reclaimedCount) {
    }
}
