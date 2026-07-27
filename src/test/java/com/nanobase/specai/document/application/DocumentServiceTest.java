package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.integration.outbox.OutboxEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.domain.Priority;
import com.nanobase.specai.tender.domain.TenderProject;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Mock DocumentRepository documents;
    @Mock DocumentVersionRepository versions;
    @Mock TenderProjectRepository projects;
    @Mock AuditEventRepository audit;
    @Mock OutboxEventRepository outbox;
    @Mock ObjectStorage storage;
    @Mock CurrentTenant currentTenant;
    @Mock FileTypeInspector fileTypeInspector;
    private DocumentService service;

    @BeforeEach
    void setup() {
        when(currentTenant.require()).thenReturn(new TenantPrincipal(TENANT, "reviewer-1", Set.of()));
        TenderProject project = TenderProject.create(PROJECT, TENANT, "TND-2026-TEST",
            "Test", "Kurum", null, LocalDate.now().plusDays(10), "TRY",
            Priority.NORMAL, null, "reviewer-1", Instant.now());
        when(projects.findByIdAndTenantId(PROJECT, TENANT)).thenReturn(Optional.of(project));
        when(fileTypeInspector.inspect(any(InputStream.class), eq("sartname.pdf")))
            .thenReturn("application/pdf");
        service = new DocumentService(documents, versions, projects, audit, outbox, storage,
            currentTenant, fileTypeInspector);
    }

    @Test
    void storesBinaryOutsideDatabaseAndEnqueuesOutboxEvent() {
        MockMultipartFile file = new MockMultipartFile("file", "sartname.pdf",
            "application/pdf", "%PDF-1.7 sample".getBytes());

        var result = service.upload(PROJECT, DocumentType.TECHNICAL_SPECIFICATION, file);

        assertThat(result.status().name()).isEqualTo("UPLOADED");
        verify(storage).put(any(), any(InputStream.class), eq(file.getSize()), eq("application/pdf"));
        verify(documents).save(any());
        verify(versions).save(any());
        verify(outbox).save(any());
        verify(audit).save(any());
    }
}
