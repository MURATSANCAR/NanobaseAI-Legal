package com.nanobase.specai.operations.application;

import com.nanobase.specai.shared.security.CurrentTenant;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsReadinessService {
    private final JdbcTemplate jdbc;
    private final RabbitAdmin rabbit;
    private final CurrentTenant currentTenant;
    private final String releaseVersion;
    private final String environment;

    public OperationsReadinessService(
        JdbcTemplate jdbc,
        RabbitAdmin rabbit,
        CurrentTenant currentTenant,
        @Value("${specai.release.version:development}") String releaseVersion,
        @Value("${specai.environment:development}") String environment
    ) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.currentTenant = currentTenant;
        this.releaseVersion = releaseVersion;
        this.environment = environment;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        var principal = currentTenant.require();
        Map<String, ServiceStatus> services = new LinkedHashMap<>();
        services.put("backend", new ServiceStatus("UP", "request served"));
        services.put("database", databaseStatus());
        services.put("rabbitmq", rabbitStatus());
        services.put("parser", new ServiceStatus("UNKNOWN",
            "readiness actuator contains authoritative dependency health"));
        services.put("modelRuntime", new ServiceStatus("UNKNOWN",
            "runtime profile health is reported by the AI orchestrator"));
        long activeJobs = count("""
            select count(*) from document_processing_job
             where organization_id = ?
               and status not in ('READY', 'FAILED', 'CANCELLED')
            """, principal.tenantId());
        long failedJobs = count("""
            select count(*) from document_processing_job
             where organization_id = ? and status = 'FAILED'
            """, principal.tenantId());
        long outboxPending = count("""
            select count(*) from outbox_event
             where organization_id = ? and status in ('PENDING', 'PROCESSING')
            """, principal.tenantId());
        long outboxDead = count("""
            select count(*) from outbox_event
             where organization_id = ? and status = 'FAILED'
            """, principal.tenantId());
        long securityFailures = count("""
            select count(*) from file_security_assessment
             where organization_id = ?
               and status in ('MALICIOUS', 'SECURITY_SCAN_FAILED',
                              'MANUAL_SECURITY_REVIEW', 'REJECTED')
               and assessed_at > now() - interval '24 hours'
            """, principal.tenantId());
        Boolean auditValid = jdbc.queryForObject(
            "select valid from verify_audit_chain(?)", Boolean.class, principal.tenantId());
        return new Snapshot(environment, releaseVersion, Instant.now(), Map.copyOf(services),
            new Workload(activeJobs, failedJobs, outboxPending, outboxDead),
            new Security(Boolean.TRUE.equals(auditValid), securityFailures),
            new Recovery("NOT_VERIFIED", null, "NOT_VERIFIED", null),
            blockers(auditValid, securityFailures, outboxDead));
    }

    @Transactional(readOnly = true)
    public AiQualitySnapshot aiQuality() {
        var principal = currentTenant.require();
        List<Map<String, Object>> runs = jdbc.queryForList("""
            select id, status, candidate_type, metrics_json, comparison_json,
                   quality_gate_result_json, created_at, completed_at
              from evaluation_run
             where organization_id = ?
             order by created_at desc
             limit 20
            """, principal.tenantId());
        long pendingReviews = count("""
            select count(*) from prompt_security_assessment
             where organization_id = ? and review_status not in ('APPROVED', 'DISMISSED')
            """, principal.tenantId());
        long shadowRuns = count("""
            select count(*) from shadow_execution
             where organization_id = ? and status in ('QUEUED', 'RUNNING')
            """, principal.tenantId());
        long activeCanaries = count("""
            select count(*) from canary_assignment
             where organization_id = ? and status = 'ACTIVE'
            """, principal.tenantId());
        return new AiQualitySnapshot(runs, pendingReviews, shadowRuns, activeCanaries);
    }

    private ServiceStatus databaseStatus() {
        try {
            Integer value = jdbc.queryForObject("select 1", Integer.class);
            return new ServiceStatus(Integer.valueOf(1).equals(value) ? "UP" : "DOWN",
                "database connectivity");
        } catch (RuntimeException unavailable) {
            return new ServiceStatus("DOWN", "database connectivity failed");
        }
    }

    private ServiceStatus rabbitStatus() {
        try {
            QueueInformation queue = rabbit.getQueueInfo("document-processing.request");
            return queue == null
                ? new ServiceStatus("DOWN", "processing queue is missing")
                : new ServiceStatus("UP", "depth=" + queue.getMessageCount());
        } catch (RuntimeException unavailable) {
            return new ServiceStatus("DOWN", "broker management query failed");
        }
    }

    private long count(String sql, Object argument) {
        Long value = jdbc.queryForObject(sql, Long.class, argument);
        return value == null ? 0 : value;
    }

    private List<String> blockers(Boolean auditValid, long securityFailures, long outboxDead) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (!Boolean.TRUE.equals(auditValid)) {
            values.add("AUDIT_INTEGRITY");
        }
        if (securityFailures > 0) {
            values.add("FILE_SECURITY_REVIEW");
        }
        if (outboxDead > 0) {
            values.add("OUTBOX_DEAD_EVENTS");
        }
        values.add("RESTORE_EVIDENCE_REQUIRED");
        return List.copyOf(values);
    }

    public record Snapshot(String environment, String releaseVersion, Instant generatedAt,
                           Map<String, ServiceStatus> services, Workload workload,
                           Security security, Recovery recovery, List<String> blockers) {
    }

    public record ServiceStatus(String status, String detail) {
    }

    public record Workload(long activeJobs, long failedJobs, long outboxPending, long outboxDead) {
    }

    public record Security(boolean auditIntegrityValid, long securityReviewCount24h) {
    }

    public record Recovery(String lastBackupStatus, Instant lastBackupAt,
                           String lastRestoreStatus, Instant lastRestoreAt) {
    }

    public record AiQualitySnapshot(List<Map<String, Object>> recentEvaluationRuns,
                                    long pendingPromptSecurityReviews,
                                    long activeShadowRuns, long activeCanaries) {
    }
}
