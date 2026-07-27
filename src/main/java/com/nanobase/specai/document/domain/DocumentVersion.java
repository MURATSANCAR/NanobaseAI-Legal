package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version")
public class DocumentVersion {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
    @Column(name = "object_storage_key", nullable = false, updatable = false, length = 1024)
    private String objectKey;
    @Column(name = "original_file_name", nullable = false, updatable = false, length = 255)
    private String originalFilename;
    @Column(name = "mime_type", nullable = false, updatable = false, length = 150)
    private String mediaType;
    @Column(name = "file_size", nullable = false, updatable = false)
    private long sizeBytes;
    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentVersion() {
    }

    public DocumentVersion(UUID id, UUID tenantId, UUID documentId, int versionNumber,
                           String objectKey, String originalFilename, String mediaType,
                           long sizeBytes, String sha256, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID documentId() { return documentId; }
    public String objectKey() { return objectKey; }
}
