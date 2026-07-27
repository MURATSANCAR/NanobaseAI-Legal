package com.nanobase.specai.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "document_version")
public class DocumentVersion {
    private static final Map<DocumentStatus, Set<DocumentStatus>> TRANSITIONS = Map.of(
        DocumentStatus.UPLOADED, EnumSet.of(DocumentStatus.VIRUS_SCANNING,
            DocumentStatus.CLASSIFYING, DocumentStatus.PARSING, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED),
        DocumentStatus.VIRUS_SCANNING, EnumSet.of(DocumentStatus.CLASSIFYING,
            DocumentStatus.PARSING, DocumentStatus.FAILED, DocumentStatus.MANUAL_REVIEW_REQUIRED),
        DocumentStatus.CLASSIFYING, EnumSet.of(DocumentStatus.PARSING,
            DocumentStatus.OCR_PROCESSING, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED),
        DocumentStatus.PARSING, EnumSet.of(DocumentStatus.OCR_PROCESSING,
            DocumentStatus.STRUCTURE_DETECTION, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED),
        DocumentStatus.OCR_PROCESSING, EnumSet.of(DocumentStatus.STRUCTURE_DETECTION,
            DocumentStatus.FAILED, DocumentStatus.MANUAL_REVIEW_REQUIRED),
        DocumentStatus.STRUCTURE_DETECTION, EnumSet.of(DocumentStatus.INDEXING,
            DocumentStatus.FAILED, DocumentStatus.MANUAL_REVIEW_REQUIRED),
        DocumentStatus.INDEXING, EnumSet.of(DocumentStatus.READY, DocumentStatus.FAILED,
            DocumentStatus.MANUAL_REVIEW_REQUIRED)
    );

    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;
    @Column(name = "object_storage_key", nullable = false, updatable = false, length = 1024)
    private String objectStorageKey;
    @Column(name = "original_file_name", nullable = false, updatable = false, length = 255)
    private String originalFileName;
    @Column(name = "mime_type", nullable = false, updatable = false, length = 150)
    private String mimeType;
    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;
    @Column(name = "sha256", nullable = false, updatable = false, length = 64)
    private String sha256;
    @Column(name = "page_count")
    private Integer pageCount;
    @Column(length = 20)
    private String language;
    @Column(name = "ocr_required", nullable = false)
    private boolean ocrRequired;
    @Column(name = "ocr_quality_score", precision = 5, scale = 2)
    private BigDecimal ocrQualityScore;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    private DocumentStatus processingStatus;
    @Column(name = "uploaded_by", nullable = false, updatable = false, length = 255)
    private String uploadedBy;
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
    @Column(name = "processing_started_at")
    private Instant processingStartedAt;
    @Column(name = "processing_completed_at")
    private Instant processingCompletedAt;
    @Column(name = "error_code", length = 100)
    private String errorCode;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Version
    private long version;

    protected DocumentVersion() {
    }

    public DocumentVersion(UUID id, UUID organizationId, UUID documentId, int versionNumber,
                           String objectStorageKey, String originalFileName, String mimeType,
                           long fileSize, String sha256, String uploadedBy, Instant uploadedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.objectStorageKey = objectStorageKey;
        this.originalFileName = originalFileName;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.processingStatus = DocumentStatus.UPLOADED;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public void transition(DocumentStatus next, Instant now, String errorCode, String errorMessage) {
        if (next == processingStatus) {
            return;
        }
        if (!TRANSITIONS.getOrDefault(processingStatus, Set.of()).contains(next)) {
            throw new IllegalStateException(
                "Invalid processing transition from %s to %s".formatted(processingStatus, next));
        }
        if (processingStartedAt == null && next != DocumentStatus.UPLOADED) {
            processingStartedAt = now;
        }
        processingStatus = next;
        this.errorCode = next == DocumentStatus.FAILED ? errorCode : null;
        this.errorMessage = next == DocumentStatus.FAILED ? truncate(errorMessage, 2000) : null;
        if (next.terminal()) {
            processingCompletedAt = now;
        }
    }

    public void reprocess() {
        processingStatus = DocumentStatus.UPLOADED;
        processingStartedAt = null;
        processingCompletedAt = null;
        errorCode = null;
        errorMessage = null;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID documentId() { return documentId; }
    public int versionNumber() { return versionNumber; }
    public String objectStorageKey() { return objectStorageKey; }
    public String originalFileName() { return originalFileName; }
    public String mimeType() { return mimeType; }
    public long fileSize() { return fileSize; }
    public String sha256() { return sha256; }
    public Integer pageCount() { return pageCount; }
    public String language() { return language; }
    public boolean ocrRequired() { return ocrRequired; }
    public BigDecimal ocrQualityScore() { return ocrQualityScore; }
    public DocumentStatus processingStatus() { return processingStatus; }
    public String uploadedBy() { return uploadedBy; }
    public Instant uploadedAt() { return uploadedAt; }
    public Instant processingStartedAt() { return processingStartedAt; }
    public Instant processingCompletedAt() { return processingCompletedAt; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public long version() { return version; }
}
