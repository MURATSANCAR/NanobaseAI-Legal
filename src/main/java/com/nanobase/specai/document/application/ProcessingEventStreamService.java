package com.nanobase.specai.document.application;

import com.nanobase.specai.document.api.DocumentContracts.ProcessingEvent;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentProcessingJobRepository;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.domain.ProcessingEventRecord;
import com.nanobase.specai.document.domain.ProcessingEventRepository;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ProcessingEventStreamService {
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final ProcessingEventPublisher publisher;

    public ProcessingEventStreamService(
        DocumentRepository documents,
        DocumentVersionRepository versions,
        DocumentProcessingJobRepository jobs,
        ProcessingEventRepository events,
        ProjectAccessService access,
        CurrentTenant currentTenant,
        ProcessingEventPublisher publisher) {
        this.documents = documents;
        this.versions = versions;
        this.jobs = jobs;
        this.events = events;
        this.access = access;
        this.currentTenant = currentTenant;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public SseEmitter document(UUID documentId, String lastEventId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndOrganizationId(
            documentId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        access.requireDocumentView(document.projectId(), principal);
        DocumentVersion version = versions.findByIdAndOrganizationId(
            document.currentVersionId(), principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException(
                "Document version was not found"));
        List<ProcessingEvent> history = history(
            version.id(), principal.tenantId(), documentId, lastEventId);
        return publisher.subscribe(documentId, version.id(), version.processingStatus(), history);
    }

    @Transactional(readOnly = true)
    public SseEmitter job(UUID jobId, String lastEventId) {
        TenantPrincipal principal = currentTenant.require();
        var job = jobs.findByIdAndOrganizationId(jobId, principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException(
                "Processing job was not found"));
        Document document = documents.findByIdAndOrganizationId(
            job.documentId(), principal.tenantId())
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        access.requireDocumentView(document.projectId(), principal);
        List<ProcessingEvent> history = events
            .findAllByProcessingJobIdAndOrganizationIdOrderByOccurredAt(
                jobId, principal.tenantId()).stream()
            .filter(event -> after(event, lastEventId, principal.tenantId()))
            .map(event -> response(event, document.id())).toList();
        return publisher.subscribe(document.id(), job.documentVersionId(),
            job.status(), history);
    }

    private List<ProcessingEvent> history(
        UUID versionId, UUID organizationId, UUID documentId, String lastEventId) {
        Instant after = eventTime(lastEventId, organizationId);
        List<ProcessingEventRecord> records = after == null
            ? events.findAllByDocumentVersionIdAndOrganizationIdOrderByOccurredAt(
                versionId, organizationId)
            : events
                .findAllByDocumentVersionIdAndOrganizationIdAndOccurredAtAfterOrderByOccurredAt(
                    versionId, organizationId, after);
        return records.stream().map(event -> response(event, documentId)).toList();
    }

    private boolean after(ProcessingEventRecord event, String lastEventId, UUID organizationId) {
        Instant after = eventTime(lastEventId, organizationId);
        return after == null || event.occurredAt().isAfter(after);
    }

    private Instant eventTime(String lastEventId, UUID organizationId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return events.findByIdAndOrganizationId(
                UUID.fromString(lastEventId), organizationId)
                .map(ProcessingEventRecord::occurredAt).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ProcessingEvent response(ProcessingEventRecord event, UUID documentId) {
        return new ProcessingEvent(event.id(), event.processingJobId(), documentId,
            event.documentVersionId(),
            com.nanobase.specai.document.domain.DocumentStatus.valueOf(event.stage()),
            event.progress(), event.message(), event.occurredAt());
    }
}
