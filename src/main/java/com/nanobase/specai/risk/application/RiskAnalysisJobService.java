package com.nanobase.specai.risk.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.integration.outbox.RabbitConfiguration;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.integration.RiskEvents.RiskAnalysisRequested;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import com.nanobase.specai.tender.application.ProjectAccessService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskAnalysisJobService {
    private final RiskCatalogPort catalog;
    private final RiskPersistencePort store;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    public RiskAnalysisJobService(RiskCatalogPort catalog, RiskPersistencePort store,
                                  ProjectAccessService access, CurrentTenant currentTenant,
                                  OutboxService outbox, ObjectMapper mapper) {
        this.catalog = catalog;
        this.store = store;
        this.access = access;
        this.currentTenant = currentTenant;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Object> create(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        RiskProfileInputs inputs = catalog.resolveProfileInputs(principal.tenantId(), projectId);
        JsonNode snapshot = mapper.valueToTree(inputs);
        UUID profileId = store.createProfile(principal.tenantId(), projectId, inputs,
            snapshot, sha256(snapshot.toString()));
        long requirementSetVersion = store.requirements(principal.tenantId(), projectId)
            .stream().mapToLong(candidate -> 1).sum();
        UUID knowledgeSnapshot = store.latestKnowledgeSnapshot(principal.tenantId(), projectId)
            .orElse(null);
        UUID jobId = store.createJob(principal.tenantId(), projectId, profileId,
            knowledgeSnapshot, requirementSetVersion, principal.subject());
        store.event(principal.tenantId(), jobId, "QUEUED", 0,
            "Risk analysis queued", mapper.valueToTree(Map.of("profileId", profileId)));
        outbox.publish(principal.tenantId(), "RiskAnalysis", jobId,
            "risk.analysis.requested.v1", RabbitConfiguration.RISK_ANALYSIS_REQUESTED,
            new RiskAnalysisRequested(jobId, projectId, profileId),
            RequestContext.current().correlationId());
        return get(jobId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID jobId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> job = store.job(principal.tenantId(), jobId)
            .orElseThrow(() -> new IllegalArgumentException("Risk analysis job not found"));
        access.requireView((UUID) job.get("projectId"), principal);
        return job;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(UUID jobId) {
        Map<String, Object> job = get(jobId);
        return store.jobEvents(currentTenant.require().tenantId(), (UUID) job.get("id"));
    }

    @Transactional
    public Map<String, Object> cancel(UUID jobId) {
        Map<String, Object> job = get(jobId);
        store.cancelJob(currentTenant.require().tenantId(), (UUID) job.get("id"));
        return get(jobId);
    }

    @Transactional
    public Map<String, Object> retryFailed(UUID jobId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> job = get(jobId);
        String status = String.valueOf(job.get("status"));
        if (!"FAILED".equals(status) && !"COMPLETED_WITH_ERRORS".equals(status)) {
            throw new IllegalArgumentException("Only failed risk analyses can be retried");
        }
        outbox.publish(principal.tenantId(), "RiskAnalysis", jobId,
            "risk.analysis.requested.v1", RabbitConfiguration.RISK_ANALYSIS_REQUESTED,
            new RiskAnalysisRequested(jobId, (UUID) job.get("projectId"),
                (UUID) job.get("riskAnalysisProfileId")),
            RequestContext.current().correlationId());
        return job;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
