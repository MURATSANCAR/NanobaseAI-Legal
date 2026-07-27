package com.nanobase.specai.document.integration;

import com.nanobase.specai.document.domain.DocumentStatus;
import java.util.UUID;

/**
 * Provider-neutral boundary. Domain code never sees provider IDs, GraphQL types,
 * or OpenContracts implementation classes.
 */
public interface DocumentIntelligencePort {
    DocumentProcessingResult process(DocumentProcessingCommand command);

    record DocumentProcessingCommand(
        UUID organizationId,
        UUID projectId,
        UUID documentId,
        UUID documentVersionId,
        String objectStorageKey,
        String mimeType,
        long fileSize
    ) {
    }

    record DocumentProcessingResult(
        DocumentStatus status,
        String errorCode,
        String message
    ) {
    }
}
