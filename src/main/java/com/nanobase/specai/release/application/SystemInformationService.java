package com.nanobase.specai.release.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.pilot.application.SensitiveDataSanitizer;
import com.nanobase.specai.release.api.ReleaseContracts.DiagnosticBundleResponse;
import com.nanobase.specai.release.api.ReleaseContracts.SystemVersionResponse;
import com.nanobase.specai.shared.security.CurrentTenant;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemInformationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final SensitiveDataSanitizer sanitizer;
    private final AuditService audit;
    private final String applicationVersion;
    private final String buildNumber;
    private final String commitHash;
    private final String releaseDate;
    private final String environment;

    public SystemInformationService(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        CurrentTenant currentTenant,
        SensitiveDataSanitizer sanitizer,
        AuditService audit,
        @Value("${specai.release.version:development}") String applicationVersion,
        @Value("${specai.release.build-number:local}") String buildNumber,
        @Value("${specai.release.commit-hash:unknown}") String commitHash,
        @Value("${specai.release.date:unreleased}") String releaseDate,
        @Value("${specai.environment:development}") String environment
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.sanitizer = sanitizer;
        this.audit = audit;
        this.applicationVersion = applicationVersion;
        this.buildNumber = buildNumber;
        this.commitHash = commitHash;
        this.releaseDate = releaseDate;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public SystemVersionResponse version() {
        UUID organizationId = currentTenant.require().tenantId();
        return new SystemVersionResponse(applicationVersion, buildNumber, commitHash,
            releaseDate, scalar("""
                select coalesce(max(version::text), 'NOT_AVAILABLE')
                  from flyway_schema_history where success = true
                """), scalar("""
                select coalesce(max(manifest.manifest_version)::text, 'NOT_AVAILABLE')
                  from release_configuration_manifest manifest
                  join release_record release on release.id = manifest.release_id
                 where release.organization_id = ?
                """, organizationId), scalar("""
                select case when count(*) = 0 then 'NOT_AVAILABLE'
                            else 'public-profile-set@'
                                 || to_char(max(updated_at), 'YYYYMMDDHH24MISS')
                       end
                  from model_profile
                 where active = true
                   and (organization_id = ? or organization_id is null)
                """, organizationId));
    }

    @Transactional
    public DiagnosticBundleResponse diagnosticBundle() {
        var principal = currentTenant.require();
        Instant generatedAt = Instant.now();
        Instant expiresAt = generatedAt.plus(24, ChronoUnit.HOURS);
        ObjectNode manifest = mapper.createObjectNode();
        manifest.set("serviceVersions", mapper.valueToTree(version()));
        manifest.set("healthStates", mapper.valueToTree(Map.of(
            "backend", "UP",
            "database", databaseHealth(),
            "parser", "AUTHORITATIVE_HEALTH_ENDPOINT_REQUIRED",
            "model", "AUTHORITATIVE_HEALTH_ENDPOINT_REQUIRED")));
        manifest.set("sanitizedConfiguration", mapper.valueToTree(Map.of(
            "environment", environment,
            "releaseVersion", applicationVersion,
            "tenantScoped", true)));
        manifest.set("queueMetrics", mapper.valueToTree(Map.of(
            "documentActive", count("""
                select count(*) from document_processing_job
                 where organization_id = ?
                   and status not in ('READY','FAILED','CANCELLED')
                """, principal.tenantId()),
            "documentFailed", count("""
                select count(*) from document_processing_job
                 where organization_id = ? and status = 'FAILED'
                """, principal.tenantId()),
            "outboxPending", count("""
                select count(*) from outbox_event
                 where organization_id = ? and status in ('PENDING','FAILED','CLAIMED')
                """, principal.tenantId()))));
        manifest.set("recentErrorCodes", mapper.valueToTree(jdbc.queryForList("""
            select status as code, count(*) as count
              from document_processing_job
             where organization_id = ? and status in ('FAILED','MANUAL_REVIEW_REQUIRED')
               and updated_at > now() - interval '24 hours'
             group by status order by count(*) desc
            """, principal.tenantId())));
        Runtime runtime = Runtime.getRuntime();
        manifest.set("runtimeSummary", mapper.valueToTree(Map.of(
            "threadCount", ManagementFactory.getThreadMXBean().getThreadCount(),
            "heapUsedBytes", runtime.totalMemory() - runtime.freeMemory(),
            "heapCommittedBytes", runtime.totalMemory(),
            "availableProcessors", runtime.availableProcessors(),
            "uptimeMilliseconds", ManagementFactory.getRuntimeMXBean().getUptime())));
        manifest.set("featureFlags", mapper.valueToTree(jdbc.queryForList("""
            select definition.feature_code, coalesce(assignment.enabled, definition.default_state)
                   as enabled
              from feature_definition definition
              left join feature_assignment assignment
                on assignment.feature_definition_id = definition.id
               and assignment.organization_id = ?
               and assignment.project_id is null
             order by definition.feature_code
            """, principal.tenantId())));
        SensitiveDataSanitizer.SanitizedPayload sanitized = sanitizer.sanitize(manifest);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into diagnostic_bundle_request (
                id, organization_id, requested_by, status, manifest_json,
                content_hash, created_at, expires_at
            ) values (?, ?, ?, 'GENERATED', ?::jsonb, ?, ?, ?)
            """, id, principal.tenantId(), principal.subject(),
            sanitized.value().toString(), sanitized.contentHash(),
            java.sql.Timestamp.from(generatedAt), java.sql.Timestamp.from(expiresAt));
        DiagnosticBundleResponse response = new DiagnosticBundleResponse(id, generatedAt,
            expiresAt, sanitized.contentHash(), sanitized.value());
        audit.record("diagnostic.bundle.generated.v1", "DiagnosticBundle", id,
            null, Map.of("contentHash", sanitized.contentHash(), "expiresAt", expiresAt,
                "removedPaths", sanitized.removedPaths()));
        return response;
    }

    private String databaseHealth() {
        try {
            Integer value = jdbc.queryForObject("select 1", Integer.class);
            return Integer.valueOf(1).equals(value) ? "UP" : "DOWN";
        } catch (RuntimeException unavailable) {
            return "DOWN";
        }
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private String scalar(String sql, Object... arguments) {
        try {
            String value = jdbc.queryForObject(sql, String.class, arguments);
            return value == null ? "NOT_AVAILABLE" : value;
        } catch (RuntimeException unavailable) {
            return "NOT_AVAILABLE";
        }
    }
}
