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
@Table(name = "document")
public class Document {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "tender_project_id", nullable = false, updatable = false)
    private UUID tenderProjectId;
    @Column(nullable = false, length = 255)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DocumentStatus status;
    @Column(name = "current_version", nullable = false)
    private int currentVersion;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Document() {
    }

    public static Document uploaded(UUID id, UUID tenantId, UUID projectId, String name,
                                    DocumentType type, Instant now) {
        Document document = new Document();
        document.id = id;
        document.tenantId = tenantId;
        document.tenderProjectId = projectId;
        document.name = name;
        document.documentType = type;
        document.status = DocumentStatus.UPLOADED;
        document.currentVersion = 1;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID tenderProjectId() { return tenderProjectId; }
    public String name() { return name; }
    public DocumentType documentType() { return documentType; }
    public DocumentStatus status() { return status; }
    public int currentVersion() { return currentVersion; }
    public Instant createdAt() { return createdAt; }

    public void processing(DocumentStatus status, Instant now) {
        if (status == DocumentStatus.UPLOADED) {
            throw new IllegalArgumentException("Processing cannot transition back to UPLOADED");
        }
        this.status = status;
        this.updatedAt = now;
    }
}
