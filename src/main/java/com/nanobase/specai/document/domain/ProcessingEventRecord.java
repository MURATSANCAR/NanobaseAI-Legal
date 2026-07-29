package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_event")
public class ProcessingEventRecord {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "processing_job_id", nullable = false, updatable = false)
    private UUID processingJobId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(nullable = false, updatable = false, length = 50)
    private String stage;
    @Column(nullable = false, updatable = false)
    private int progress;
    @Column(nullable = false, updatable = false, length = 500)
    private String message;
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, updatable = false,
        columnDefinition = "jsonb")
    private String metadataJson;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ProcessingEventRecord() {
    }

    public ProcessingEventRecord(UUID id, UUID organizationId, UUID processingJobId,
                                 UUID documentVersionId, String stage, int progress,
                                 String message, String eventType, String metadataJson,
                                 Instant occurredAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.processingJobId = processingJobId;
        this.documentVersionId = documentVersionId;
        this.stage = stage;
        this.progress = progress;
        this.message = message;
        this.eventType = eventType;
        this.metadataJson = metadataJson;
        this.occurredAt = occurredAt;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID processingJobId() { return processingJobId; }
    public UUID documentVersionId() { return documentVersionId; }
    public String stage() { return stage; }
    public int progress() { return progress; }
    public String message() { return message; }
    public String eventType() { return eventType; }
    public String metadataJson() { return metadataJson; }
    public Instant occurredAt() { return occurredAt; }
}
