package com.nanobase.specai.document.application;

import com.nanobase.specai.audit.domain.AuditEvent;
import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.document.api.ProcessingContracts.ClauseInput;
import com.nanobase.specai.document.api.ProcessingContracts.ProcessingResult;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessingService {
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final ClauseRepository clauses;
    private final AuditEventRepository audit;
    private final Clock clock = Clock.systemUTC();

    public DocumentProcessingService(DocumentRepository documents, DocumentVersionRepository versions,
                                     ClauseRepository clauses, AuditEventRepository audit) {
        this.documents = documents;
        this.versions = versions;
        this.clauses = clauses;
        this.audit = audit;
    }

    @Transactional
    public void accept(ProcessingResult result) {
        DocumentVersion version = versions.findByIdAndTenantId(result.documentVersionId(), result.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document version was not found"));
        Document document = documents.findByIdAndTenantId(version.documentId(), result.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        Instant now = clock.instant();
        DocumentStatus status = DocumentStatus.valueOf(result.status());
        if (status == DocumentStatus.READY) {
            clauses.deleteAllByDocumentVersionIdAndTenantId(version.id(), result.tenantId());
            clauses.saveAll(toEntities(result.tenantId(), version.id(), result.clauses(), now));
        }
        document.processing(status, now);
        audit.save(new AuditEvent(UUID.randomUUID(), result.tenantId(), "document-worker",
            "DOCUMENT_PROCESSING_" + status, "Document", document.id(), now,
            "{\"versionId\":\"%s\",\"clauseCount\":%d}".formatted(version.id(), result.clauses().size())));
    }

    private List<Clause> toEntities(UUID tenantId, UUID versionId, List<ClauseInput> inputs, Instant now) {
        return inputs.stream().map(input -> new Clause(
            input.id(), tenantId, versionId, input.parentId(), input.number(), input.title(),
            input.sourceText(), input.pageNumber(), input.sortOrder(), now)).toList();
    }
}
