package com.nanobase.specai.document.integration;

import java.util.UUID;

/**
 * Provider-neutral boundary. Implementations may use OpenContracts, Docling, or another parser.
 */
public interface DocumentIntelligencePort {
    ProcessingReference submit(UUID tenantId, UUID documentVersionId, String objectKey);
    ProcessingStatus status(String externalDocumentId);

    record ProcessingReference(String provider, String corpusId, String documentId, String version) {}
    record ProcessingStatus(String state, int progress, String message) {}
}
