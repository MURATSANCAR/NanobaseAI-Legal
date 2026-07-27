package com.nanobase.specai.integration.outbox;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    @Modifying
    @Query(value = """
        insert into processed_message (
            id, consumer_name, event_id, processed_at, result_status
        ) values (
            :id, :consumerName, :eventId, :now, 'PROCESSING'
        )
        on conflict (consumer_name, event_id) do update
        set id = excluded.id,
            processed_at = excluded.processed_at,
            result_status = 'PROCESSING'
        where processed_message.result_status = 'FAILED'
           or (
               processed_message.result_status = 'PROCESSING'
               and processed_message.processed_at <= :staleBefore
           )
        """, nativeQuery = true)
    int claim(@Param("id") UUID id,
              @Param("consumerName") String consumerName,
              @Param("eventId") UUID eventId,
              @Param("now") Instant now,
              @Param("staleBefore") Instant staleBefore);

    @Modifying
    @Query(value = """
        update processed_message
        set result_status = :resultStatus, processed_at = :now
        where consumer_name = :consumerName and event_id = :eventId
        """, nativeQuery = true)
    int complete(@Param("consumerName") String consumerName,
                 @Param("eventId") UUID eventId,
                 @Param("resultStatus") String resultStatus,
                 @Param("now") Instant now);
}
