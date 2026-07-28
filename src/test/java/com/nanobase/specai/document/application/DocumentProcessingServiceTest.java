package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ProcessingStatusResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {
    @Mock DocumentRepository documents;
    @Mock DocumentVersionRepository versions;
    @Mock AuditService audit;
    @Mock ProcessingEventPublisher events;

    @Test
    void readyResultUsesValidStatusTransitions() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-27T12:00:00Z");
        Document document = Document.uploaded(documentId, organizationId, projectId,
            "Teknik Şartname", DocumentType.TECHNICAL_SPECIFICATION, true, "manager", now);
        document.attachVersion(versionId, 1, now);
        DocumentVersion version = new DocumentVersion(versionId, organizationId, documentId, 1,
            "object-key", "spec.pdf", "application/pdf", 42, "a".repeat(64), "manager", now);
        when(versions.findForUpdate(versionId, organizationId)).thenReturn(Optional.of(version));
        when(documents.findByIdAndOrganizationId(documentId, organizationId))
            .thenReturn(Optional.of(document));

        DocumentProcessingService service =
            new DocumentProcessingService(documents, versions, audit, events);
        service.complete(organizationId, versionId,
            new ProcessingStatusResult(DocumentStatus.READY, "READY", 100,
                "ready", null));

        assertThat(document.status()).isEqualTo(DocumentStatus.READY);
        assertThat(version.processingStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void processingStatusCannotMoveBackwards() {
        UUID id = UUID.randomUUID();
        DocumentVersion version = new DocumentVersion(id, UUID.randomUUID(), UUID.randomUUID(), 1,
            "key", "spec.pdf", "application/pdf", 42, "a".repeat(64), "manager", Instant.now());
        version.transition(DocumentStatus.PARSING, Instant.now(), null, null);

        assertThatThrownBy(() -> version.transition(
            DocumentStatus.VIRUS_SCANNING, Instant.now(), null, null))
            .isInstanceOf(IllegalStateException.class);
    }
}
