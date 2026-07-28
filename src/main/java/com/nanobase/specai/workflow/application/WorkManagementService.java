package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.workflow.api.WorkManagementContracts.AddCommentRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.ApprovalDecisionRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.ApprovalDecisionResponse;
import com.nanobase.specai.workflow.api.WorkManagementContracts.ApprovalResponse;
import com.nanobase.specai.workflow.api.WorkManagementContracts.AssignTaskRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.EscalateTaskRequest;
import com.nanobase.specai.workflow.api.WorkManagementContracts.TaskResponse;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalPolicyContext;
import com.nanobase.specai.workflow.application.WorkflowModels.ApprovalVote;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentCandidate;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentContext;
import com.nanobase.specai.workflow.application.WorkflowModels.AssignmentPolicyVersion;
import com.nanobase.specai.workflow.application.WorkflowModels.BusinessCalendarDefinition;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkManagementService implements WorkflowNodeActionProvider {
    private static final Pattern SAFE_CODE_PREFIX = Pattern.compile("[A-Z0-9_-]{1,40}");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final AssignmentPolicyEngine assignments;
    private final ApprovalPolicyEngine approvals;
    private final BusinessCalendarService calendars;
    private final AuditService audit;
    private final OutboxService outbox;

    public WorkManagementService(JdbcTemplate jdbc, ObjectMapper mapper,
                                 CurrentTenant currentTenant,
                                 AssignmentPolicyEngine assignments,
                                 ApprovalPolicyEngine approvals,
                                 BusinessCalendarService calendars,
                                 AuditService audit, OutboxService outbox) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.assignments = assignments;
        this.approvals = approvals;
        this.calendars = calendars;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Override
    public boolean supports(String providerCode) {
        return "DYNAMIC_TASK".equals(providerCode)
            || "DYNAMIC_APPROVAL".equals(providerCode);
    }

    @Override
    @Transactional
    public WorkflowNodeExecutionResult execute(WorkflowNodeExecutionContext context) {
        JsonNode config = context.configuration();
        if ("DYNAMIC_APPROVAL".equals(config.path("actionProvider").asText())) {
            return createApproval(context, config);
        }
        UUID projectId = jdbc.queryForObject("""
            select project_id from workflow_instance where id = ?
            """, UUID.class, context.workflowInstanceId());
        UUID taskType = requiredUuid(config, "taskTypeConceptId");
        UUID priority = requiredUuid(config, "priorityConceptId");
        UUID status = requiredUuid(config, "initialStatusConceptId");
        requireConcept(taskType, "TASK_TYPE");
        requireConcept(priority, "TASK_PRIORITY");
        requireConcept(status, "TASK_STATUS");
        String prefix = config.path("taskCodePrefix").asText("TASK");
        if (!SAFE_CODE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Unsafe task code prefix");
        }
        UUID taskId = UUID.randomUUID();
        String taskCode = prefix + "-" + taskId.toString().substring(0, 8).toUpperCase();
        UUID assignmentVersionId = optionalUuid(config, "assignmentPolicyVersionId");
        var resolved = assignmentVersionId == null
            ? new WorkflowModels.AssignmentResult(null, null,
                List.of("No assignment policy configured"), true)
            : resolveAssignment(context.organizationId(), projectId, taskType, priority,
                assignmentVersionId);
        jdbc.update("""
            insert into task_record (
                id, organization_id, project_id, workflow_instance_id,
                workflow_execution_id, task_code, task_type_concept_id,
                subject_entity_type, subject_entity_id, title, description,
                priority_concept_id, status_concept_id, assignment_policy_id,
                assigned_user_id, assigned_group_id, created_by, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, taskId, context.organizationId(), projectId, context.workflowInstanceId(),
            context.workflowExecutionId(), taskCode, taskType,
            config.path("subjectEntityType").asText("WORKFLOW_INSTANCE"),
            context.workflowInstanceId(), config.path("title").asText(taskCode),
            config.path("description").asText(null), priority, status, assignmentVersionId,
            resolved.assignedUserId(), resolved.assignedGroupId(),
            config.path("createdBy").asText("workflow"));
        applySla(context.organizationId(), taskId, config);
        outbox.publish(context.organizationId(), "TaskRecord", taskId,
            "task.created.v1", "task.created.v1",
            Map.of("taskId", taskId, "workflowInstanceId", context.workflowInstanceId()), null);
        var output = JsonNodeFactory.instance.objectNode();
        output.put("taskId", taskId.toString());
        output.put("taskCode", taskCode);
        output.put("assignmentRequiresManualResolution", resolved.manualResolutionRequired());
        return new WorkflowNodeExecutionResult(false, true, output,
            List.of("task.created.v1"));
    }

    private WorkflowNodeExecutionResult createApproval(WorkflowNodeExecutionContext context,
                                                       JsonNode config) {
        UUID projectId = jdbc.queryForObject("""
            select project_id from workflow_instance where id = ?
            """, UUID.class, context.workflowInstanceId());
        UUID policyVersionId = requiredUuid(config, "approvalPolicyVersionId");
        UUID status = requiredUuid(config, "initialStatusConceptId");
        requireConcept(status, "APPROVAL_STATUS");
        UUID requestId = UUID.randomUUID();
        jdbc.update("""
            insert into approval_request (
                id, organization_id, project_id, workflow_instance_id,
                workflow_execution_id, subject_entity_type, subject_entity_id,
                approval_policy_id, status_concept_id, requested_by, requested_at,
                expires_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), now())
            """, requestId, context.organizationId(), projectId,
            context.workflowInstanceId(), context.workflowExecutionId(),
            config.path("subjectEntityType").asText("WORKFLOW_INSTANCE"),
            context.workflowInstanceId(), policyVersionId, status,
            config.path("requestedBy").asText("workflow"),
            config.hasNonNull("expiresAt")
                ? Timestamp.from(Instant.parse(config.path("expiresAt").asText())) : null);
        int order = 0;
        for (JsonNode step : config.path("steps")) {
            UUID mode = requiredUuid(step, "approvalModeConceptId");
            requireConcept(mode, "APPROVAL_MODE");
            jdbc.update("""
                insert into approval_step (
                    id, organization_id, approval_request_id, step_code, step_order,
                    approval_mode_concept_id, required_approval_count,
                    assignment_policy_id, status_concept_id, started_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, case when ? = 0 then now() else null end,
                          now())
                """, UUID.randomUUID(), context.organizationId(), requestId,
                step.path("stepCode").asText("STEP_" + order), order, mode,
                step.path("requiredApprovalCount").asInt(1),
                optionalUuid(step, "assignmentPolicyVersionId"), status, order);
            order++;
        }
        outbox.publish(context.organizationId(), "ApprovalRequest", requestId,
            "approval.requested.v1", "approval.requested.v1",
            Map.of("approvalRequestId", requestId,
                "workflowInstanceId", context.workflowInstanceId()), null);
        var output = JsonNodeFactory.instance.objectNode();
        output.put("approvalRequestId", requestId.toString());
        return new WorkflowNodeExecutionResult(false, true, output,
            List.of("approval.requested.v1"));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(UUID projectId, UUID statusConceptId,
                                   boolean assignedToMe) {
        TenantPrincipal principal = currentTenant.require();
        StringBuilder sql = new StringBuilder("""
            select t.*, tt.concept_code as task_type_code,
                   p.concept_code as priority_code, s.concept_code as status_code
              from task_record t
              join ontology_concept tt on tt.id = t.task_type_concept_id
              join ontology_concept p on p.id = t.priority_concept_id
              join ontology_concept s on s.id = t.status_concept_id
             where 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        if (projectId != null) {
            sql.append(" and t.project_id = ?");
            args.add(projectId);
        }
        if (statusConceptId != null) {
            sql.append(" and t.status_concept_id = ?");
            args.add(statusConceptId);
        }
        if (assignedToMe) {
            sql.append(" and t.assigned_user_id = ?");
            args.add(principal.subject());
        }
        sql.append(" order by t.due_at nulls last, t.created_at desc");
        return jdbc.query(sql.toString(), this::task, args.toArray());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID id) {
        TaskResponse task = jdbc.queryForObject("""
            select t.*, tt.concept_code as task_type_code,
                   p.concept_code as priority_code, s.concept_code as status_code
              from task_record t
              join ontology_concept tt on tt.id = t.task_type_concept_id
              join ontology_concept p on p.id = t.priority_concept_id
              join ontology_concept s on s.id = t.status_concept_id
             where t.id = ?
            """, this::task, id);
        return withComments(task);
    }

    @Transactional
    public TaskResponse claim(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        TaskResponse before = getTask(id);
        int updated = jdbc.update("""
            update task_record set assigned_user_id = ?, started_at = coalesce(started_at, now()),
                   updated_at = now(), version = version + 1
             where id = ? and (assigned_user_id is null or assigned_user_id = ?)
            """, principal.subject(), id, principal.subject());
        if (updated != 1) {
            throw new IllegalStateException("Task is already assigned to another user");
        }
        TaskResponse after = getTask(id);
        audit.record("task.claimed.v1", "TaskRecord", id, before, after);
        event(principal, id, "task.assigned.v1", Map.of("assignedUserId", principal.subject()));
        return after;
    }

    @Transactional
    public TaskResponse assign(UUID id, AssignTaskRequest request) {
        TenantPrincipal principal = currentTenant.require();
        if ((request.assignedUserId() == null) == (request.assignedGroupId() == null)) {
            throw new IllegalArgumentException(
                "Exactly one assignedUserId or assignedGroupId is required");
        }
        TaskResponse before = getTask(id);
        jdbc.update("""
            update task_record set assigned_user_id = ?, assigned_group_id = ?,
                   updated_at = now(), version = version + 1 where id = ?
            """, request.assignedUserId(), request.assignedGroupId(), id);
        TaskResponse after = getTask(id);
        audit.record("task.assigned.v1", "TaskRecord", id, before, after);
        event(principal, id, "task.assigned.v1", Map.of(
            "assignedUserId", request.assignedUserId() == null ? "" : request.assignedUserId(),
            "assignedGroupId", request.assignedGroupId() == null ? "" : request.assignedGroupId()));
        return after;
    }

    @Transactional
    public TaskResponse changeStatus(UUID id, UUID statusConceptId, String actionEffect) {
        TenantPrincipal principal = currentTenant.require();
        TaskResponse before = getTask(id);
        String configuredEffect = jdbc.queryForObject("""
            select metadata_json ->> 'actionEffect' from ontology_concept
             where id = ? and concept_type = 'TASK_STATUS' and active = true
            """, String.class, statusConceptId);
        if (!actionEffect.equals(configuredEffect)) {
            throw new IllegalArgumentException("Selected status does not support this action");
        }
        jdbc.update("""
            update task_record set status_concept_id = ?,
                   completed_at = case when ? = 'complete' then now() else completed_at end,
                   updated_at = now(), version = version + 1
             where id = ?
            """, statusConceptId, actionEffect, id);
        TaskResponse after = getTask(id);
        String eventType = "complete".equals(actionEffect)
            ? "task.completed.v1" : "task.blocked.v1";
        audit.record(eventType, "TaskRecord", id, before, after);
        event(principal, id, eventType, Map.of("statusConceptId", statusConceptId));
        return after;
    }

    @Transactional
    public TaskResponse comment(UUID id, AddCommentRequest request) {
        TenantPrincipal principal = currentTenant.require();
        requireConcept(request.visibilityConceptId(), "COMMENT_VISIBILITY");
        jdbc.update("""
            insert into task_comment (
                id, organization_id, task_id, author_user_id, comment_text,
                visibility_concept_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, now(), now())
            """, UUID.randomUUID(), principal.tenantId(), id, principal.subject(),
            request.commentText(), request.visibilityConceptId());
        audit.record("task.comment.added.v1", "TaskRecord", id, null,
            Map.of("visibilityConceptId", request.visibilityConceptId()));
        return getTask(id);
    }

    @Transactional
    public Map<String, Object> escalate(UUID id, EscalateTaskRequest request) {
        TenantPrincipal principal = currentTenant.require();
        TaskResponse task = getTask(id);
        UUID escalationId = UUID.randomUUID();
        jdbc.update("""
            insert into escalation_record (
                id, organization_id, task_id, workflow_instance_id,
                escalation_policy_version_id, level_concept_id,
                trigger_reason_concept_id, target_user_id, target_group_id,
                triggered_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, escalationId, principal.tenantId(), id, task.workflowInstanceId(),
            request.escalationPolicyVersionId(), request.levelConceptId(),
            request.triggerReasonConceptId(), request.targetUserId(),
            request.targetGroupId());
        audit.record("task.escalated.v1", "TaskRecord", id, null,
            Map.of("escalationId", escalationId));
        event(principal, id, "task.escalated.v1", Map.of("escalationId", escalationId));
        return Map.of("id", escalationId, "taskId", id, "triggeredAt", Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> approvals(UUID projectId) {
        String sql = projectId == null
            ? "select id from approval_request order by requested_at desc"
            : "select id from approval_request where project_id = ? order by requested_at desc";
        List<UUID> ids = projectId == null
            ? jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class))
            : jdbc.query(sql, (result, row) -> result.getObject(1, UUID.class), projectId);
        return ids.stream().map(this::approval).toList();
    }

    @Transactional(readOnly = true)
    public ApprovalResponse approval(UUID id) {
        ApprovalResponse base = jdbc.queryForObject("""
            select a.id, a.project_id, a.workflow_instance_id, a.workflow_execution_id,
                   a.subject_entity_type,
                   a.subject_entity_id, a.status_concept_id, s.concept_code,
                   a.requested_by, a.requested_at, a.completed_at, a.expires_at,
                   p.configuration_json::text
              from approval_request a
              join approval_policy_version p on p.id = a.approval_policy_id
              join ontology_concept s on s.id = a.status_concept_id
             where a.id = ?
            """, (result, row) -> new ApprovalResponse(
                result.getObject("id", UUID.class),
                result.getObject("project_id", UUID.class),
                result.getObject("workflow_instance_id", UUID.class),
                result.getObject("workflow_execution_id", UUID.class),
                result.getString("subject_entity_type"),
                result.getObject("subject_entity_id", UUID.class),
                result.getObject("status_concept_id", UUID.class),
                result.getString("concept_code"), result.getString("requested_by"),
                result.getTimestamp("requested_at").toInstant(),
                instant(result, "completed_at"), instant(result, "expires_at"),
                parse(result.getString(13)), List.of(), List.of(), List.of()), id);
        List<Map<String, Object>> steps = jdbc.query("""
            select st.id, st.step_code, st.step_order, m.concept_code as approval_mode,
                   st.required_approval_count, st.assignment_policy_id,
                   s.concept_code as status, st.started_at, st.completed_at
              from approval_step st
              join ontology_concept m on m.id = st.approval_mode_concept_id
              join ontology_concept s on s.id = st.status_concept_id
             where st.approval_request_id = ? order by st.step_order
            """, (result, row) -> rowMap(result, List.of("id", "step_code", "step_order",
                "approval_mode", "required_approval_count", "assignment_policy_id",
                "status", "started_at", "completed_at")), id);
        List<ApprovalDecisionResponse> decisions = decisions(id);
        List<Map<String, Object>> options = jdbc.query("""
            select id, concept_code, name, metadata_json::text
              from ontology_concept
             where concept_type = 'APPROVAL_DECISION' and active = true
             order by sort_order
            """, (result, row) -> Map.of(
                "id", result.getObject("id", UUID.class),
                "code", result.getString("concept_code"),
                "name", result.getString("name"),
                "metadata", parse(result.getString(4))));
        return new ApprovalResponse(base.id(), base.projectId(), base.workflowInstanceId(),
            base.workflowExecutionId(), base.subjectEntityType(), base.subjectEntityId(),
            base.statusConceptId(),
            base.statusConceptCode(), base.requestedBy(), base.requestedAt(),
            base.completedAt(), base.expiresAt(), base.policyConfiguration(),
            steps, decisions, options);
    }

    @Transactional
    public ApprovalDecisionResponse decide(UUID approvalId,
                                           ApprovalDecisionRequest request) {
        TenantPrincipal principal = currentTenant.require();
        ApprovalResponse approval = approval(approvalId);
        if (principal.subject().equals(approval.requestedBy())
            && !approval.policyConfiguration().path("allowSelfApproval").asBoolean(false)) {
            throw new IllegalStateException("Requester cannot approve their own request");
        }
        requireBusinessRoles(principal.subject(),
            approval.policyConfiguration().path("requiredBusinessRoleCodes"));
        requireConcept(request.decisionConceptId(), "APPROVAL_DECISION");
        UUID decisionId = UUID.randomUUID();
        JsonNode snapshot = request.decisionSnapshot() == null
            ? JsonNodeFactory.instance.objectNode() : request.decisionSnapshot();
        jdbc.update("""
            insert into approval_decision (
                id, organization_id, approval_request_id, approval_step_id,
                reviewer_user_id, decision_concept_id, comment,
                decision_snapshot_json, decided_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
            """, decisionId, principal.tenantId(), approvalId, request.approvalStepId(),
            principal.subject(), request.decisionConceptId(), request.comment(), json(snapshot));
        evaluateApproval(approvalId, request.approvalStepId(),
            approval.policyConfiguration());
        ApprovalDecisionResponse result = decisions(approvalId).stream()
            .filter(decision -> decision.id().equals(decisionId)).findFirst().orElseThrow();
        audit.record("approval.decision.recorded.v1", "ApprovalRequest", approvalId,
            null, result);
        outbox.publish(principal.tenantId(), "ApprovalRequest", approvalId,
            "approval.decision.recorded.v1", "approval.decision.recorded.v1",
            Map.of("approvalRequestId", approvalId, "decisionConceptId",
                request.decisionConceptId()), null);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ApprovalDecisionResponse> decisions(UUID approvalId) {
        return jdbc.query("""
            select d.id, d.approval_request_id, d.approval_step_id,
                   d.reviewer_user_id, d.decision_concept_id, c.concept_code,
                   d.comment, d.decision_snapshot_json::text, d.decided_at
              from approval_decision d
              join ontology_concept c on c.id = d.decision_concept_id
             where d.approval_request_id = ? order by d.decided_at
            """, (result, row) -> new ApprovalDecisionResponse(
                result.getObject("id", UUID.class),
                result.getObject("approval_request_id", UUID.class),
                result.getObject("approval_step_id", UUID.class),
                result.getString("reviewer_user_id"),
                result.getObject("decision_concept_id", UUID.class),
                result.getString("concept_code"), result.getString("comment"),
                parse(result.getString(8)),
                result.getTimestamp("decided_at").toInstant()), approvalId);
    }

    private WorkflowModels.AssignmentResult resolveAssignment(
        UUID organizationId, UUID projectId, UUID taskType, UUID priority,
        UUID policyVersionId) {
        JsonNode config = jdbc.queryForObject("""
            select configuration_json::text from assignment_policy_version where id = ?
            """, (result, row) -> parse(result.getString(1)), policyVersionId);
        List<String> users = projectId == null ? List.of() : jdbc.query("""
            select user_id from project_member where project_id = ?
            """, (result, row) -> result.getString(1), projectId);
        List<AssignmentCandidate> candidates = users.stream()
            .map(user -> candidate(user, projectId, config)).toList();
        return assignments.resolve(new AssignmentContext(organizationId, projectId,
            conceptCode(taskType), conceptCode(priority), null, null, null,
            candidates, Map.of()), new AssignmentPolicyVersion(policyVersionId, config));
    }

    private AssignmentCandidate candidate(String user, UUID projectId, JsonNode config) {
        Set<String> roles = new HashSet<>(jdbc.query("""
            select r.role_code from user_business_role u
              join business_role r on r.id = u.business_role_id
             where u.user_id = ?
               and (u.scope_entity_id is null or u.scope_entity_id = ?)
               and (u.valid_from is null or u.valid_from <= now())
               and (u.valid_until is null or u.valid_until >= now())
            """, (result, row) -> result.getString(1), user, projectId));
        Integer open = jdbc.queryForObject("""
            select count(*) from task_record
             where assigned_user_id = ? and completed_at is null and cancelled_at is null
            """, Integer.class, user);
        double capacity = Math.max(1, config.path("workloadCapacity").asDouble(10));
        return new AssignmentCandidate(user, null, Set.of(), roles, null, null,
            Math.min(1, (open == null ? 0 : open) / capacity), true, false, Map.of());
    }

    private void applySla(UUID organizationId, UUID taskId, JsonNode config) {
        UUID policyId = optionalUuid(config, "slaPolicyVersionId");
        UUID calendarId = optionalUuid(config, "businessCalendarId");
        if (policyId == null || calendarId == null) {
            return;
        }
        JsonNode policy = jdbc.queryForObject("""
            select configuration_json::text from sla_policy_version where id = ?
            """, (result, row) -> parse(result.getString(1)), policyId);
        CalendarRow calendar = jdbc.queryForObject("""
            select timezone, configuration_json::text from business_calendar where id = ?
            """, (result, row) -> new CalendarRow(result.getString(1),
                parse(result.getString(2))), calendarId);
        Map<LocalDate, String> exceptions = new HashMap<>();
        jdbc.query("""
            select e.exception_date, c.concept_code from calendar_exception e
              join ontology_concept c on c.id = e.exception_type_concept_id
             where e.business_calendar_id = ?
            """, (result, row) -> {
                exceptions.put(result.getObject(1, LocalDate.class), result.getString(2));
                return row;
            }, calendarId);
        var due = calendars.calculate(Instant.now(),
            policy.path("businessMinutes").asLong(),
            policy.path("warningRatio").asDouble(0.8),
            new BusinessCalendarDefinition(calendar.timezone(), calendar.configuration(),
                exceptions));
        UUID slaStatus = requiredUuid(policy, "initialStatusConceptId");
        jdbc.update("""
            insert into task_sla_record (
                id, organization_id, task_id, sla_policy_version_id, target_due_at,
                warning_at, breach_at, status_concept_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, UUID.randomUUID(), organizationId, taskId, policyId,
            Timestamp.from(due.targetDueAt()), Timestamp.from(due.warningAt()),
            Timestamp.from(due.breachAt()), slaStatus);
        jdbc.update("update task_record set due_at = ? where id = ?",
            Timestamp.from(due.targetDueAt()), taskId);
    }

    private void evaluateApproval(UUID approvalId, UUID stepId, JsonNode policy) {
        String mode = jdbc.queryForObject("""
            select c.concept_code from approval_step s
              join ontology_concept c on c.id = s.approval_mode_concept_id
             where s.id = ? and s.approval_request_id = ?
            """, String.class, stepId, approvalId);
        Integer eligible = jdbc.queryForObject("""
            select coalesce(required_approval_count, 1) from approval_step where id = ?
            """, Integer.class, stepId);
        List<ApprovalVote> votes = jdbc.query("""
            select c.concept_code,
                   coalesce((d.decision_snapshot_json ->> 'weight')::numeric, 1)
              from approval_decision d
              join ontology_concept c on c.id = d.decision_concept_id
             where d.approval_step_id = ?
            """, (result, row) -> new ApprovalVote("", result.getString(1),
                result.getDouble(2)), stepId);
        var result = approvals.evaluate(new ApprovalPolicyContext(mode,
            eligible == null ? 1 : eligible, votes, Map.of()), policy);
        if (!result.completed()) {
            return;
        }
        String statusCode = result.approved()
            ? policy.path("approvedStatusConceptCode").asText("APPROVAL_COMPLETED")
            : policy.path("rejectedStatusConceptCode").asText("APPROVAL_REJECTED");
        UUID status = concept(statusCode, "APPROVAL_STATUS");
        jdbc.update("""
            update approval_step set status_concept_id = ?, completed_at = now()
             where id = ?
            """, status, stepId);
        jdbc.update("""
            update approval_request set status_concept_id = ?, completed_at = now(),
                   updated_at = now(), version = version + 1 where id = ?
            """, status, approvalId);
    }

    private void requireBusinessRoles(String userId, JsonNode roles) {
        if (!roles.isArray() || roles.isEmpty()) {
            return;
        }
        Set<String> actual = new HashSet<>(jdbc.query("""
            select r.role_code from user_business_role u
              join business_role r on r.id = u.business_role_id
             where u.user_id = ? and (u.valid_from is null or u.valid_from <= now())
               and (u.valid_until is null or u.valid_until >= now())
            """, (result, row) -> result.getString(1), userId));
        boolean matched = false;
        for (JsonNode role : roles) {
            matched |= actual.contains(role.asText());
        }
        if (!matched) {
            throw new IllegalStateException("User does not hold a required business role");
        }
    }

    private TaskResponse withComments(TaskResponse task) {
        List<Map<String, Object>> comments = jdbc.query("""
            select c.id, c.author_user_id, c.comment_text, v.concept_code as visibility,
                   c.created_at, c.updated_at
              from task_comment c
              join ontology_concept v on v.id = c.visibility_concept_id
             where c.task_id = ? order by c.created_at
            """, (result, row) -> rowMap(result, List.of("id", "author_user_id",
                "comment_text", "visibility", "created_at", "updated_at")), task.id());
        return new TaskResponse(task.id(), task.projectId(), task.workflowInstanceId(),
            task.workflowExecutionId(), task.taskCode(), task.taskTypeConceptId(),
            task.taskTypeConceptCode(), task.subjectEntityType(), task.subjectEntityId(),
            task.title(), task.description(), task.priorityConceptId(),
            task.priorityConceptCode(), task.statusConceptId(), task.statusConceptCode(),
            task.assignedUserId(), task.assignedGroupId(), task.dueAt(), task.startedAt(),
            task.completedAt(), task.version(), comments);
    }

    private TaskResponse task(ResultSet result, int row) throws SQLException {
        return new TaskResponse(result.getObject("id", UUID.class),
            result.getObject("project_id", UUID.class),
            result.getObject("workflow_instance_id", UUID.class),
            result.getObject("workflow_execution_id", UUID.class),
            result.getString("task_code"),
            result.getObject("task_type_concept_id", UUID.class),
            result.getString("task_type_code"), result.getString("subject_entity_type"),
            result.getObject("subject_entity_id", UUID.class), result.getString("title"),
            result.getString("description"),
            result.getObject("priority_concept_id", UUID.class),
            result.getString("priority_code"),
            result.getObject("status_concept_id", UUID.class),
            result.getString("status_code"), result.getString("assigned_user_id"),
            result.getString("assigned_group_id"), instant(result, "due_at"),
            instant(result, "started_at"), instant(result, "completed_at"),
            result.getLong("version"), List.of());
    }

    private Map<String, Object> rowMap(ResultSet result, List<String> columns)
        throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        for (String column : columns) {
            Object cell = result.getObject(column);
            if (cell instanceof Timestamp timestamp) {
                cell = timestamp.toInstant();
            }
            value.put(column, cell);
        }
        return value;
    }

    private void event(TenantPrincipal principal, UUID taskId, String eventType,
                       Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>(payload);
        event.put("taskId", taskId);
        outbox.publish(principal.tenantId(), "TaskRecord", taskId, eventType,
            eventType, Map.copyOf(event), null);
    }

    private void requireConcept(UUID id, String type) {
        Integer count = jdbc.queryForObject("""
            select count(*) from ontology_concept
             where id = ? and concept_type = ? and active = true
            """, Integer.class, id, type);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active " + type + " concept is required");
        }
    }

    private UUID concept(String code, String type) {
        return jdbc.queryForObject("""
            select id from ontology_concept
             where concept_code = ? and concept_type = ? and active = true
             order by organization_id nulls last limit 1
            """, UUID.class, code, type);
    }

    private String conceptCode(UUID id) {
        return jdbc.queryForObject("select concept_code from ontology_concept where id = ?",
            String.class, id);
    }

    private static UUID requiredUuid(JsonNode node, String field) {
        UUID value = optionalUuid(node, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.path(field).asText().isBlank()
            ? UUID.fromString(node.path(field).asText()) : null;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Work management JSON cannot be serialized",
                exception);
        }
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored work management JSON is invalid", exception);
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record CalendarRow(String timezone, JsonNode configuration) {
    }
}
