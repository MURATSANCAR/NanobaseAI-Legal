package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.document.api.ProcessingContracts.ClauseInput;
import com.nanobase.specai.document.api.ProcessingContracts.ProcessingResult;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {
    @Mock DocumentRepository documents;
    @Mock DocumentVersionRepository versions;
    @Mock ClauseRepository clauses;
    @Mock AuditEventRepository audit;

    @Test
    void readyResultPersistsClausesAndTransitionsDocument() {
        UUID tenant = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Document document = Document.uploaded(documentId, tenant, projectId, "spec.pdf",
            DocumentType.TECHNICAL_SPECIFICATION, "reviewer", Instant.now());
        DocumentVersion version = new DocumentVersion(versionId, tenant, documentId, 1,
            "object-key", "spec.pdf", "application/pdf", 42, "a".repeat(64), Instant.now());
        when(versions.findByIdAndTenantId(versionId, tenant)).thenReturn(Optional.of(version));
        when(documents.findByIdAndTenantId(documentId, tenant)).thenReturn(Optional.of(document));
        ClauseInput input = new ClauseInput(UUID.randomUUID(), null, "7.1",
            "Web tabanlı sistem", "Sistem web tabanlı olmalıdır.", 3, 0);

        new DocumentProcessingService(documents, versions, clauses, audit)
            .accept(new ProcessingResult(tenant, versionId, "READY", null, List.of(input)));

        assertThat(document.status()).isEqualTo(DocumentStatus.READY);
        ArgumentCaptor<List> saved = ArgumentCaptor.forClass(List.class);
        verify(clauses).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        verify(audit).save(any());
    }
}
