package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_processing_job")
public class DocumentProcessingJob {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Column(length = 50)
    private String provider;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DocumentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 50)
    private DocumentStatus currentStage;
    @Column(nullable = false)
    private int progress;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "external_reference", length = 255)
    private String externalReference;
    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected DocumentProcessingJob() {
    }

    public static DocumentProcessingJob queued(
        UUID id, UUID organizationId, UUID projectId, UUID documentId,
        UUID documentVersionId, UUID correlationId, Instant now) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.id = id;
        job.organizationId = organizationId;
        job.projectId = projectId;
        job.documentId = documentId;
        job.documentVersionId = documentVersionId;
        job.status = DocumentStatus.UPLOADED;
        job.currentStage = DocumentStatus.UPLOADED;
        job.correlationId = correlationId;
        job.createdAt = now;
        job.updatedAt = now;
        job.heartbeatAt = now;
        return job;
    }

    public void transition(DocumentStatus next, int nextProgress, String message,
                           String errorCode, String errorMessage, Instant now) {
        ProcessingStateMachine.requireTransition(status, next);
        status = next;
        currentStage = next;
        progress = Math.max(progress, Math.min(100, Math.max(0, nextProgress)));
        if (startedAt == null && next != DocumentStatus.UPLOADED) {
            startedAt = now;
        }
        if (next.terminal()) {
            completedAt = now;
            progress = 100;
        }
        this.errorCode = next == DocumentStatus.FAILED
            || next == DocumentStatus.MANUAL_REVIEW_REQUIRED ? errorCode : null;
        this.errorMessage = next == DocumentStatus.FAILED
            || next == DocumentStatus.MANUAL_REVIEW_REQUIRED
            ? truncate(errorMessage, 2000) : null;
        heartbeatAt = now;
        updatedAt = now;
    }

    public void routed(String provider, Instant now) {
        this.provider = provider;
        updatedAt = now;
    }

    public void submitted(String externalReference, Instant now) {
        this.externalReference = externalReference;
        attemptCount++;
        heartbeatAt = now;
        updatedAt = now;
    }

    public void heartbeat(Instant now) {
        heartbeatAt = now;
        updatedAt = now;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID projectId() { return projectId; }
    public UUID documentId() { return documentId; }
    public UUID documentVersionId() { return documentVersionId; }
    public String provider() { return provider; }
    public DocumentStatus status() { return status; }
    public DocumentStatus currentStage() { return currentStage; }
    public int progress() { return progress; }
    public int attemptCount() { return attemptCount; }
    public String externalReference() { return externalReference; }
    public UUID correlationId() { return correlationId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant heartbeatAt() { return heartbeatAt; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
