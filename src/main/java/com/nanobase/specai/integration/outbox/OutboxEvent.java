package com.nanobase.specai.integration.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;
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
    @JdbcTypeCode(SqlTypes.JSON)
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
    @Column(name = "claimed_by", length = 255)
    private String claimedBy;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error", length = 2000)
    private String lastError;
    @Version
    private long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                       int eventVersion, String routingKey, String payloadJson,
                       UUID organizationId, UUID correlationId, Instant now) {
        this.id = id;
        this.eventId = id;
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
        this.updatedAt = now;
    }

    public void claim(String publisherId, Instant now) {
        if (status != OutboxStatus.PENDING && status != OutboxStatus.FAILED
            && status != OutboxStatus.CLAIMED) {
            throw new IllegalStateException("Only retryable outbox events can be claimed");
        }
        status = OutboxStatus.CLAIMED;
        claimedBy = publisherId;
        claimedAt = now;
        updatedAt = now;
    }

    public void published(Instant now) {
        requireClaimed();
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
        claimedBy = null;
        claimedAt = null;
        updatedAt = now;
    }

    public void failed(Instant now, String error, int maximumRetries, java.time.Duration delay) {
        requireClaimed();
        retryCount++;
        lastError = truncate(error);
        claimedBy = null;
        claimedAt = null;
        updatedAt = now;
        if (retryCount >= maximumRetries) {
            status = OutboxStatus.DEAD;
            return;
        }
        status = OutboxStatus.FAILED;
        nextAttemptAt = now.plus(delay);
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown publish failure";
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public String routingKey() { return routingKey; }
    public String payloadJson() { return payloadJson; }
    public OutboxStatus status() { return status; }
    public int retryCount() { return retryCount; }
    public String claimedBy() { return claimedBy; }
    public Instant claimedAt() { return claimedAt; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public long version() { return version; }

    private void requireClaimed() {
        if (status != OutboxStatus.CLAIMED) {
            throw new IllegalStateException("Outbox event is not claimed");
        }
    }
}
