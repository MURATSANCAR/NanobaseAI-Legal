package com.nanobase.specai.integration.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    private static final int MAX_RETRIES = 10;

    @Id
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;
    @Column(name = "event_version", nullable = false, updatable = false)
    private int eventVersion;
    @Column(name = "routing_key", nullable = false, updatable = false, length = 150)
    private String routingKey;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payloadJson;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxStatus status;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error", length = 2000)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                       int eventVersion, String routingKey, String payloadJson,
                       UUID organizationId, UUID correlationId, Instant now) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.organizationId = organizationId;
        this.correlationId = correlationId;
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public void claim() {
        status = OutboxStatus.PROCESSING;
    }

    public void published(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void failed(Instant now, String error) {
        retryCount++;
        lastError = truncate(error);
        if (retryCount >= MAX_RETRIES) {
            status = OutboxStatus.FAILED;
            return;
        }
        status = OutboxStatus.PENDING;
        nextAttemptAt = now.plus(backoff(retryCount));
    }

    private Duration backoff(int attempt) {
        long seconds = Math.min(300, 1L << Math.min(attempt, 8));
        return Duration.ofSeconds(seconds);
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown publish failure";
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public UUID id() { return id; }
    public String routingKey() { return routingKey; }
    public String payloadJson() { return payloadJson; }
    public OutboxStatus status() { return status; }
    public int retryCount() { return retryCount; }
}
