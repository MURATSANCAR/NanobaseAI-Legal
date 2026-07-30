package com.nanobase.specai.compliance.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Short-lived transactions for compliance job/task claim, heartbeat, cancel and
 * finalization. Must never call the LLM, wait on slots, or hold locks across
 * external I/O.
 */
@Service
public class ComplianceJobTransactionService {
    private static final Logger log = LoggerFactory.getLogger(ComplianceJobTransactionService.class);
    public static final Duration DEFAULT_LEASE = Duration.ofMinutes(15);

    private final JdbcTemplate jdbc;

    public ComplianceJobTransactionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum ClaimOutcome {
        CLAIMED,
        ALREADY_COMPLETED,
        ALREADY_CANCELLED,
        CLAIMED_BY_OTHER_WORKER,
        LEASE_NOT_EXPIRED,
        NOT_FOUND
    }

    public record JobClaimResult(
        ClaimOutcome outcome,
        UUID jobId,
        UUID projectId,
        String status,
        int totalRequirementCount,
        UUID analysisProfileId,
        UUID knowledgeSnapshotId,
        UUID retrievalPolicyVersionId,
        UUID matchingPolicyVersionId,
        UUID comparisonPolicyVersionId,
        UUID confidencePolicyVersionId,
        UUID promptPackageVersionId,
        Instant claimedAt,
        int attemptCount
    ) {
        public boolean claimed() {
            return outcome == ClaimOutcome.CLAIMED;
        }
    }

    public record TaskClaimResult(
        boolean claimed,
        UUID taskId,
        UUID requirementId,
        UUID targetEntityId,
        String status
    ) {
    }

    public record CancellationSnapshot(
        boolean cancelRequested,
        Instant cancelRequestedAt,
        String status
    ) {
    }

    public record JobFinalizationResult(
        String status,
        int completed,
        int failed,
        int reviews,
        int processed
    ) {
    }

    @Transactional
    public JobClaimResult claimJob(UUID organizationId, UUID jobId, String workerId,
                                   Instant leaseExpiresAt) {
        Instant now = Instant.now();
        List<JobClaimResult> claimed = jdbc.query("""
            update compliance_analysis_job
               set status = 'RUNNING',
                   claimed_by = ?,
                   claimed_at = clock_timestamp(),
                   heartbeat_at = clock_timestamp(),
                   lease_expires_at = ?,
                   started_at = coalesce(started_at, clock_timestamp()),
                   attempt_count = attempt_count + 1,
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and (
                    status = 'QUEUED'
                    or (
                        status = 'RUNNING'
                        and lease_expires_at is not null
                        and lease_expires_at < clock_timestamp()
                    )
               )
         returning id, project_id, status, total_requirement_count, analysis_profile_id,
                   knowledge_snapshot_id, retrieval_policy_version_id,
                   matching_policy_version_id, comparison_policy_version_id,
                   confidence_policy_version_id, prompt_package_version_id, claimed_at,
                   attempt_count
            """, (rs, row) -> new JobClaimResult(
            ClaimOutcome.CLAIMED,
            rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getString("status"),
            rs.getInt("total_requirement_count"),
            rs.getObject("analysis_profile_id", UUID.class),
            rs.getObject("knowledge_snapshot_id", UUID.class),
            rs.getObject("retrieval_policy_version_id", UUID.class),
            rs.getObject("matching_policy_version_id", UUID.class),
            rs.getObject("comparison_policy_version_id", UUID.class),
            rs.getObject("confidence_policy_version_id", UUID.class),
            rs.getObject("prompt_package_version_id", UUID.class),
            rs.getTimestamp("claimed_at").toInstant(),
            rs.getInt("attempt_count")
        ), workerId, java.sql.Timestamp.from(leaseExpiresAt), jobId, organizationId);
        if (!claimed.isEmpty()) {
            log.info("event=COMPLIANCE_JOB_CLAIMED jobId={} workerId={} leaseExpiresAt={}",
                jobId, workerId, leaseExpiresAt);
            log.info("event=COMPLIANCE_JOB_RUNNING_COMMITTED jobId={} workerId={}",
                jobId, workerId);
            return claimed.getFirst();
        }
        return diagnoseJobClaim(organizationId, jobId, workerId, now);
    }

