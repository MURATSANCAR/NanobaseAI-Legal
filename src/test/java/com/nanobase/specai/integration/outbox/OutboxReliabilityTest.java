package com.nanobase.specai.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxReliabilityTest {
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void failedPublishUsesRetryStateThenMovesToDead() {
        OutboxEvent event = event();
        event.claim("publisher-a", NOW);
        event.failed(NOW, "broker unavailable", 2, Duration.ofSeconds(3));
        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(3));
        event.claim("publisher-b", NOW.plusSeconds(3));
        event.failed(NOW.plusSeconds(3), "broker unavailable", 2,
            Duration.ofSeconds(5));
        assertThat(event.status()).isEqualTo(OutboxStatus.DEAD);
    }

    @Test
    void expiredClaimIsReclaimedByAnotherPublisher() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEvent event = event();
        event.claim("crashed-publisher", NOW.minusSeconds(300));
        when(repository.lockExpiredClaims(any(), anyInt())).thenReturn(List.of(event));
        when(repository.lockPending(any(), anyInt())).thenReturn(List.of());
        when(repository.lockRetryable(any(), anyInt())).thenReturn(List.of());
        OutboxStore store = new OutboxStore(repository, "publisher-b", 10, 3,
            Duration.ofMinutes(2), Duration.ofSeconds(1), Duration.ofMinutes(5),
            Clock.fixed(NOW, ZoneOffset.UTC));

        OutboxStore.ClaimBatch batch = store.claimBatch();

        assertThat(batch.reclaimedCount()).isOne();
        assertThat(batch.events()).containsExactly(event);
        assertThat(event.claimedBy()).isEqualTo("publisher-b");
    }

    private OutboxEvent event() {
        UUID id = UUID.randomUUID();
        return new OutboxEvent(id, "Document", UUID.randomUUID(), "Requested", 1,
            "document.processing.requested.v1", "{}", UUID.randomUUID(),
            UUID.randomUUID(), NOW.minusSeconds(600));
    }
}
