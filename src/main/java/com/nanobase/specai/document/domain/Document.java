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
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "logical_name", nullable = false, length = 255)
    private String logicalName;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;
    @Column(name = "current_version_id")
    private UUID currentVersionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DocumentStatus status;
    @Column(name = "current_version_number", nullable = false)
    private int currentVersionNumber;
    @Column(name = "included_in_analysis", nullable = false)
    private boolean includedInAnalysis;
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Document() {
    }

    public static Document uploaded(UUID id, UUID organizationId, UUID projectId,
                                    String logicalName, DocumentType type,
                                    boolean includedInAnalysis, String createdBy, Instant now) {
        Document document = new Document();
        document.id = id;
        document.organizationId = organizationId;
        document.projectId = projectId;
        document.logicalName = logicalName;
        document.documentType = type;
        document.status = DocumentStatus.UPLOADED;
        document.currentVersionNumber = 0;
        document.includedInAnalysis = includedInAnalysis;
        document.createdBy = createdBy;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    public void attachVersion(UUID versionId, int versionNumber, Instant now) {
        if (versionNumber != currentVersionNumber + 1) {
            throw new IllegalArgumentException("Document version number must increment by one");
        }
        currentVersionId = versionId;
        currentVersionNumber = versionNumber;
        status = DocumentStatus.UPLOADED;
        updatedAt = now;
    }

    public void processing(DocumentStatus next, UUID versionId, Instant now) {
        if (!versionId.equals(currentVersionId)) {
            return;
        }
        status = next;
        updatedAt = now;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID projectId() { return projectId; }
    public String logicalName() { return logicalName; }
    public DocumentType documentType() { return documentType; }
    public UUID currentVersionId() { return currentVersionId; }
    public DocumentStatus status() { return status; }
    public int currentVersionNumber() { return currentVersionNumber; }
    public boolean includedInAnalysis() { return includedInAnalysis; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