    @Transactional(readOnly = true)
    public Optional<JobClaimResult> loadRunningJob(UUID organizationId, UUID jobId) {
        List<JobClaimResult> rows = jdbc.query("""
            select id, project_id, status, total_requirement_count, analysis_profile_id,
                   knowledge_snapshot_id, retrieval_policy_version_id,
                   matching_policy_version_id, comparison_policy_version_id,
                   confidence_policy_version_id, prompt_package_version_id, claimed_at,
                   attempt_count
              from compliance_analysis_job
             where id = ? and organization_id = ?
            """, (rs, row) -> new JobClaimResult(
            ClaimOutcome.CLAIMED,
            rs.getObject("id", UUID.class),
            rs.getObject("project_id", UUID.class),
            rs.getString("status"),
            rs.getInt("total_requirement_count"),
            rs.getObject("analysis_profile_id", UUID.class),
            rs.getObject("knowledge_snapshot_id", UUID.class),
            rs.getObject("retrieval_policy_version_id", UUID.class),
            rs.getObject("matching_policy_version_id", UUID.class),
            rs.getObject("comparison_policy_version_id", UUID.class),
            rs.getObject("confidence_policy_version_id", UUID.class),
            rs.getObject("prompt_package_version_id", UUID.class),
            rs.getTimestamp("claimed_at") == null
                ? null : rs.getTimestamp("claimed_at").toInstant(),
            rs.getInt("attempt_count")
        ), jobId, organizationId);
        return rows.stream().findFirst();
    }

    @Transactional
    public TaskClaimResult claimTask(UUID organizationId, UUID jobId, UUID taskId,
                                     String workerId, Instant leaseExpiresAt) {
        List<TaskClaimResult> claimed = jdbc.query("""
            update requirement_matching_task
               set status = 'RUNNING',
                   claimed_by = ?,
                   claimed_at = clock_timestamp(),
                   heartbeat_at = clock_timestamp(),
                   lease_expires_at = ?,
                   started_at = coalesce(started_at, clock_timestamp()),
                   attempt_count = attempt_count + 1,
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and compliance_job_id = ?
               and (
                    status = 'QUEUED'
                    or (
                        status = 'RUNNING'
                        and lease_expires_at is not null
                        and lease_expires_at < clock_timestamp()
                    )
               )
         returning id, requirement_id, status
            """, (rs, row) -> new TaskClaimResult(
            true,
            rs.getObject("id", UUID.class),
            rs.getObject("requirement_id", UUID.class),
            null,
            rs.getString("status")
        ), workerId, java.sql.Timestamp.from(leaseExpiresAt), taskId, organizationId, jobId);
        if (!claimed.isEmpty()) {
            log.info("event=COMPLIANCE_TASK_CLAIMED jobId={} taskId={} workerId={}",
                jobId, taskId, workerId);
            return claimed.getFirst();
        }
        return new TaskClaimResult(false, taskId, null, null, "UNCLAIMED");
    }

    @Transactional
    public void heartbeat(UUID organizationId, UUID jobId, UUID taskId, String workerId,
                          Instant leaseExpiresAt) {
        int jobUpdated = jdbc.update("""
            update compliance_analysis_job
               set heartbeat_at = clock_timestamp(),
                   lease_expires_at = ?,
                   updated_at = clock_timestamp()
             where id = ?
               and organization_id = ?
               and status = 'RUNNING'
               and claimed_by = ?
            """, java.sql.Timestamp.from(leaseExpiresAt), jobId, organizationId, workerId);
        if (taskId != null) {
            jdbc.update("""
                update requirement_matching_task
                   set heartbeat_at = clock_timestamp(),
                       lease_expires_at = ?,
                       updated_at = clock_timestamp()
                 where id = ?
                   and organization_id = ?
                   and compliance_job_id = ?
                   and status = 'RUNNING'
                   and claimed_by = ?
                """, java.sql.Timestamp.from(leaseExpiresAt), taskId, organizationId, jobId,
                workerId);
        }
        if (jobUpdated > 0) {
            log.info("event=COMPLIANCE_HEARTBEAT_UPDATED jobId={} taskId={} workerId={} "
                + "leaseExpiresAt={}", jobId, taskId, workerId, leaseExpiresAt);
        }
    }

