package com.nanobase.specai.document.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.api.DocumentContracts.ClauseResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentPageResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentTableResponse;
import com.nanobase.specai.document.api.DocumentContracts.PageResponse;
import com.nanobase.specai.document.api.DocumentContracts.ParserWarningResponse;
import com.nanobase.specai.document.api.DocumentContracts.ProcessingJobResponse;
import com.nanobase.specai.document.domain.Clause;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentPageRepository;
import com.nanobase.specai.document.domain.DocumentProcessingJob;
import com.nanobase.specai.document.domain.DocumentProcessingJobRepository;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentTableRepository;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.domain.NormalizedBoundingBox;
import com.nanobase.specai.document.domain.ParserWarningRepository;
import com.nanobase.specai.document.integration.DocumentIntelligencePort;
import com.nanobase.specai.document.integration.DocumentIntelligencePort.ExternalProcessingReference;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryService {
    private static final TypeReference<List<NormalizedBoundingBox>> BOXES =
        new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT =
        new TypeReference<>() { };
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentPageRepository pages;
    private final ClauseRepository clauses;
    private final DocumentTableRepository tables;
    private final DocumentProcessingJobRepository jobs;
    private final ParserWarningRepository warnings;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final ProcessingJobService processing;
    private final DocumentIntelligencePort intelligence;

    public DocumentQueryService(
        DocumentRepository documents,
        DocumentVersionRepository versions,
        DocumentPageRepository pages,
        ClauseRepository clauses,
        DocumentTableRepository tables,
        DocumentProcessingJobRepository jobs,
        ParserWarningRepository warnings,
        ProjectAccessService access,
        CurrentTenant currentTenant,
        AuditService audit,
        ObjectMapper objectMapper,
        ProcessingJobService processing,
        DocumentIntelligencePort intelligence) {
        this.documents = documents;
        this.versions = versions;
        this.pages = pages;
        this.clauses = clauses;
        this.tables = tables;
        this.jobs = jobs;
        this.warnings = warnings;
        this.access = access;
        this.currentTenant = currentTenant;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.processing = processing;
        this.intelligence = intelligence;
    }

    @Transactional
    public PageResponse<DocumentPageResponse> pages(
        UUID documentId, int page, int size) {
        AccessContext context = context(documentId, false);
        var result = pages.findAllByDocumentVersionIdAndOrganizationId(
            context.version().id(), context.principal().tenantId(),
            PageRequest.of(page, size, Sort.by("pageNumber").ascending()))
            .map(DocumentPageResponse::from);
        audit.record("DOCUMENT_PREVIEW_OPENED", "Document", documentId, null,
            Map.of("documentVersionId", context.version().id()));
        return PageResponse.from(result);
    }

    @Transactional
    public DocumentPageResponse page(UUID documentId, int pageNumber) {
        AccessContext context = context(documentId, false);
        var result = pages.findByDocumentVersionIdAndOrganizationIdAndPageNumber(
            context.version().id(), context.principal().tenantId(), pageNumber)
            .orElseThrow(() -> new InvalidDocumentException("Document page was not found"));
        audit.record("DOCUMENT_PREVIEW_OPENED", "Document", documentId, null,
            Map.of("documentVersionId", context.version().id(), "pageNumber", pageNumber));
        return DocumentPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClauseResponse> clauses(
        UUID documentId, UUID parentClauseId, String clauseType, Integer pageNumber,
        String search, int page, int size) {
        AccessContext context = context(documentId, false);
        var result = clauses.search(context.version().id(), context.principal().tenantId(),
            parentClauseId, blankToNull(clauseType), pageNumber, blankToNull(search),
            PageRequest.of(page, size, Sort.by("sortOrder").ascending()))
            .map(this::clauseResponse);
        return PageResponse.from(result);
    }

    @Transactional
    public ClauseResponse clause(UUID documentId, UUID clauseId) {
        AccessContext context = context(documentId, false);
        Clause clause = clauses.findByIdAndDocumentVersionIdAndOrganizationId(
            clauseId, context.version().id(), context.principal().tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Clause was not found"));
        audit.record("CLAUSE_VIEWED", "Clause", clauseId, null,
            Map.of("documentId", documentId,
                "documentVersionId", context.version().id()));
        return clauseResponse(clause);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentTableResponse> tables(
        UUID documentId, int page, int size) {
        AccessContext context = context(documentId, false);
        var result = tables.findAllByDocumentVersionIdAndOrganizationId(
            context.version().id(), context.principal().tenantId(),
            PageRequest.of(page, size, Sort.by("pageStart").ascending()))
            .map(table -> new DocumentTableResponse(table.id(), table.pageStart(),
                table.pageEnd(), table.caption(), table.markdownContent(),
                object(table.structuredContentJson()), boxes(table.boundingBoxesJson()),
                table.contentHash()));
        return PageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProcessingJobResponse> jobs(
        UUID documentId, int page, int size) {
        AccessContext context = context(documentId, false);
        var result = jobs.findAllByDocumentIdAndOrganizationId(documentId,
            context.principal().tenantId(),
            PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(job -> ProcessingJobResponse.from(job, warningResponses(
                job, context.principal().tenantId())));
        return PageResponse.from(result);
    }

    @Transactional
    public ProcessingJobResponse cancel(UUID jobId) {
        TenantPrincipal principal = currentTenant.require();
        DocumentProcessingJob job = jobs.findByIdAndOrganizationId(jobId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Processing job was not found"));
        Document document = documents.findByIdAndOrganizationId(
            job.documentId(), principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        access.requireUpload(document.projectId(), principal);
        if (job.status().terminal()) {
            return ProcessingJobResponse.from(job,
                warningResponses(job, principal.tenantId()));
        }
        if (job.externalReference() != null && job.provider() != null
            && !"NONE".equals(job.provider())) {
            intelligence.cancel(new ExternalProcessingReference(
                job.provider(), job.externalReference(), principal.tenantId(),
                job.documentVersionId()));
        }
        DocumentProcessingJob cancelled = processing.transition(principal.tenantId(),
            job.id(), DocumentStatus.CANCELLED, null, null, null);
        audit.record("DOCUMENT_PROCESSING_CANCELLED", "DocumentProcessingJob", jobId,
            null, Map.of("documentId", document.id(),
                "documentVersionId", job.documentVersionId()));
        return ProcessingJobResponse.from(cancelled,
            warningResponses(cancelled, principal.tenantId()));
    }

    private AccessContext context(UUID documentId, boolean uploadRequired) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndOrganizationId(
            documentId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        if (uploadRequired) {
            access.requireUpload(document.projectId(), principal);
        } else {
            access.requireDocumentView(document.projectId(), principal);
        }
        DocumentVersion version = versions.findByIdAndOrganizationId(
            document.currentVersionId(), principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException(
                "Document version was not found"));
        return new AccessContext(principal, document, version);
    }

    private ClauseResponse clauseResponse(Clause clause) {
        return new ClauseResponse(clause.id(), clause.parentId(), clause.clauseNumber(),
            clause.title(), clause.rawText(), clause.normalizedText(), clause.clauseType(),
            clause.pageStart(), clause.pageEnd(), boxes(clause.boundingBoxesJson()),
            clause.contentHash(), clause.sortOrder());
    }

    private List<ParserWarningResponse> warningResponses(
        DocumentProcessingJob job, UUID organizationId) {
        return warnings.findAllByProcessingJobIdAndOrganizationIdOrderByCreatedAt(
            job.id(), organizationId).stream().map(warning ->
                new ParserWarningResponse(warning.id(), warning.warningCode(),
                    warning.severity(), warning.message(), warning.pageNumber(),
                    object(warning.metadataJson()))).toList();
    }

    private List<NormalizedBoundingBox> boxes(String json) {
        try {
            return objectMapper.readValue(json, BOXES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored bounding boxes are invalid", exception);
        }
    }

    private Map<String, Object> object(String json) {
        try {
            return objectMapper.readValue(json, OBJECT);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored parser metadata is invalid", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AccessContext(
        TenantPrincipal principal, Document document, DocumentVersion version) {
    }
}
