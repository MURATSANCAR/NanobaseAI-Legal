package com.nanobase.specai.compliance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.api.ExtractionEventStream;
import com.nanobase.specai.integration.outbox.OutboxService;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceJobService {
    private static final Set<String> TERMINAL = Set.of(
        "COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final ProjectAccessService access;
    private final OutboxService outbox;
    private final ExtractionEventStream eventStream;
    private final ComplianceJobTransactionService transactionService;

    public ComplianceJobService(JdbcTemplate jdbc, ObjectMapper mapper,
                                CurrentTenant currentTenant, ProjectAccessService access,
                                OutboxService outbox,
                                ExtractionEventStream eventStream,
                                ComplianceJobTransactionService transactionService) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.access = access;
        this.outbox = outbox;
        this.eventStream = eventStream;
        this.transactionService = transactionService;
    }

    @Transactional
    public Map<String, Object> create(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        Profile profile = latestProfile(principal.tenantId(), projectId);
        UUID retrieval = retrievalPolicy(principal.tenantId());
        UUID matching = policy(principal.tenantId(), "COMPLIANCE_MATCHING");
        UUID comparison = policy(principal.tenantId(), "COMPARISON");
        UUID confidence = policy(principal.tenantId(), "COMPLIANCE_CONFIDENCE");
        UUID prompt = prompt(principal.tenantId(), "COMPLIANCE_EVALUATION");
        UUID terminology = latestTerminologySnapshot(principal.tenantId(), projectId);
        long requirementVersion = jdbc.queryForObject("""
            select coalesce(max(version), 0) from requirement
            where organization_id = ? and project_id = ?
            """, Long.class, principal.tenantId(), projectId);
        int total = jdbc.queryForObject("""
            select count(*) from requirement
            where organization_id = ? and project_id = ?
            """, Integer.class, principal.tenantId(), projectId);
        if (total == 0) {
            throw new IllegalStateException("Project has no extracted requirements");
        }
        Map<String, Object> policyVersions = Map.of(
            "retrieval", retrieval, "matching", matching, "comparison", comparison,
            "confidence", confidence, "prompt", prompt
        );
        UUID snapshotId = UUID.randomUUID();
        String snapshotMaterial = json(Map.of(
            "projectId", projectId,
            "requirementSetVersion", requirementVersion,
            "ontologyVersionId", profile.ontologyVersionId(),
            "terminologySnapshotId", terminology,
            "policyVersions", policyVersions,
            "entityCutoff", java.time.Instant.now().toString()
        ));
        jdbc.update("""
            insert into knowledge_snapshot (
                id, organization_id, project_id, created_at, entity_version_cutoff,
                evidence_version_cutoff, ontology_version_id, terminology_snapshot_id,
                policy_versions_json, content_hash
            ) values (?, ?, ?, now(), now(), now(), ?, ?, ?::jsonb, ?)
            """, snapshotId, principal.tenantId(), projectId, profile.ontologyVersionId(),
            terminology, json(policyVersions), sha256(snapshotMaterial));
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
            insert into compliance_analysis_job (
                id, organization_id, project_id, status, analysis_profile_id,
                requirement_set_version, knowledge_snapshot_id,
                retrieval_policy_version_id, matching_policy_version_id,
                comparison_policy_version_id, confidence_policy_version_id,
                prompt_package_version_id, model_routing_policy_id,
                total_requirement_count, created_by, created_at, updated_at
            ) values (?, ?, ?, 'QUEUED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, jobId, principal.tenantId(), projectId, profile.id(),
            requirementVersion, snapshotId, retrieval, matching, comparison, confidence,
            prompt, profile.modelRoutingPolicyId(), total, principal.subject());
        List<UUID> requirements = jdbc.query("""
            select id from requirement
            where organization_id = ? and project_id = ?
            order by created_at, id
            """, (result, row) -> result.getObject(1, UUID.class),
            principal.tenantId(), projectId);
        for (UUID requirementId : requirements) {
            jdbc.update("""
                insert into requirement_matching_task (
                    id, organization_id, compliance_job_id, requirement_id,
                    status, created_at, updated_at
                ) values (?, ?, ?, ?, 'QUEUED', now(), now())
                """, UUID.randomUUID(), principal.tenantId(), jobId, requirementId);
        }
        event(principal.tenantId(), jobId, "QUEUED", 0,
            "Compliance analysis queued", Map.of("requirementCount", total));
        UUID correlationId = RequestContext.current().correlationId();
        outbox.publish(principal.tenantId(), "ComplianceAnalysis", jobId,
            "ComplianceAnalysisRequested", "compliance.analysis.requested.v1",
            new ComplianceRequested(jobId, projectId, snapshotId, correlationId),
            correlationId);
        return get(jobId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID jobId) {
        UUID organizationId = currentTenant.require().tenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, project_id, status, analysis_profile_id, requirement_set_version,
                   knowledge_snapshot_id, retrieval_policy_version_id,
                   matching_policy_version_id, comparison_policy_version_id,
                   confidence_policy_version_id, prompt_package_version_id,
                   model_routing_policy_id, total_requirement_count,
                   processed_requirement_count, completed_count, manual_review_count,
                   failed_count, started_at, completed_at, created_by,
                   created_at, updated_at, version,
                   claimed_by, claimed_at, heartbeat_at, lease_expires_at,
                   cancel_requested_at, cancel_requested_by, cancel_reason,
                   attempt_count, last_error_code, last_error_message
            from compliance_analysis_job where id = ? and organization_id = ?
            """, jobId, organizationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Compliance analysis job not found");
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(UUID jobId) {
        UUID organizationId = currentTenant.require().tenantId();
        get(jobId);
        return jdbc.queryForList("""
            select id, event_type as "eventType", progress, message,
                   metadata_json::text as "metadataJson", occurred_at as "occurredAt"
            from compliance_analysis_event
            where organization_id = ? and compliance_job_id = ?
            order by occurred_at, id
            """, organizationId, jobId);
    }

    @Transactional
    public Map<String, Object> cancel(UUID jobId) {
        TenantPrincipal principal = currentTenant.require();
        UUID organizationId = principal.tenantId();
        Instant cancelStarted = Instant.now();
        String status = String.valueOf(get(jobId).get("status"));
        if (TERMINAL.contains(status)) {
            throw new IllegalStateException("Compliance analysis job is already terminal");
        }
        // Short TX cooperative cancel — must not wait on a long job-row lock.
        var snapshot = transactionService.requestCancel(
            organizationId, jobId, principal.subject(), "user_cancel");
        if ("CANCELLED".equals(snapshot.status())) {
            event(organizationId, jobId, "CANCELLED", 100,
                "Compliance analysis cancelled", Map.of(
                    "cancelRequestedAt", snapshot.cancelRequestedAt() == null
                        ? null : snapshot.cancelRequestedAt().toString()));
        } else {
            event(organizationId, jobId, "CANCEL_REQUESTED", 0,
                "Compliance analysis cancellation requested", Map.of(
                    "status", snapshot.status(),
                    "cancelRequestedAt", snapshot.cancelRequestedAt() == null
                        ? null : snapshot.cancelRequestedAt().toString()));
        }
        Map<String, Object> result = get(jobId);
        result.put("cancelLatencyMs",
            java.time.Duration.between(cancelStarted, Instant.now()).toMillis());
        return result;
    }

    @Transactional
    public Map<String, Object> retryFailed(UUID jobId) {
        TenantPrincipal principal = currentTenant.require();
        Map<String, Object> existing = get(jobId);
        int changed = jdbc.update("""
            update requirement_matching_task
            set status = 'QUEUED', error_code = null, error_message = null,
                started_at = null, completed_at = null, updated_at = now(),
                version = version + 1
            where compliance_job_id = ? and organization_id = ? and status = 'FAILED'
            """, jobId, principal.tenantId());
        if (changed == 0) {
            throw new IllegalStateException("Compliance analysis has no failed tasks");
        }
        jdbc.update("""
            update compliance_analysis_job
            set status = 'QUEUED', failed_count = 0, completed_at = null,
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, jobId, principal.tenantId());
        UUID correlationId = RequestContext.current().correlationId();
        outbox.publish(principal.tenantId(), "ComplianceAnalysis", jobId,
            "ComplianceAnalysisRequested", "compliance.analysis.requested.v1",
            new ComplianceRequested(jobId, (UUID) existing.get("project_id"),
                (UUID) existing.get("knowledge_snapshot_id"), correlationId),
            correlationId);
        return get(jobId);
    }

    void event(UUID organizationId, UUID jobId, String type, int progress,
               String message, Object metadata) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
            insert into compliance_analysis_event (
                id, organization_id, compliance_job_id, event_type, progress,
                message, metadata_json, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, now())
            """, eventId, organizationId, jobId, type, progress,
            message, json(metadata));
        eventStream.publish(jobId, type, Map.of(
            "id", eventId,
            "eventType", type,
            "progress", progress,
            "message", message,
            "metadata", metadata == null ? Map.of() : metadata
        ));
        if (TERMINAL.contains(type)) {
            eventStream.complete(jobId);
        }
    }

    private Profile latestProfile(UUID organizationId, UUID projectId) {
        return jdbc.query("""
            select id, ontology_version_id, model_routing_policy_id
            from analysis_profile where organization_id = ? and project_id = ?
            order by created_at desc limit 1
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException(
                        "Project has no analysis profile snapshot");
                }
                return new Profile(result.getObject(1, UUID.class),
                    result.getObject(2, UUID.class), result.getObject(3, UUID.class));
            }, organizationId, projectId);
    }

    private UUID latestTerminologySnapshot(UUID organizationId, UUID projectId) {
        List<UUID> ids = jdbc.query("""
            select terminology_snapshot_id from requirement_extraction_job
            where organization_id = ? and project_id = ?
            order by created_at desc limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            organizationId, projectId);
        return ids.isEmpty()
            ? UUID.nameUUIDFromBytes(("terminology:" + projectId)
                .getBytes(StandardCharsets.UTF_8))
            : ids.getFirst();
    }

    private UUID retrievalPolicy(UUID organizationId) {
        return requiredId(jdbc.query("""
            select version.id
            from retrieval_policy_definition definition
            join retrieval_policy_version version on version.id = definition.active_version_id
            where version.status = 'ACTIVE'
              and (definition.organization_id = ? or definition.organization_id is null)
            order by (definition.organization_id is not null) desc limit 1
            """, (result, row) -> result.getObject(1, UUID.class), organizationId),
            "Active retrieval policy is not configured");
    }

    private UUID policy(UUID organizationId, String type) {
        return requiredId(jdbc.query("""
            select version.id
            from policy_definition definition
            join policy_version version on version.policy_definition_id = definition.id
            where definition.policy_type = ? and version.status = 'ACTIVE'
              and (definition.organization_id = ? or definition.organization_id is null)
            order by (definition.organization_id is not null) desc,
                     version.version_number desc limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            type, organizationId), "Active policy is not configured: " + type);
    }

    private UUID prompt(UUID organizationId, String taskType) {
        return requiredId(jdbc.query("""
            select version.id
            from prompt_package package
            join prompt_package_version version on version.id = package.active_version_id
            where package.task_type = ? and version.status = 'ACTIVE'
              and (package.organization_id = ? or package.organization_id is null)
            order by (package.organization_id is not null) desc limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            taskType, organizationId), "Active compliance prompt is not configured");
    }

    private UUID requiredId(List<UUID> ids, String message) {
        if (ids.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return ids.getFirst();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Compliance snapshot is not serializable",
                exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ComplianceRequested(UUID jobId, UUID projectId, UUID snapshotId,
                                      UUID correlationId) {
    }

    private record Profile(UUID id, UUID ontologyVersionId,
                           UUID modelRoutingPolicyId) {
    }
}
