package com.nanobase.specai.integration.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
        select *
        from outbox_event
        where status = 'PENDING' and next_attempt_at <= :now
        order by created_at
        for update skip locked
        limit :batchSize
        """, nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("now") Instant now,
                                  @Param("batchSize") int batchSize);

    @Query(value = """
        select *
        from outbox_event
        where status = 'FAILED' and next_attempt_at <= :now
        order by next_attempt_at, created_at
        for update skip locked
        limit :batchSize
        """, nativeQuery = true)
    List<OutboxEvent> lockRetryable(@Param("now") Instant now,
                                    @Param("batchSize") int batchSize);

    @Query(value = """
        select *
        from outbox_event
        where status = 'CLAIMED' and claimed_at <= :claimExpiredBefore
        order by claimed_at, created_at
        for update skip locked
        limit :batchSize
        """, nativeQuery = true)
    List<OutboxEvent> lockExpiredClaims(
        @Param("claimExpiredBefore") Instant claimExpiredBefore,
        @Param("batchSize") int batchSize);

    long countByStatus(OutboxStatus status);
}
