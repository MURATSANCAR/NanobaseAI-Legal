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
        limit 50
        """, nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("now") Instant now);

    long countByStatus(OutboxStatus status);
}
