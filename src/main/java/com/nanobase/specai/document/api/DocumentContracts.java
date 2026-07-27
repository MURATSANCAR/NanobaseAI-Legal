package com.nanobase.specai.document.api;

import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class DocumentContracts {
    private DocumentContracts() {
    }

    public record DocumentResponse(
        UUID id,
        UUID projectId,
        String logicalName,
        DocumentType documentType,
        UUID currentVersionId,
        int currentVersionNumber,
        DocumentStatus status,
        boolean includedInAnalysis,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        long version,
        DocumentVersionResponse currentVersion
    ) {
        public static DocumentResponse from(Document document, DocumentVersion currentVersion) {
            return new DocumentResponse(document.id(), document.projectId(),
                document.logicalName(), document.documentType(), document.currentVersionId(),
                document.currentVersionNumber(), document.status(),
                document.includedInAnalysis(), document.createdBy(), document.createdAt(),
                document.updatedAt(), document.version(),
                currentVersion == null ? null : DocumentVersionResponse.from(currentVersion));
        }
    }

    public record DocumentVersionResponse(
        UUID id,
        int versionNumber,
        String originalFileName,
        String mimeType,
        long fileSize,
        String sha256,
        Integer pageCount,
        String language,
        boolean ocrRequired,
        BigDecimal ocrQualityScore,
        DocumentStatus processingStatus,
        String uploadedBy,
        Instant uploadedAt,
        Instant processingStartedAt,
        Instant processingCompletedAt,
        String errorCode,
        String errorMessage,
        long version
    ) {
        public static DocumentVersionResponse from(DocumentVersion documentVersion) {
            return new DocumentVersionResponse(documentVersion.id(),
                documentVersion.versionNumber(), documentVersion.originalFileName(),
                documentVersion.mimeType(), documentVersion.fileSize(), documentVersion.sha256(),
                documentVersion.pageCount(), documentVersion.language(),
                documentVersion.ocrRequired(), documentVersion.ocrQualityScore(),
                documentVersion.processingStatus(), documentVersion.uploadedBy(),
                documentVersion.uploadedAt(), documentVersion.processingStartedAt(),
                documentVersion.processingCompletedAt(), documentVersion.errorCode(),
                documentVersion.errorMessage(), documentVersion.version());
        }
    }

    public record DownloadUrlResponse(String url, int expiresInSeconds) {
    }

    public record ClauseResponse(UUID id, UUID parentId, String number, String title,
                                 String sourceText, int pageNumber, int sortOrder) {
    }

    public record ProcessingEvent(
        UUID documentId,
        UUID documentVersionId,
        DocumentStatus stage,
        int progress,
        String message,
        Instant occurredAt
    ) {
    }
}
