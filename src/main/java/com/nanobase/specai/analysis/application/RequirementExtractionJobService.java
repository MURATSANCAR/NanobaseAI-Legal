package com.nanobase.specai.analysis.application;

import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.analysis.domain.RequirementExtractionJob;
import com.nanobase.specai.analysis.domain.RequirementExtractionJobRepository;
import com.nanobase.specai.analysis.infrastructure.AnalysisPersistenceStore;
import com.nanobase.specai.analysis.integration.AnalysisEvents.ExtractionRequested;
import com.nanobase.specai.document.domain.Document;
import com.nanobase.specai.document.domain.DocumentRepository;
import com.nanobase.specai.document.domain.DocumentStatus;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequirementExtractionJobService {
    private final RequirementExtractionJobRepository jobs;
    private final AnalysisProfileService profiles;
    private final DocumentRepository documents;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final AnalysisPersistenceStore store;
    private final OutboxService outbox;
    private final Clock clock = Clock.systemUTC();

    public RequirementExtractionJobService(RequirementExtractionJobRepository jobs,
                                           AnalysisProfileService profiles,
                                           DocumentRepository documents,
                                           ProjectAccessService access,
                                           CurrentTenant currentTenant,
                                           AnalysisPersistenceStore store,
                                           OutboxService outbox) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.documents = documents;
        this.access = access;
        this.currentTenant = currentTenant;
        this.store = store;
        this.outbox = outbox;
    }

    @Transactional
    public RequirementExtractionJob create(UUID documentId) {
        return enqueue(documentId, null);
    }

    @Transactional(readOnly = true)
    public RequirementExtractionJob get(UUID jobId) {
        return jobs.findByIdAndOrganizationId(jobId, currentTenant.require().tenantId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Requirement extraction job not found: " + jobId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(UUID jobId) {
        RequirementExtractionJob job = get(jobId);
        return store.events(job.organizationId(), job.id());
    }

    @Transactional
    public RequirementExtractionJob cancel(UUID jobId) {
        RequirementExtractionJob job = jobs.findForUpdate(
            jobId, currentTenant.require().tenantId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Requirement extraction job not found: " + jobId));
        access.requireView(job.projectId(), currentTenant.require());
        job.cancel(clock.instant());
        store.event(job.organizationId(), job.id(), "CANCELLED", progress(job),
            "Requirement extraction cancelled", Map.of(), clock.instant());
        return job;
    }

    @Transactional
    public RequirementExtractionJob reprocess(UUID jobId) {
        RequirementExtractionJob previous = get(jobId);
        access.requireView(previous.projectId(), currentTenant.require());
        return enqueue(previous.documentId(), previous.id());
    }

    private RequirementExtractionJob enqueue(UUID documentId, UUID parentJobId) {
        TenantPrincipal principal = currentTenant.require();
        Document document = documents.findByIdAndOrganizationId(documentId, principal.tenantId())
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        access.requireDocumentView(document.projectId(), principal);
        if (!document.includedInAnalysis()) {
            throw new IllegalArgumentException("Document is excluded from analysis");
        }
        if (document.status() != DocumentStatus.READY) {
            throw new IllegalArgumentException(
                "Requirement extraction requires a READY document");
        }
        AnalysisProfile profile = profiles.createSnapshot(documentId);
        UUID terminologySnapshotId = UUID.nameUUIDFromBytes(
            profile.terminologySetIdsJson().getBytes(StandardCharsets.UTF_8));
        UUID correlationId = RequestContext.current().correlationId();
        Instant now = clock.instant();
        RequirementExtractionJob job = jobs.save(new RequirementExtractionJob(
            UUID.randomUUID(), principal.tenantId(), document.projectId(), document.id(),
            profile.documentVersionId(), profile, terminologySnapshotId, correlationId,
            parentJobId, now));
        store.event(job.organizationId(), job.id(), "QUEUED", 0,
            "Requirement extraction queued",
            Map.of("analysisProfileId", profile.id()), now);
        outbox.publish(job.organizationId(), "RequirementExtraction", job.id(),
            "RequirementExtractionRequested", RabbitConfiguration.REQUIREMENT_REQUESTED,
            new ExtractionRequested(job.id(), job.documentId(), job.documentVersionId(),
                job.analysisProfileId()), correlationId);
        return job;
    }

    private int progress(RequirementExtractionJob job) {
        return job.totalClauseCount() == 0 ? 0
            : (int) Math.floor(100d * job.processedClauseCount() / job.totalClauseCount());
    }
}
