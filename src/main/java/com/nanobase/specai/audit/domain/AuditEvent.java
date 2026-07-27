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
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "user_id", nullable = false, updatable = false, length = 255)
    private String userId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;
    @Column(name = "entity_type", nullable = false, updatable = false, length = 100)
    private String entityType;
    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;
    @Column(name = "ip_address", updatable = false, length = 64)
    private String ipAddress;
    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;
    @Column(name = "before_json", updatable = false, columnDefinition = "jsonb")
    private String beforeJson;
    @Column(name = "after_json", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String afterJson;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, UUID organizationId, String userId, String eventType,
                      String entityType, UUID entityId, String ipAddress, String userAgent,
                      String beforeJson, String afterJson, UUID correlationId, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.userId = userId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public String userId() { return userId; }
    public String eventType() { return eventType; }
    public String entityType() { return entityType; }
    public UUID entityId() { return entityId; }
    public String ipAddress() { return ipAddress; }
    public String userAgent() { return userAgent; }
    public String beforeJson() { return beforeJson; }
    public String afterJson() { return afterJson; }
    public UUID correlationId() { return correlationId; }
    public Instant createdAt() { return createdAt; }
}
