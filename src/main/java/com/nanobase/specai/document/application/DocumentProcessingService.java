package com.nanobase.specai.document.application;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.DocumentProcessingResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final AuditService audit;
    private final ProcessingEventPublisher events;
    private final Clock clock = Clock.systemUTC();

    public DocumentProcessingService(DocumentRepository documents,
                                     DocumentVersionRepository versions,
                                     AuditService audit,
                                     ProcessingEventPublisher events) {
        this.documents = documents;
        this.versions = versions;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public void start(UUID organizationId, UUID versionId) {
        DocumentVersion version = requireVersion(organizationId, versionId);
        if (version.processingStatus().terminal()) {
            return;
        }
        if (version.processingStatus() == DocumentStatus.UPLOADED) {
            transition(version, DocumentStatus.VIRUS_SCANNING, null, null);
        }
    }

    @Transactional
    public void complete(UUID organizationId, UUID versionId, DocumentProcessingResult result) {
        DocumentVersion version = requireVersion(organizationId, versionId);
        if (version.processingStatus().terminal()) {
            return;
        }
        if (result.status() == DocumentStatus.READY) {
            progressToReady(version);
        } else {
            transition(version, result.status(), result.errorCode(), result.message());
        }
    }

    @Transactional
    public void fail(UUID organizationId, UUID versionId, String code, String message) {
        DocumentVersion version = requireVersion(organizationId, versionId);
        if (!version.processingStatus().terminal()) {
            transition(version, DocumentStatus.FAILED, code, message);
        }
    }

    private void progressToReady(DocumentVersion version) {
        for (DocumentStatus status : new DocumentStatus[]{
            DocumentStatus.CLASSIFYING, DocumentStatus.PARSING,
            DocumentStatus.STRUCTURE_DETECTION, DocumentStatus.INDEXING,
            DocumentStatus.READY}) {
            if (!version.processingStatus().terminal()
                && version.processingStatus().ordinal() < status.ordinal()) {
                transition(version, status, null, null);
            }
        }
    }

    private void transition(DocumentVersion version, DocumentStatus status,
                            String errorCode, String errorMessage) {
        DocumentStatus before = version.processingStatus();
        Instant now = clock.instant();
        version.transition(status, now, errorCode, errorMessage);
        Document document = documents.findByIdAndOrganizationId(
            version.documentId(), version.organizationId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        document.processing(status, version.id(), now);
        audit.recordSystem(version.organizationId(), "document-worker",
            "DOCUMENT_STATUS_CHANGED", "Document", document.id(),
            Map.of("status", before.name()),
            Map.of("status", status.name(), "documentVersionId", version.id()));
        events.publish(document.id(), version.id(), status);
    }

    private DocumentVersion requireVersion(UUID organizationId, UUID versionId) {
        return versions.findForUpdate(versionId, organizationId)
            .orElseThrow(() -> new InvalidDocumentException("Document version was not found"));
    }
}
