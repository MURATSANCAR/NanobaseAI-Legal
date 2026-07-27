package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.NormalizedBoundingBox;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provider-neutral document intelligence boundary. Provider DTOs and SDK types
 * must never cross this interface.
 */
public interface DocumentIntelligencePort {
    ProcessingSubmission submit(DocumentProcessingCommand command);

    ProcessingStatusResult getStatus(ExternalProcessingReference reference);

    DocumentExtractionResult getResult(ExternalProcessingReference reference);

    void cancel(ExternalProcessingReference reference);

    record DocumentProcessingCommand(
        UUID organizationId,
        UUID projectId,
        UUID documentId,
        UUID documentVersionId,
        String objectStorageKey,
        String originalFileName,
        String mimeType,
        String sha256,
        String languageHint,
        boolean ocrRequired,
        UUID correlationId,
        long fileSize,
        boolean annotationSynchronizationRequested
    ) {
    }

    record ProcessingSubmission(
        ExternalProcessingReference reference,
        DocumentStatus status
    ) {
    }

    record ExternalProcessingReference(
        String provider,
        String externalReference,
        UUID organizationId,
        UUID documentVersionId
    ) {
    }

    record ProcessingStatusResult(
        DocumentStatus status,
        String currentStage,
        int progress,
        String message,
        String errorCode
    ) {
        public boolean terminal() {
            return status.terminal();
        }
    }

    record DocumentExtractionResult(
        UUID documentVersionId,
        String provider,
        String providerVersion,
        int pageCount,
        String language,
        double textQualityScore,
        List<ExtractedPage> pages,
        List<ExtractedClause> clauses,
        List<ExtractedTable> tables,
        List<ExtractionWarning> warnings,
        Map<String, Object> metadata
    ) {
        public DocumentExtractionResult {
            pages = pages == null ? List.of() : List.copyOf(pages);
            clauses = clauses == null ? List.of() : List.copyOf(clauses);
            tables = tables == null ? List.of() : List.copyOf(tables);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            if (textQualityScore < 0d || textQualityScore > 1d) {
                throw new IllegalArgumentException("Text quality score must be normalized");
            }
        }
    }

    record ExtractedPage(
        int pageNumber,
        double width,
        double height,
        int rotation,
        String rawText,
        String normalizedText,
        double textQualityScore,
        String thumbnailObjectKey,
        Map<String, Object> metadata
    ) {
    }

    record ExtractedClause(
        String sourceId,
        String parentSourceId,
        String clauseNumber,
        String title,
        String rawText,
        String normalizedText,
        String clauseType,
        int pageStart,
        int pageEnd,
        List<NormalizedBoundingBox> boundingBoxes,
        String contentHash,
        int sortOrder,
        Map<String, Object> metadata
    ) {
    }

    record ExtractedTable(
        int pageStart,
        int pageEnd,
        String caption,
        String markdownContent,
        Map<String, Object> structuredContent,
        List<NormalizedBoundingBox> boundingBoxes,
        String contentHash
    ) {
    }

    record ExtractionWarning(
        String warningCode,
        String severity,
        String message,
        Integer pageNumber,
        Map<String, Object> metadata
    ) {
    }
}
