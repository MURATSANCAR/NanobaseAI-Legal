package com.nanobase.specai.document.api;

import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentType;
import java.time.Instant;
import java.util.UUID;

public final class DocumentContracts {
    private DocumentContracts() {}

    public record DocumentResponse(UUID id, UUID projectId, String name, DocumentType type,
                                   DocumentStatus status, int currentVersion, Instant createdAt) {
        public static DocumentResponse from(Document document) {
            return new DocumentResponse(document.id(), document.tenderProjectId(), document.name(),
                document.documentType(), document.status(), document.currentVersion(), document.createdAt());
        }
    }

    public record DocumentPreviewResponse(String url, int expiresInSeconds) {}

    public record ClauseResponse(UUID id, UUID parentId, String number, String title,
                                 String sourceText, int pageNumber, int sortOrder) {}
}
