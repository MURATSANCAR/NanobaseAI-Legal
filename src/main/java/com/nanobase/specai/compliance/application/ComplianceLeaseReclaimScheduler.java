package com.nanobase.specai.compliance.application;

import com.nanobase.specai.compliance.application.ComplianceJobService.ComplianceRequested;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Requeues compliance jobs whose worker lease expired (crash / heartbeat stop).
 * Recovery model: clear expired claim + republish ComplianceAnalysisRequested via outbox.
 */
@Component
public class ComplianceLeaseReclaimScheduler {
    private static final Logger log = LoggerFactory.getLogger(ComplianceLeaseReclaimScheduler.class);

    private final JdbcTemplate jdbc;
    private final TenantDatabaseContext tenantDatabase;
    private final TransactionTemplate transactions;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;
    private final int batchSize;
    private final int maxAttempts;

    public ComplianceLeaseReclaimScheduler(
        JdbcTemplate jdbc,
        TenantDatabaseContext tenantDatabase,
        PlatformTransactionManager transactionManager,
        OutboxService outbox,
        PlatformMetrics metrics,
        @Value("${specai.compliance.reclaim-batch-size:10}") int batchSize,
        @Value("${specai.compliance.max-attempt-count:5}") int maxAttempts
    ) {
        this.jdbc = jdbc;
        this.tenantDatabase = tenantDatabase;
        this.transactions = new TransactionTemplate(transactionManager);
        this.outbox = outbox;
        this.metrics = metrics;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${specai.compliance.reclaim-interval-ms:30000}")
    public void reclaimExpiredLeases() {
        List<UUID> organizations =
            jdbc.queryForList("select id from organization order by id", UUID.class);
        for (UUID organizationId : organizations) {
            try {
                reclaimForOrganization(organizationId);
            } catch (RuntimeException failure) {
                log.warn("event=COMPLIANCE_RECLAIM_ORG_FAILED organizationId={} error={}",
                    organizationId, failure.toString());
            }
        }
    }

    private void reclaimForOrganization(UUID organizationId) {
        List<ExpiredJob> expired = transactions.execute(status -> {
            tenantDatabase.apply(organizationId);
            return jdbc.query("""
                select id, project_id, knowledge_snapshot_id, attempt_count,
                       claimed_by, lease_generation
                  from compliance_analysis_job
                 where organization_id = ?
                   and status = 'RUNNING'
                   and cancel_requested_at is null
                   and lease_expires_at is not null
                   and lease_expires_at < clock_timestamp()
                 order by lease_expires_at
                 limit ?
                """, (rs, row) -> new ExpiredJob(
                rs.getObject("id", UUID.class),
                organizationId,
                rs.getObject("project_id", UUID.class),
                rs.getObject("knowledge_snapshot_id", UUID.class),
                rs.getInt("attempt_count"),
                rs.getString("claimed_by"),
                rs.getLong("lease_generation")
            ), organizationId, batchSize);
        });
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (ExpiredJob job : expired) {
            transactions.executeWithoutResult(status -> reclaimOne(job));
        }
    }

    private void reclaimOne(ExpiredJob job) {
        tenantDatabase.apply(job.organizationId());
        if (job.attemptCount() >= maxAttempts) {
            jdbc.update("""
                update requirement_matching_task
                   set status = 'FAILED',
                       error_code = 'WORKER_REPEATEDLY_INTERRUPTED',
                       error_message = 'Maximum reclaim attempts exceeded',
                       last_error_code = 'WORKER_REPEATEDLY_INTERRUPTED',
                       completed_at = clock_timestamp(),
                       updated_at = clock_timestamp(),
                       version = version + 1
                 where compliance_job_id = ?
                   and organization_id = ?
                   and status in ('QUEUED', 'RUNNING', 'READY_FOR_MODEL')
                """, job.jobId(), job.organizationId());
            jdbc.update("""
                update compliance_analysis_job
                   set status = 'FAILED',
                       last_error_code = 'WORKER_REPEATEDLY_INTERRUPTED',
                       last_error_message = 'Maximum reclaim attempts exceeded',
                       completed_at = clock_timestamp(),
                       lease_expires_at = null,
                       claimed_by = null,
                       updated_at = clock_timestamp(),
                       version = version + 1
                 where id = ?
                   and organization_id = ?
                   and status = 'RUNNING'
                """, job.jobId(), job.organizationId());
            log.warn("event=COMPLIANCE_RECLAIM_EXHAUSTED jobId={} attemptCount={}",
                job.jobId(), job.attemptCount());
            return;
        }
        int cleared = jdbc.update("""
            update compliance_analysis_job
               set status = 'QUEUED',
                   claimed_by = null,
                   lease_expires_at = null,
                   heartbeat_at = null,
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and status = 'RUNNING'
               and lease_expires_at is not null
               and lease_expires_at < clock_timestamp()
            """, job.jobId(), job.organizationId());
        if (cleared == 0) {
            return;
        }
        jdbc.update("""
            update requirement_matching_task
               set status = 'QUEUED',
                   claimed_by = null,
                   lease_expires_at = null,
                   heartbeat_at = null,
                   updated_at = clock_timestamp(),
                   version = version + 1
             where compliance_job_id = ?
               and organization_id = ?
               and status in ('RUNNING', 'READY_FOR_MODEL', 'CLAIMED', 'WAITING_FOR_SLOT')
            """, job.jobId(), job.organizationId());
        UUID correlationId = UUID.randomUUID();
        outbox.publish(job.organizationId(), "ComplianceAnalysis", job.jobId(),
            "ComplianceAnalysisRequested", "compliance.analysis.requested.v1",
            new ComplianceRequested(job.jobId(), job.projectId(), job.snapshotId(), correlationId),
            correlationId);
        metrics.complianceJobReclaimed();
        log.info("event=COMPLIANCE_JOB_RECLAIM_SCHEDULED jobId={} previousWorker={} "
                + "previousLeaseGeneration={} attemptCount={} correlationId={}",
            job.jobId(), job.claimedBy(), job.leaseGeneration(), job.attemptCount(),
            correlationId);
    }

    private record ExpiredJob(
        UUID jobId,
        UUID organizationId,
        UUID projectId,
        UUID snapshotId,
        int attemptCount,
        String claimedBy,
        long leaseGeneration
    ) {
    }
}
