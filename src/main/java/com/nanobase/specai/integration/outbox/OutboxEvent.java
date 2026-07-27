package com.nanobase.specai.integration.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "routing_key", nullable = false, updatable = false, length = 150)
    private String routingKey;
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, UUID tenantId, String routingKey, String payload, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.routingKey = routingKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public String routingKey() { return routingKey; }
    public String payload() { return payload; }
    public void published(Instant now) { this.publishedAt = now; }
    public void failed() { this.attemptCount++; }
}
