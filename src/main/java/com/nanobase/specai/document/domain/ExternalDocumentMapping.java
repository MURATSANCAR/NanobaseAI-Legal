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

    public enum Provider {
        OPENCONTRACTS, DOCLING, MINERU, CUSTOM
    }

    public enum SyncStatus {
        PENDING, SYNCING, SYNCED, FAILED
    }
}
