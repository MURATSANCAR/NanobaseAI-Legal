package com.nanobase.specai.document.application;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentProcessingJob;
import com.nanobase.specai.document.domain.DocumentProcessingJobRepository;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.document.domain.DocumentVersion;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.document.domain.ProcessingEventRecord;
import com.nanobase.specai.document.domain.ProcessingEventRepository;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProcessingJobService {
    private final DocumentProcessingJobRepository jobs;
    private final ProcessingEventRepository processingEvents;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final AuditService audit;
    private final ProcessingEventPublisher liveEvents;
    private final TenantDatabaseContext tenantContext;
    private final PlatformMetrics metrics;
    private final Clock clock = Clock.systemUTC();

    public ProcessingJobService(
        DocumentProcessingJobRepository jobs,
        ProcessingEventRepository processingEvents,
        DocumentRepository documents,
        DocumentVersionRepository versions,
        AuditService audit,
        ProcessingEventPublisher liveEvents,
        TenantDatabaseContext tenantContext,
        PlatformMetrics metrics) {
        this.jobs = jobs;
        this.processingEvents = processingEvents;
        this.documents = documents;
        this.versions = versions;
        this.audit = audit;
        this.liveEvents = liveEvents;
        this.tenantContext = tenantContext;
        this.metrics = metrics;
    }

    public DocumentProcessingJob create(
        UUID organizationId, UUID projectId, UUID documentId, UUID documentVersionId,
        UUID correlationId, Instant now) {
        DocumentProcessingJob job = DocumentProcessingJob.queued(UUID.randomUUID(),
            organizationId, projectId, documentId, documentVersionId, correlationId, now);
        jobs.save(job);
        ProcessingEventRecord event = persistEvent(job, "JOB_CREATED",
            "Doküman işleme işi oluşturuldu", now);
        publishAfterCommit(documentId, event);
        return job;
    }

    @Transactional
    public DocumentProcessingJob transition(
        UUID organizationId, UUID jobId, DocumentStatus next,
        String message, String errorCode, String errorMessage) {
        tenantContext.apply(organizationId);
        DocumentProcessingJob job = require(jobId, organizationId);
        if (job.status() == next) {
            job.heartbeat(clock.instant());
            return job;
        }
        Instant now = clock.instant();
        DocumentStatus before = job.status();
        int progress = progress(next);
        job.transition(next, progress, message, errorCode, errorMessage, now);
        DocumentVersion version = versions.findForUpdate(
            job.documentVersionId(), organizationId)
            .orElseThrow(() -> new InvalidDocumentException(
                "Document version was not found"));
        if (version.processingStatus() != next) {
            version.transition(next, now, errorCode, errorMessage);
        }
        Document document = documents.findByIdAndOrganizationId(
            job.documentId(), organizationId)
            .orElseThrow(() -> new InvalidDocumentException("Document was not found"));
        document.processing(next, version.id(), now);
        ProcessingEventRecord event = persistEvent(job, "STAGE_CHANGED",
            message == null ? message(next) : message, now);
        audit.recordSystem(organizationId, "document-worker",
            "DOCUMENT_PROCESSING_STATUS_CHANGED", "DocumentProcessingJob", job.id(),
            Map.of("status", before.name()),
            Map.of("status", next.name(), "documentVersionId", version.id()));
        if (next == DocumentStatus.MANUAL_REVIEW_REQUIRED) {
            metrics.manualReview();
        }
        publishAfterCommit(job.documentId(), event);
        return job;
    }

    @Transactional
    public void submitted(UUID organizationId, UUID jobId, String provider,
                          String externalReference) {
        tenantContext.apply(organizationId);
        DocumentProcessingJob job = require(jobId, organizationId);
        Instant now = clock.instant();
        job.routed(provider, now);
        job.submitted(externalReference, now);
        ProcessingEventRecord event = persistEvent(job, "PROVIDER_SUBMITTED",
            "Doküman parser servisine gönderildi", now);
        publishAfterCommit(job.documentId(), event);
    }

    @Transactional(readOnly = true)
    public DocumentProcessingJob get(UUID organizationId, UUID jobId) {
        tenantContext.apply(organizationId);
        return jobs.findByIdAndOrganizationId(jobId, organizationId)
            .orElseThrow(() -> new InvalidDocumentException(
                "Processing job was not found"));
    }

    private DocumentProcessingJob require(UUID jobId, UUID organizationId) {
        return jobs.findForUpdate(jobId, organizationId)
            .orElseThrow(() -> new InvalidDocumentException(
                "Processing job was not found"));
    }

    private ProcessingEventRecord persistEvent(
        DocumentProcessingJob job, String eventType, String message, Instant now) {
        ProcessingEventRecord event = new ProcessingEventRecord(
            UUID.randomUUID(), job.organizationId(), job.id(), job.documentVersionId(),
            job.currentStage().name(), job.progress(), message, eventType, "{}", now);
        processingEvents.save(event);
        return event;
    }

    private void publishAfterCommit(UUID documentId, ProcessingEventRecord event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            liveEvents.publish(documentId, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    liveEvents.publish(documentId, event);
                }
            });
    }

    public static int progress(DocumentStatus status) {
        return switch (status) {
            case UPLOADED -> 5;
            case VIRUS_SCANNING -> 12;
            case CLASSIFYING -> 22;
            case QUEUED -> 30;
            case PARSING -> 45;
            case OCR_PROCESSING -> 60;
            case STRUCTURE_DETECTION -> 74;
            case INDEXING -> 90;
            case READY, FAILED, MANUAL_REVIEW_REQUIRED, CANCELLED -> 100;
        };
    }

    public static String message(DocumentStatus status) {
        return switch (status) {
            case UPLOADED -> "Doküman işleme işi oluşturuldu";
            case VIRUS_SCANNING -> "Doküman güvenlik taramasından geçiriliyor";
            case CLASSIFYING -> "Doküman sınıflandırılıyor";
            case QUEUED -> "Doküman parser kuyruğuna alındı";
            case PARSING -> "Doküman metni ayrıştırılıyor";
            case OCR_PROCESSING -> "OCR işlemi uygulanıyor";
            case STRUCTURE_DETECTION -> "Teknik şartname maddeleri ayrıştırılıyor";
            case INDEXING -> "Doküman indeksleniyor";
            case READY -> "Doküman hazır";
            case FAILED -> "Doküman işlenemedi";
            case MANUAL_REVIEW_REQUIRED -> "Manuel inceleme gerekiyor";
            case CANCELLED -> "Doküman işleme iptal edildi";
        };
    }
}