    @Transactional
    public CancellationSnapshot requestCancel(UUID organizationId, UUID jobId,
                                              String actor, String reason) {
        // Cooperative cancel: never wait on a long FOR UPDATE held by LLM work.
        int updated = jdbc.update("""
            update compliance_analysis_job
               set cancel_requested_at = coalesce(cancel_requested_at, clock_timestamp()),
                   cancel_requested_by = coalesce(cancel_requested_by, ?),
                   cancel_reason = coalesce(cancel_reason, ?),
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and status not in ('COMPLETED', 'FAILED', 'CANCELLED', 'PARTIALLY_COMPLETED')
            """, actor, truncate(reason), jobId, organizationId);
        // If still QUEUED (not yet claimed), cancel immediately.
        jdbc.update("""
            update compliance_analysis_job
               set status = 'CANCELLED',
                   completed_at = clock_timestamp(),
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and status = 'QUEUED'
            """, jobId, organizationId);
        jdbc.update("""
            update requirement_matching_task
               set status = 'CANCELLED',
                   completed_at = clock_timestamp(),
                   updated_at = clock_timestamp(),
                   version = version + 1
             where compliance_job_id = ?
               and organization_id = ?
               and status in ('QUEUED', 'RUNNING')
               and exists (
                    select 1 from compliance_analysis_job job
                     where job.id = compliance_job_id
                       and job.organization_id = ?
                       and job.status = 'CANCELLED'
               )
            """, jobId, organizationId, organizationId);
        log.info("event=COMPLIANCE_CANCEL_REQUESTED jobId={} actor={} updated={}",
            jobId, actor, updated);
        return cancellationState(organizationId, jobId);
    }

    @Transactional(readOnly = true)
    public CancellationSnapshot cancellationState(UUID organizationId, UUID jobId) {
        return jdbc.query("""
            select status, cancel_requested_at
              from compliance_analysis_job
             where id = ? and organization_id = ?
            """, rs -> {
                if (!rs.next()) {
                    return new CancellationSnapshot(false, null, "NOT_FOUND");
                }
                Instant requested = rs.getTimestamp("cancel_requested_at") == null
                    ? null : rs.getTimestamp("cancel_requested_at").toInstant();
                String status = rs.getString("status");
                boolean cancelRequested = requested != null
                    || "CANCELLED".equals(status);
                return new CancellationSnapshot(cancelRequested, requested, status);
            }, jobId, organizationId);
    }

    @Transactional
    public void cancelRemainingTasks(UUID organizationId, UUID jobId) {
        jdbc.update("""
            update requirement_matching_task
               set status = 'CANCELLED',
                   completed_at = clock_timestamp(),
                   updated_at = clock_timestamp(),
                   version = version + 1
             where compliance_job_id = ?
               and organization_id = ?
               and status in ('QUEUED', 'RUNNING')
            """, jobId, organizationId);
        jdbc.update("""
            update compliance_analysis_job
               set status = 'CANCELLED',
                   completed_at = clock_timestamp(),
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and status not in ('COMPLETED', 'FAILED', 'CANCELLED', 'PARTIALLY_COMPLETED')
            """, jobId, organizationId);
        log.info("event=COMPLIANCE_JOB_CANCELLED jobId={}", jobId);
    }

    @Transactional
    public JobFinalizationResult finalizeJob(UUID organizationId, UUID jobId) {
        // Aggregate from DB, not in-memory counters.
        var counts = jdbc.query("""
            select
              count(*) filter (where status = 'COMPLETED') as completed,
              count(*) filter (where status = 'FAILED') as failed,
              count(*) filter (where status = 'CANCELLED') as cancelled,
              count(*) filter (where status in ('QUEUED', 'RUNNING')) as active,
              count(*) as total
              from requirement_matching_task
             where compliance_job_id = ? and organization_id = ?
            """, rs -> {
                rs.next();
                return new int[]{
                    rs.getInt("completed"),
                    rs.getInt("failed"),
                    rs.getInt("cancelled"),
                    rs.getInt("active"),
                    rs.getInt("total")
                };
            }, jobId, organizationId);
        CancellationSnapshot cancel = cancellationState(organizationId, jobId);
        if (counts[3] > 0 && !cancel.cancelRequested()) {
            throw new IllegalStateException("AGGREGATION_INCOMPLETE");
        }
        String terminal;
        if (cancel.cancelRequested() || counts[2] > 0 && counts[0] == 0 && counts[1] == 0) {
            terminal = "CANCELLED";
        } else if (counts[0] == 0 && counts[1] > 0) {
            terminal = "FAILED";
        } else if (counts[1] > 0 || counts[2] > 0) {
            terminal = "PARTIALLY_COMPLETED";
        } else {
            terminal = "COMPLETED";
        }
        int processed = counts[0] + counts[1] + counts[2];
        jdbc.update("""
            update compliance_analysis_job
               set status = ?,
                   processed_requirement_count = ?,
                   completed_count = ?,
                   failed_count = ?,
                   completed_at = clock_timestamp(),
                   claimed_by = null,
                   lease_expires_at = null,
                   updated_at = clock_timestamp(),
                   version = version + 1
             where id = ?
               and organization_id = ?
               and status not in ('COMPLETED', 'FAILED', 'CANCELLED', 'PARTIALLY_COMPLETED')
            """, terminal, processed, counts[0], counts[1], jobId, organizationId);
        log.info("event=COMPLIANCE_JOB_{} jobId={} completed={} failed={} cancelled={}",
            terminal, jobId, counts[0], counts[1], counts[2]);
        return new JobFinalizationResult(terminal, counts[0], counts[1], 0, processed);
    }

