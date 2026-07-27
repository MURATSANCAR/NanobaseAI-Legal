package com.nanobase.specai.document.application;

import com.nanobase.specai.audit.domain.AuditEvent;
import com.nanobase.specai.audit.domain.AuditEventRepository;
import com.nanobase.specai.document.api.DocumentContracts.DocumentResponse;
import com.nanobase.specai.document.api.DocumentContracts.ClauseResponse;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.integration.outbox.OutboxEvent;
import com.nanobase.specai.integration.outbox.OutboxEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.TenderNotFoundException;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final TenderProjectRepository projects;
    private final AuditEventRepository audit;
    private final OutboxEventRepository outbox;
    private final ObjectStorage storage;
    private final CurrentTenant currentTenant;
    private final FileTypeInspector fileTypeInspector;
    private final ClauseRepository clauses;
    private final Clock clock = Clock.systemUTC();

    DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                           TenderProjectRepository projects, AuditEventRepository audit,
                           OutboxEventRepository outbox, ObjectStorage storage,
                           CurrentTenant currentTenant, FileTypeInspector fileTypeInspector) {
        this(documents, versions, projects, audit, outbox, storage, currentTenant, fileTypeInspector, null);
    }

    @Autowired
    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                           TenderProjectRepository projects, AuditEventRepository audit,
                           OutboxEventRepository outbox, ObjectStorage storage,
                           CurrentTenant currentTenant, FileTypeInspector fileTypeInspector,
                           ClauseRepository clauses) {
        this.documents = documents;
        this.versions = versions;
        this.projects = projects;
        this.audit = audit;
        this.outbox = outbox;
        this.storage = storage;
        this.currentTenant = currentTenant;
        this.fileTypeInspector = fileTypeInspector;
        this.clauses = clauses;
    }

    @Transactional
    public DocumentResponse upload(UUID projectId, DocumentType type, MultipartFile file) {
        TenantPrincipal principal = currentTenant.require();
        projects.findByIdAndOrganizationId(projectId, principal.tenantId())
            .orElseThrow(() -> new TenderNotFoundException(projectId));
        validate(file);

        Instant now = clock.instant();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String filename = safeFilename(file.getOriginalFilename());
        String objectKey = "%s/%s/%s/%s/original".formatted(
            principal.tenantId(), projectId, documentId, versionId);
        String sha256;
        String detectedMediaType;
        try (InputStream source = new BufferedInputStream(file.getInputStream());
             DigestInputStream digest = new DigestInputStream(source, MessageDigest.getInstance("SHA-256"))) {
            source.mark(16 * 1024);
            detectedMediaType = fileTypeInspector.inspect(source, filename);
            source.reset();
            storage.put(objectKey, digest, file.getSize(), detectedMediaType);
            sha256 = HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Document upload failed", exception);
        }

        try {
            Document document = Document.uploaded(documentId, principal.tenantId(), projectId,
                filename, type, principal.subject(), now);
            documents.save(document);
            versions.save(new DocumentVersion(versionId, principal.tenantId(), documentId, 1,
                objectKey, filename, detectedMediaType, file.getSize(), sha256, now));
            audit.save(new AuditEvent(UUID.randomUUID(), principal.tenantId(), principal.subject(),
                "DOCUMENT_UPLOADED", "Document", documentId, null, null, null,
                "{\"versionId\":\"%s\",\"sha256\":\"%s\"}".formatted(versionId, sha256),
                UUID.randomUUID(), now));
            outbox.save(new OutboxEvent(UUID.randomUUID(), principal.tenantId(), "document.uploaded",
                "{\"eventType\":\"DocumentUploaded\",\"schemaVersion\":1,"
                    + "\"tenantId\":\"%s\",\"projectId\":\"%s\",\"documentId\":\"%s\","
                    + "\"documentVersionId\":\"%s\",\"objectKey\":\"%s\",\"mediaType\":\"%s\"}"
                    .formatted(principal.tenantId(), projectId, documentId, versionId, objectKey,
                        detectedMediaType), now));
            return DocumentResponse.from(document);
        } catch (RuntimeException exception) {
            storage.delete(objectKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        projects.findByIdAndOrganizationId(projectId, principal.tenantId())
            .orElseThrow(() -> new TenderNotFoundException(projectId));
        return documents.findAllByTenderProjectIdAndTenantIdOrderByCreatedAtDesc(
            projectId, principal.tenantId()).stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public URI preview(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndTenantId(documentId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        DocumentVersion version = versions.findByDocumentIdAndTenantIdAndVersionNumber(
            documentId, principal.tenantId(), document.currentVersion())
            .orElseThrow(() -> new InvalidDocumentException("Document version was not found"));
        return storage.signedDownloadUrl(version.objectKey(), Duration.ofMinutes(10));
    }

    @Transactional(readOnly = true)
    public List<ClauseResponse> clauses(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndTenantId(documentId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        DocumentVersion version = versions.findByDocumentIdAndTenantIdAndVersionNumber(
            documentId, principal.tenantId(), document.currentVersion())
            .orElseThrow(() -> new InvalidDocumentException("Document version was not found"));
        if (clauses == null) {
            return List.of();
        }
        return clauses.findAllByDocumentVersionIdAndTenantIdOrderBySortOrder(version.id(), principal.tenantId())
            .stream().map(clause -> new ClauseResponse(clause.id(), clause.parentId(), clause.clauseNumber(),
                clause.title(), clause.sourceText(), clause.pageNumber(), clause.sortOrder())).toList();
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidDocumentException("File must not be empty");
        }
        if (!MEDIA_TYPES.contains(file.getContentType())) {
            throw new InvalidDocumentException("Only PDF and DOCX files are accepted");
        }
        String filename = safeFilename(file.getOriginalFilename()).toLowerCase();
        if (!(filename.endsWith(".pdf") || filename.endsWith(".docx"))) {
            throw new InvalidDocumentException("File extension does not match an accepted document type");
        }
    }

    private String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document";
        }
        String normalized = original.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
    }
}
