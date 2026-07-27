package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_document_mapping")
public class ExternalDocumentMapping {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "document_version_id", nullable = false, updatable = false)
    private UUID documentVersionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private Provider provider;
    @Column(name = "external_corpus_id", length = 255)
    private String externalCorpusId;
    @Column(name = "external_document_id", length = 255)
    private String externalDocumentId;
    @Column(name = "external_version", length = 100)
    private String externalVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 50)
    private SyncStatus syncStatus;
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExternalDocumentMapping() {
    }

    public static ExternalDocumentMapping pending(
        UUID id, UUID organizationId, UUID documentVersionId, Provider provider,
        String externalCorpusId, Instant now) {
        ExternalDocumentMapping mapping = new ExternalDocumentMapping();
        mapping.id = id;
        mapping.organizationId = organizationId;
        mapping.documentVersionId = documentVersionId;
        mapping.provider = provider;
        mapping.externalCorpusId = externalCorpusId;
        mapping.syncStatus = SyncStatus.PENDING;
        mapping.createdAt = now;
        mapping.updatedAt = now;
        return mapping;
    }

    public void submitted(String externalDocumentId, String externalVersion, Instant now) {
        this.externalDocumentId = externalDocumentId;
        this.externalVersion = externalVersion;
        this.syncStatus = SyncStatus.SYNCING;
        this.errorCode = null;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void synced(Instant now) {
        syncStatus = SyncStatus.SYNCED;
        lastSyncedAt = now;
        updatedAt = now;
    }

    public void failed(String code, String message, Instant now) {
        syncStatus = SyncStatus.FAILED;
        errorCode = code;
        errorMessage = message == null || message.length() <= 2000
            ? message : message.substring(0, 2000);
        updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID documentVersionId() { return documentVersionId; }
    public Provider provider() { return provider; }
    public String externalCorpusId() { return externalCorpusId; }
    public String externalDocumentId() { return externalDocumentId; }
    public String externalVersion() { return externalVersion; }
    public SyncStatus syncStatus() { return syncStatus; }

    public enum Provider {
        OPENCONTRACTS, DOCLING, MINERU, CUSTOM
    }

    public enum SyncStatus {
        PENDING, SYNCING, SYNCED, FAILED
    }
}