    @Transactional(readOnly = true)
    public List<UUID> loadPendingTaskIds(UUID organizationId, UUID jobId) {
        return jdbc.query("""
            select id from requirement_matching_task
             where organization_id = ? and compliance_job_id = ?
               and status = 'QUEUED'
             order by created_at, id
            """, (rs, row) -> rs.getObject(1, UUID.class), organizationId, jobId);
    }

    private JobClaimResult diagnoseJobClaim(UUID organizationId, UUID jobId,
                                            String workerId, Instant now) {
        return jdbc.query("""
            select status, claimed_by, lease_expires_at, project_id, total_requirement_count,
                   analysis_profile_id, knowledge_snapshot_id, retrieval_policy_version_id,
                   matching_policy_version_id, comparison_policy_version_id,
                   confidence_policy_version_id, prompt_package_version_id, claimed_at,
                   attempt_count
              from compliance_analysis_job
             where id = ? and organization_id = ?
            """, rs -> {
                if (!rs.next()) {
                    log.info("event=COMPLIANCE_JOB_CLAIM_ATTEMPTED jobId={} outcome=NOT_FOUND",
                        jobId);
                    return new JobClaimResult(ClaimOutcome.NOT_FOUND, jobId, null, null, 0,
                        null, null, null, null, null, null, null, null, 0);
                }
                String status = rs.getString("status");
                String claimedBy = rs.getString("claimed_by");
                Instant lease = rs.getTimestamp("lease_expires_at") == null
                    ? null : rs.getTimestamp("lease_expires_at").toInstant();
                ClaimOutcome outcome;
                if ("COMPLETED".equals(status) || "PARTIALLY_COMPLETED".equals(status)) {
                    outcome = ClaimOutcome.ALREADY_COMPLETED;
                } else if ("CANCELLED".equals(status)) {
                    outcome = ClaimOutcome.ALREADY_CANCELLED;
                } else if ("FAILED".equals(status)) {
                    outcome = ClaimOutcome.ALREADY_COMPLETED;
                } else if (claimedBy != null && !claimedBy.equals(workerId)
                    && lease != null && lease.isAfter(now)) {
                    outcome = ClaimOutcome.CLAIMED_BY_OTHER_WORKER;
                } else if (lease != null && lease.isAfter(now)) {
                    outcome = ClaimOutcome.LEASE_NOT_EXPIRED;
                } else {
                    outcome = ClaimOutcome.CLAIMED_BY_OTHER_WORKER;
                }
                log.info("event=COMPLIANCE_JOB_CLAIM_ATTEMPTED jobId={} outcome={} status={}",
                    jobId, outcome, status);
                return new JobClaimResult(
                    outcome,
                    jobId,
                    rs.getObject("project_id", UUID.class),
                    status,
                    rs.getInt("total_requirement_count"),
                    rs.getObject("analysis_profile_id", UUID.class),
                    rs.getObject("knowledge_snapshot_id", UUID.class),
                    rs.getObject("retrieval_policy_version_id", UUID.class),
                    rs.getObject("matching_policy_version_id", UUID.class),
                    rs.getObject("comparison_policy_version_id", UUID.class),
                    rs.getObject("confidence_policy_version_id", UUID.class),
                    rs.getObject("prompt_package_version_id", UUID.class),
                    rs.getTimestamp("claimed_at") == null
                        ? null : rs.getTimestamp("claimed_at").toInstant(),
                    rs.getInt("attempt_count")
                );
            }, jobId, organizationId);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    public static String orchestrationErrorCode(ClaimOutcome outcome) {
        return switch (outcome) {
            case CLAIMED -> null;
            case ALREADY_COMPLETED -> "JOB_ALREADY_COMPLETED";
            case ALREADY_CANCELLED -> "JOB_ALREADY_CANCELLED";
            case CLAIMED_BY_OTHER_WORKER -> "JOB_ALREADY_CLAIMED";
            case LEASE_NOT_EXPIRED -> "JOB_LEASE_NOT_EXPIRED";
            case NOT_FOUND -> "JOB_NOT_FOUND";
        };
    }

    public static String normalizeWorkerId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "worker-" + UUID.randomUUID();
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
