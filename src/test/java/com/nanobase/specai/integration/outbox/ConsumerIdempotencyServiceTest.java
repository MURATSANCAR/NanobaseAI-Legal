package com.nanobase.specai.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsumerIdempotencyServiceTest {
    @Test
    void duplicateMessageIsNotClaimedTwiceAndCompletionIsRecorded() {
        ProcessedMessageRepository repository = mock(ProcessedMessageRepository.class);
        UUID eventId = UUID.randomUUID();
        when(repository.claim(any(), eq("consumer"), eq(eventId), any(), any()))
            .thenReturn(1, 0);
        ConsumerIdempotencyService service = new ConsumerIdempotencyService(
            repository, Duration.ofMinutes(30), Clock.fixed(
                Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        assertThat(service.claim("consumer", eventId)).isTrue();
        assertThat(service.claim("consumer", eventId)).isFalse();
        service.complete("consumer", eventId);

        verify(repository).complete(eq("consumer"), eq(eventId),
            eq("PROCESSED"), any());
    }
}
