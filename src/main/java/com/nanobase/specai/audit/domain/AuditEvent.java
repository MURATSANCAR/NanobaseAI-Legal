package com.nanobase.specai.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 100)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, UUID tenantId, String actorId, String eventType, String aggregateType,
                      UUID aggregateId, Instant occurredAt, String payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }
}
