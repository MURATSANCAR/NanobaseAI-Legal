package com.nanobase.specai.document.application;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.api.DocumentContracts.ClauseResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentResponse;
import com.nanobase.specai.document.api.DocumentContracts.DocumentVersionResponse;
import com.nanobase.specai.document.domain.ClauseRepository;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentType;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.domain.DocumentProcessingJob;
import com.nanobase.specai.document.integration.DocumentEvents.DocumentUploaded;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import com.nanobase.specai.tender.application.ProjectAccessService;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private static final Set<String> MEDIA_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final ProjectAccessService access;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ObjectStorage storage;
    private final CurrentTenant currentTenant;
    private final FileTypeInspector fileTypeInspector;
    private final ClauseRepository clauses;
    private final ProcessingJobService processingJobs;
    private final long maximumFileSize;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                           ProjectAccessService access, AuditService audit, OutboxService outbox,
                           ObjectStorage storage, CurrentTenant currentTenant,
                           FileTypeInspector fileTypeInspector, ClauseRepository clauses,
                           ProcessingJobService processingJobs,
                           @Value("${specai.documents.max-file-size-bytes:104857600}")
                           long maximumFileSize) {
        this(documents, versions, access, audit, outbox, storage, currentTenant,
            fileTypeInspector, clauses, processingJobs, maximumFileSize, Clock.systemUTC());
    }

    DocumentService(DocumentRepository documents, DocumentVersionRepository versions,
                    ProjectAccessService access, AuditService audit, OutboxService outbox,
                    ObjectStorage storage, CurrentTenant currentTenant,
                    FileTypeInspector fileTypeInspector, ClauseRepository clauses,
                    ProcessingJobService processingJobs, long maximumFileSize, Clock clock) {
        this.documents = documents;
        this.versions = versions;
        this.access = access;
        this.audit = audit;
        this.outbox = outbox;
        this.storage = storage;
        this.currentTenant = currentTenant;
        this.fileTypeInspector = fileTypeInspector;
        this.clauses = clauses;
        this.processingJobs = processingJobs;
        this.maximumFileSize = maximumFileSize;
        this.clock = clock;
    }

    @Transactional
    public DocumentResponse upload(UUID projectId, DocumentType type, String logicalName,
                                   boolean includedInAnalysis, MultipartFile file) {
        TenantPrincipal principal = currentTenant.require();
        access.requireUpload(projectId, principal);
        UploadMetadata upload = inspect(projectId, principal.tenantId(), file);
        Instant now = clock.instant();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String temporaryKey = temporaryKey(principal.tenantId(), UUID.randomUUID(),
            upload.fileName());
        String objectKey = objectKey(principal.tenantId(), projectId, documentId,
            versionId, upload.fileName());
        registerCompensation(temporaryKey, objectKey);
        put(temporaryKey, file, upload.mimeType());
        try {
            storage.finalizeObject(temporaryKey, objectKey, file.getSize(), upload.sha256());
            String resolvedLogicalName = logicalName == null || logicalName.isBlank()
                ? withoutExtension(upload.fileName()) : logicalName.trim();
            if (resolvedLogicalName.length() > 255) {
                throw new InvalidDocumentException("Logical name must not exceed 255 characters");
            }
            Document document = Document.uploaded(documentId, principal.tenantId(), projectId,
                resolvedLogicalName, type, includedInAnalysis, principal.subject(), now);
            documents.save(document);
            DocumentVersion version = new DocumentVersion(versionId, principal.tenantId(),
                documentId, 1, objectKey, upload.fileName(), upload.mimeType(), file.getSize(),
                upload.sha256(), principal.subject(), now);
            versions.save(version);
            document.attachVersion(versionId, 1, now);
            DocumentProcessingJob job = processingJobs.create(principal.tenantId(), projectId,
                documentId, versionId, RequestContext.current().correlationId(), now);
            outbox.documentUploaded(principal.tenantId(), event(document, version, job));
            audit.record("DOCUMENT_UPLOADED", "Document", documentId, null,
                Map.of("documentVersionId", versionId, "sha256", upload.sha256()));
            return DocumentResponse.from(document, version);
        } catch (RuntimeException exception) {
            safeDelete(temporaryKey, exception);
            safeDelete(objectKey, exception);
            throw exception;
        }
    }

    @Transactional
    public DocumentResponse uploadVersion(UUID documentId, MultipartFile file) {
        TenantPrincipal principal = currentTenant.require();
        Document document = requireDocumentForUpdate(documentId, principal);
        access.requireUpload(document.projectId(), principal);
        UploadMetadata upload = inspect(document.projectId(), principal.tenantId(), file);
        int versionNumber = document.currentVersionNumber() + 1;
        UUID versionId = UUID.randomUUID();
        Instant now = clock.instant();
        String temporaryKey = temporaryKey(principal.tenantId(), UUID.randomUUID(),
            upload.fileName());
        String objectKey = objectKey(principal.tenantId(), document.projectId(), document.id(),
            versionId, upload.fileName());
        registerCompensation(temporaryKey, objectKey);
        put(temporaryKey, file, upload.mimeType());
        try {
            storage.finalizeObject(temporaryKey, objectKey, file.getSize(), upload.sha256());
            DocumentVersion version = new DocumentVersion(versionId, principal.tenantId(),
                document.id(), versionNumber, objectKey, upload.fileName(), upload.mimeType(),
                file.getSize(), upload.sha256(), principal.subject(), now);
            versions.save(version);
            document.attachVersion(versionId, versionNumber, now);
            DocumentProcessingJob job = processingJobs.create(principal.tenantId(),
                document.projectId(), document.id(), versionId,
                RequestContext.current().correlationId(), now);
            outbox.documentUploaded(principal.tenantId(), event(document, version, job));
            audit.record("DOCUMENT_VERSION_UPLOADED", "Document", documentId, null,
                Map.of("documentVersionId", versionId, "versionNumber", versionNumber,
                    "sha256", upload.sha256()));
            return DocumentResponse.from(document, version);
        } catch (RuntimeException exception) {
            safeDelete(temporaryKey, exception);
            safeDelete(objectKey, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireDocumentView(projectId, principal);
        return documents.findAllByProjectIdAndOrganizationIdOrderByCreatedAtDesc(
            projectId, principal.tenantId()).stream().map(document ->
                DocumentResponse.from(document, currentVersion(document, principal.tenantId())))
            .toList();
    }

    @Transactional
    public DocumentResponse get(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = requireDocument(documentId, principal);
        access.requireDocumentView(document.projectId(), principal);
        DocumentVersion version = currentVersion(document, principal.tenantId());
        audit.record("DOCUMENT_VIEWED", "Document", documentId, null,
            Map.of("documentVersionId", version.id()));
        return DocumentResponse.from(document, version);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> versions(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = requireDocument(documentId, principal);
        access.requireDocumentView(document.projectId(), principal);
        return versions.findAllByDocumentIdAndOrganizationIdOrderByVersionNumberDesc(
            documentId, principal.tenantId()).stream().map(DocumentVersionResponse::from).toList();
    }

    @Transactional
    public DocumentResponse reprocess(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = requireDocumentForUpdate(documentId, principal);
        access.requireUpload(document.projectId(), principal);
        DocumentVersion version = currentVersion(document, principal.tenantId());
        version.reprocess();
        document.processing(DocumentStatus.UPLOADED, version.id(), clock.instant());
        DocumentProcessingJob job = processingJobs.create(principal.tenantId(),
            document.projectId(), document.id(), version.id(),
            RequestContext.current().correlationId(), clock.instant());
        outbox.documentUploaded(principal.tenantId(), event(document, version, job));
        audit.record("DOCUMENT_REPROCESS_REQUESTED", "Document", documentId, null,
            Map.of("documentVersionId", version.id()));
        return DocumentResponse.from(document, version);
    }

    @Transactional
    public URI downloadUrl(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = requireDocument(documentId, principal);
        access.requireDocumentView(document.projectId(), principal);
        DocumentVersion version = currentVersion(document, principal.tenantId());
        URI url = storage.signedDownloadUrl(version.objectStorageKey(), Duration.ofMinutes(5));
        audit.record("DOCUMENT_DOWNLOADED", "Document", documentId, null,
            Map.of("documentVersionId", version.id(), "expiresInSeconds", 300));
        return url;
    }

    @Transactional(readOnly = true)
    public List<ClauseResponse> clauses(UUID documentId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = requireDocument(documentId, principal);
        access.requireDocumentView(document.projectId(), principal);
        DocumentVersion version = currentVersion(document, principal.tenantId());
        return clauses.findAllByDocumentVersionIdAndOrganizationIdOrderBySortOrder(
            version.id(), principal.tenantId()).stream()
            .map(clause -> new ClauseResponse(clause.id(), clause.parentId(),
                clause.clauseNumber(), clause.title(), clause.sourceText(),
                clause.pageNumber(), clause.sortOrder())).toList();
    }

    Document requireDocument(UUID documentId, TenantPrincipal principal) {
        return documents.findByIdAndOrganizationId(documentId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
    }

    private Document requireDocumentForUpdate(UUID documentId, TenantPrincipal principal) {
        return documents.findForUpdate(documentId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
    }

    DocumentVersion currentVersion(Document document, UUID organizationId) {
        if (document.currentVersionId() == null) {
            throw new InvalidDocumentException("Document version was not found");
        }
        return versions.findByIdAndOrganizationId(document.currentVersionId(), organizationId)
            .orElseThrow(() -> new InvalidDocumentException("Document version was not found"));
    }

    public static String sha256(InputStream content) {
        try (DigestInputStream digest = new DigestInputStream(
            content, MessageDigest.getInstance("SHA-256"))) {
            digest.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 could not be calculated", exception);
        }
    }

    private UploadMetadata inspect(UUID projectId, UUID organizationId, MultipartFile file) {
        validate(file);
        String fileName = FileNameSanitizer.sanitize(file.getOriginalFilename());
        try (InputStream content = file.getInputStream()) {
            String mimeType = fileTypeInspector.inspect(content, fileName);
            validateExtension(fileName, mimeType);
            String hash;
            try (InputStream digestContent = file.getInputStream()) {
                hash = sha256(digestContent);
            }
            if (versions.existsDuplicate(projectId, organizationId, hash)) {
                throw new InvalidDocumentException(
                    "The same file has already been uploaded to this project");
            }
            return new UploadMetadata(fileName, mimeType, hash);
        } catch (IOException exception) {
            throw new InvalidDocumentException("File content could not be read");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("File must not be empty");
        }
        if (file.getSize() > maximumFileSize) {
            throw new InvalidDocumentException("File exceeds the configured size limit");
        }
    }

    private void validateExtension(String fileName, String mimeType) {
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        boolean matches = ("application/pdf".equals(mimeType) && lower.endsWith(".pdf"))
            || ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(mimeType) && lower.endsWith(".docx"));
        if (!MEDIA_TYPES.contains(mimeType) || !matches) {
            throw new InvalidDocumentException(
                "File extension does not match its detected PDF or DOCX content");
        }
    }

    private void put(String objectKey, MultipartFile file, String mimeType) {
        try (InputStream content = file.getInputStream()) {
            storage.put(objectKey, content, file.getSize(), mimeType);
        } catch (IOException exception) {
            throw new IllegalStateException("Document upload failed", exception);
        }
    }

    private void safeDelete(String objectKey, RuntimeException original) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException cleanup) {
            original.addSuppressed(cleanup);
        }
    }

    private String objectKey(UUID organizationId, UUID projectId, UUID documentId,
                             UUID versionId, String fileName) {
        return "specai-original/%s/%s/%s/%s/%s".formatted(
            organizationId, projectId, documentId, versionId, fileName);
    }

    private String temporaryKey(UUID organizationId, UUID uploadId, String fileName) {
        return "specai-temp/%s/%s/%s".formatted(organizationId, uploadId, fileName);
    }

    private String withoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private DocumentUploaded event(Document document, DocumentVersion version,
                                   DocumentProcessingJob job) {
        return new DocumentUploaded(job.id(), document.projectId(), document.id(), version.id(),
            version.objectStorageKey(), version.originalFileName(), version.mimeType(),
            version.fileSize(), version.sha256(), version.language(), version.ocrRequired());
    }

    private void registerCompensation(String temporaryKey, String finalKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        safeDeleteQuietly(temporaryKey);
                        safeDeleteQuietly(finalKey);
                    }
                }
            });
    }

    private void safeDeleteQuietly(String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // The scheduled orphan reconciler performs the second cleanup layer.
        }
    }

    private record UploadMetadata(String fileName, String mimeType, String sha256) {
    }
}
