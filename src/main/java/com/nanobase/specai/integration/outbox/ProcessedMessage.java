package com.nanobase.specai.integration.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_message")
public class ProcessedMessage {
    @Id
    private UUID id;
    @Column(name = "consumer_name", nullable = false, updatable = false, length = 150)
    private String consumerName;
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
    @Column(name = "result_status", nullable = false, length = 30)
    private String resultStatus;

    protected ProcessedMessage() {
    }
}
