package com.nanobase.specai.integration.outbox;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxStore {
    private final OutboxEventRepository events;
    private final Clock clock = Clock.systemUTC();

    public OutboxStore(OutboxEventRepository events) {
        this.events = events;
    }

    @Transactional
    public List<OutboxEvent> claimBatch() {
        List<OutboxEvent> claimed = events.lockPending(clock.instant());
        claimed.forEach(OutboxEvent::claim);
        return List.copyOf(claimed);
    }

    @Transactional
    public void published(UUID eventId) {
        events.findById(eventId).ifPresent(event -> event.published(clock.instant()));
    }

    @Transactional
    public void failed(UUID eventId, String error) {
        events.findById(eventId).ifPresent(event -> event.failed(clock.instant(), error));
    }
}
