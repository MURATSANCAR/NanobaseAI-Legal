package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Advances tenant SLA records from policy configuration. The scheduler never embeds
 * business durations, status values, recipients, or escalation levels.
 */
@Component
public class Sprint7SlaScheduler {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TenantDatabaseContext tenantDatabase;
    private final TransactionTemplate transactions;
    private final OutboxService outbox;
    private final AuditService audit;
    private final PlatformMetrics metrics;
    private final Clock clock;
    private final int batchSize;

    public Sprint7SlaScheduler(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        TenantDatabaseContext tenantDatabase,
        PlatformTransactionManager transactionManager,
        OutboxService outbox,
        AuditService audit,
        PlatformMetrics metrics,
        @Value("${specai.workflow.sla-scan-batch-size:100}") int batchSize) {
        this(jdbc, mapper, tenantDatabase, new TransactionTemplate(transactionManager),
            outbox, audit, metrics, Clock.systemUTC(), batchSize);
    }

    Sprint7SlaScheduler(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        TenantDatabaseContext tenantDatabase,
        TransactionTemplate transactions,
        OutboxService outbox,
        AuditService audit,
        PlatformMetrics metrics,
        Clock clock,
        int batchSize) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tenantDatabase = tenantDatabase;
        this.transactions = transactions;
        this.outbox = outbox;
        this.audit = audit;
        this.metrics = metrics;
        this.clock = clock;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${specai.workflow.sla-scan-interval-ms:60000}")
    public void evaluateDueRecords() {
        List<UUID> organizations =
            jdbc.queryForList("select id from organization order by id", UUID.class);
        for (UUID organizationId : organizations) {
            transactions.executeWithoutResult(ignored -> {
                tenantDatabase.apply(organizationId);
                evaluateTenant(organizationId, clock.instant());
            });
        }
    }

    void evaluateTenant(UUID organizationId, Instant now) {
        List<SlaRow> due = jdbc.query("""
            select s.id, s.task_id, t.workflow_instance_id, s.status_concept_id,
                   s.warning_at, s.breach_at, p.configuration_json::text
              from task_sla_record s
              join task_record t on t.id = s.task_id
              join sla_policy_version p on p.id = s.sla_policy_version_id
             where t.completed_at is null and t.cancelled_at is null
               and coalesce(s.warning_at, s.breach_at) <= ?
             order by s.breach_at
             limit ?
            """, (result, row) -> new SlaRow(
                result.getObject("id", UUID.class),
                result.getObject("task_id", UUID.class),
                result.getObject("workflow_instance_id", UUID.class),
                result.getObject("status_concept_id", UUID.class),
                instant(result.getTimestamp("warning_at")),
                instant(result.getTimestamp("breach_at")),
                parse(result.getString(7))), Timestamp.from(now), batchSize);
        due.forEach(row -> advance(organizationId, now, row));
    }

    private void advance(UUID organizationId, Instant now, SlaRow row) {
        SlaAction action = resolveAction(now, row.warningAt(), row.breachAt(),
            row.currentStatusId(), row.configuration());
        if (action == null) {
            return;
        }
        int changed = jdbc.update("""
            update task_sla_record
               set status_concept_id = ?, updated_at = now()
             where id = ? and status_concept_id = ?
            """, action.statusConceptId(), row.id(), row.currentStatusId());
        if (changed != 1) {
            return;
        }
        Map<String, Object> payload = Map.of(
            "taskId", row.taskId(),
            "taskSlaRecordId", row.id(),
            "occurredAt", now.toString());
        outbox.publish(organizationId, "TaskRecord", row.taskId(),
            action.eventType(), action.eventType(), payload, null);
        audit.recordSystem(organizationId, "sla-scheduler", action.eventType(),
            "TaskRecord", row.taskId(), Map.of("statusConceptId", row.currentStatusId()),
            Map.of("statusConceptId", action.statusConceptId()));
        if ("task.sla.breached.v1".equals(action.eventType())) {
            metrics.sprint7("task_sla_breached_total");
            escalate(organizationId, row, now);
        }
    }

    private void escalate(UUID organizationId, SlaRow row, Instant now) {
        JsonNode escalation = row.configuration().path("breachEscalation");
        UUID policyVersionId = uuid(escalation, "escalationPolicyVersionId");
        UUID levelConceptId = uuid(escalation, "levelConceptId");
        UUID reasonConceptId = uuid(escalation, "triggerReasonConceptId");
        if (policyVersionId == null || levelConceptId == null || reasonConceptId == null) {
            return;
        }
        UUID escalationId = UUID.randomUUID();
        int inserted = jdbc.update("""
            insert into escalation_record (
                id, organization_id, task_id, workflow_instance_id,
                escalation_policy_version_id, level_concept_id,
                trigger_reason_concept_id, target_user_id, target_group_id,
                triggered_at, created_at
            )
            select ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()
             where not exists (
                select 1 from escalation_record
                 where task_id = ? and escalation_policy_version_id = ?
                   and level_concept_id = ? and resolved_at is null
             )
            """, escalationId, organizationId, row.taskId(), row.workflowInstanceId(),
            policyVersionId, levelConceptId, reasonConceptId,
            text(escalation, "targetUserId"), text(escalation, "targetGroupId"),
            Timestamp.from(now), row.taskId(), policyVersionId, levelConceptId);
        if (inserted != 1) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", row.taskId());
        payload.put("escalationId", escalationId);
        payload.put("levelConceptId", levelConceptId);
        outbox.publish(organizationId, "TaskRecord", row.taskId(),
            "task.escalated.v1", "task.escalated.v1", Map.copyOf(payload), null);
        audit.recordSystem(organizationId, "sla-scheduler", "task.escalated.v1",
            "TaskRecord", row.taskId(), null, payload);
        metrics.sprint7("task_escalated_total");
    }

    static SlaAction resolveAction(Instant now, Instant warningAt, Instant breachAt,
                                   UUID currentStatusId, JsonNode configuration) {
        UUID breachStatus = uuid(configuration, "breachedStatusConceptId");
        if (breachStatus != null && !now.isBefore(breachAt)
            && !breachStatus.equals(currentStatusId)) {
            return new SlaAction(breachStatus, "task.sla.breached.v1");
        }
        UUID warningStatus = uuid(configuration, "warningStatusConceptId");
        if (warningStatus != null && warningAt != null && !now.isBefore(warningAt)
            && !warningStatus.equals(currentStatusId)) {
            return new SlaAction(warningStatus, "task.sla.warning.v1");
        }
        return null;
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored SLA policy JSON is invalid", exception);
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.path(field).asText().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(node.path(field).asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("SLA policy " + field + " must be a UUID",
                exception);
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && !node.path(field).asText().isBlank()
            ? node.path(field).asText() : null;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    record SlaAction(UUID statusConceptId, String eventType) {
    }

    private record SlaRow(UUID id, UUID taskId, UUID workflowInstanceId,
                          UUID currentStatusId, Instant warningAt, Instant breachAt,
                          JsonNode configuration) {
    }
}
