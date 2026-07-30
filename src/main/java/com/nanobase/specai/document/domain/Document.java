package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
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
    @Column(name = "effective_date")
    private LocalDate effectiveDate;
    @Column(name = "publication_date")
    private LocalDate publicationDate;
    @Column(name = "is_authoritative", nullable = false)
    private boolean authoritative = true;
    @Column(name = "supersedes_document_id")
    private UUID supersedesDocumentId;
    @Column(name = "superseded_by_document_id")
    private UUID supersededByDocumentId;
    @Column(name = "document_priority", nullable = false)
    private int documentPriority = 100;
    @Column(name = "source_institution", length = 200)
    private String sourceInstitution;
    @Column(name = "analysis_status", nullable = false, length = 50)
    private String analysisStatus = "NOT_STARTED";
    @Column(name = "content_checksum", length = 64)
    private String contentChecksum;
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

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
        document.authoritative = true;
        document.documentPriority = priorityFor(type);
        document.analysisStatus = "NOT_STARTED";
        document.createdBy = createdBy;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    public void applyHierarchy(LocalDate effectiveDate, LocalDate publicationDate,
                               boolean authoritative, UUID supersedesDocumentId,
                               int documentPriority, String sourceInstitution, Instant now) {
        this.effectiveDate = effectiveDate;
        this.publicationDate = publicationDate;
        this.authoritative = authoritative;
        this.supersedesDocumentId = supersedesDocumentId;
        this.documentPriority = documentPriority;
        this.sourceInstitution = sourceInstitution;
        this.updatedAt = now;
    }

    public void markSupersededBy(UUID successorId, Instant now) {
        this.supersededByDocumentId = successorId;
        this.authoritative = false;
        this.updatedAt = now;
    }

    public void analysisStatus(String analysisStatus, Instant now) {
        this.analysisStatus = analysisStatus;
        this.updatedAt = now;
    }

    public void contentChecksum(String checksum) {
        this.contentChecksum = checksum;
    }

    private static int priorityFor(DocumentType type) {
        return switch (type) {
            case AMENDMENT, ADDENDUM -> 10;
            case OFFICIAL_CLARIFICATION, QUESTION_RESPONSE -> 20;
            case DRAFT_CONTRACT -> 30;
            case ADMINISTRATIVE_SPECIFICATION, TECHNICAL_SPECIFICATION -> 40;
            case ANNEX -> 50;
            default -> 100;
        };
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
    public LocalDate effectiveDate() { return effectiveDate; }
    public LocalDate publicationDate() { return publicationDate; }
    public boolean authoritative() { return authoritative; }
    public UUID supersedesDocumentId() { return supersedesDocumentId; }
    public UUID supersededByDocumentId() { return supersededByDocumentId; }
    public int documentPriority() { return documentPriority; }
    public String sourceInstitution() { return sourceInstitution; }
    public String analysisStatus() { return analysisStatus; }
    public String contentChecksum() { return contentChecksum; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version == null ? 0L : version; }
}
