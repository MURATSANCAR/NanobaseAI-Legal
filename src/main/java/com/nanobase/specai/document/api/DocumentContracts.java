package com.nanobase.specai.document.api;

import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import com.nanobase.specai.document.domain.DocumentProcessingJob;
import com.nanobase.specai.document.domain.DocumentPage;
import com.nanobase.specai.document.domain.DocumentTable;
import com.nanobase.specai.document.domain.NormalizedBoundingBox;
import com.nanobase.specai.document.domain.ParserWarning;
import org.springframework.data.domain.Page;

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
        DocumentVersionResponse currentVersion,
        ProcessingJobResponse currentJob
    ) {
        public static DocumentResponse from(Document document, DocumentVersion currentVersion) {
            return from(document, currentVersion, null);
        }

        public static DocumentResponse from(Document document, DocumentVersion currentVersion,
                                            DocumentProcessingJob currentJob) {
            return new DocumentResponse(document.id(), document.projectId(),
                document.logicalName(), document.documentType(), document.currentVersionId(),
                document.currentVersionNumber(), document.status(),
                document.includedInAnalysis(), document.createdBy(), document.createdAt(),
                document.updatedAt(), document.version(),
                currentVersion == null ? null : DocumentVersionResponse.from(currentVersion),
                currentJob == null ? null : ProcessingJobResponse.from(currentJob));
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

    public record ClauseResponse(
        UUID id,
        UUID parentId,
        String number,
        String title,
        String rawText,
        String normalizedText,
        String clauseType,
        int pageStart,
        int pageEnd,
        List<NormalizedBoundingBox> boundingBoxes,
        String contentHash,
        int sortOrder
    ) {
    }

    public record DocumentPageResponse(
        UUID id, int pageNumber, BigDecimal width, BigDecimal height, int rotation,
        String rawText, String normalizedText, BigDecimal textQualityScore,
        String thumbnailObjectKey
    ) {
        public static DocumentPageResponse from(DocumentPage page) {
            return new DocumentPageResponse(page.id(), page.pageNumber(), page.width(),
                page.height(), page.rotation(), page.rawText(), page.normalizedText(),
                page.textQualityScore(), page.thumbnailObjectKey());
        }
    }

    public record DocumentTableResponse(
        UUID id, int pageStart, int pageEnd, String caption, String markdownContent,
        Map<String, Object> structuredContent,
        List<NormalizedBoundingBox> boundingBoxes, String contentHash
    ) {
    }

    public record ParserWarningResponse(
        UUID id, String warningCode, String severity, String message,
        Integer pageNumber, Map<String, Object> metadata
    ) {
    }

    public record ProcessingJobResponse(
        UUID id,
        UUID documentVersionId,
        String provider,
        DocumentStatus status,
        DocumentStatus currentStage,
        int progress,
        int attemptCount,
        String externalReference,
        UUID correlationId,
        Instant startedAt,
        Instant completedAt,
        Instant heartbeatAt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        List<ParserWarningResponse> warnings
    ) {
        public static ProcessingJobResponse from(DocumentProcessingJob job) {
            return from(job, List.of());
        }

        public static ProcessingJobResponse from(
            DocumentProcessingJob job, List<ParserWarningResponse> warnings) {
            return new ProcessingJobResponse(job.id(), job.documentVersionId(),
                job.provider(), job.status(), job.currentStage(), job.progress(),
                job.attemptCount(), job.externalReference(), job.correlationId(),
                job.startedAt(), job.completedAt(), job.heartbeatAt(), job.errorCode(),
                job.errorMessage(), job.createdAt(), job.updatedAt(), warnings);
        }
    }

    public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ProcessingEvent(
        UUID eventId,
        UUID jobId,
        UUID documentId,
        UUID documentVersionId,
        DocumentStatus stage,
        int progress,
        String message,
        Instant occurredAt
    ) {
    }
}
