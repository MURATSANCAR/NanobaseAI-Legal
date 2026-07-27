package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private static final UUID ORGANIZATION =
        UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROJECT =
        UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Mock DocumentRepository documents;
    @Mock DocumentVersionRepository versions;
    @Mock ProjectAccessService access;
    @Mock AuditService audit;
    @Mock OutboxService outbox;
    @Mock ObjectStorage storage;
    @Mock CurrentTenant currentTenant;
    @Mock FileTypeInspector fileTypeInspector;
    @Mock ClauseRepository clauses;
    private DocumentService service;

    @BeforeEach
    void setup() {
        when(currentTenant.require()).thenReturn(
            new TenantPrincipal(ORGANIZATION, "manager-1", Set.of("TENDER_MANAGER")));
        when(fileTypeInspector.inspect(any(InputStream.class), eq("sartname.pdf")))
            .thenReturn("application/pdf");
        when(versions.existsDuplicate(eq(PROJECT), eq(ORGANIZATION), any())).thenReturn(false);
        service = new DocumentService(documents, versions, access, audit, outbox, storage,
            currentTenant, fileTypeInspector, clauses, 10_000_000,
            Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesBinaryOutsideDatabaseAndEnqueuesOutboxEvent() {
        MockMultipartFile file = new MockMultipartFile("file", "sartname.pdf",
            "application/octet-stream", "%PDF-1.7 sample".getBytes());

        var result = service.upload(PROJECT, DocumentType.TECHNICAL_SPECIFICATION,
            "Teknik Şartname", true, file);

        assertThat(result.status().name()).isEqualTo("UPLOADED");
        assertThat(result.currentVersionNumber()).isEqualTo(1);
        verify(access).requireUpload(PROJECT, currentTenant.require());
        verify(storage).put(any(), any(InputStream.class), eq(file.getSize()), eq("application/pdf"));
        verify(documents).save(any());
        verify(versions).save(any());
        verify(outbox).documentUploaded(eq(ORGANIZATION), any());
        verify(audit).record(eq("DOCUMENT_UPLOADED"), eq("Document"), any(), eq(null), any());
    }

}
