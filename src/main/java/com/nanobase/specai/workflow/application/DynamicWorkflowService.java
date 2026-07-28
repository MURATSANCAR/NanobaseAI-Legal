package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.workflow.api.WorkflowContracts.CreateWorkflowRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.CreateWorkflowVersionRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.SimulationRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.SimulationResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.StartWorkflowRequest;
import com.nanobase.specai.workflow.api.WorkflowContracts.TransitionDraft;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowDefinitionResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowHistoryResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowInstanceResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowNodeResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowTransitionResponse;
import com.nanobase.specai.workflow.api.WorkflowContracts.WorkflowVersionResponse;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowGraphNode;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowGraphTransition;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowNodeExecutionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowSimulationInput;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicWorkflowService {
    private static final int RUNTIME_STEP_LIMIT = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final WorkflowSimulationService simulation;
    private final WorkflowConditionEngine conditions;
    private final WorkflowNodeHandlerRegistry handlers;
    private final AuditService audit;
    private final OutboxService outbox;
    private final PlatformMetrics metrics;

    public DynamicWorkflowService(JdbcTemplate jdbc, ObjectMapper mapper,
                                  CurrentTenant currentTenant,
                                  WorkflowSimulationService simulation,
                                  WorkflowConditionEngine conditions,
                                  WorkflowNodeHandlerRegistry handlers,
                                  AuditService audit, OutboxService outbox,
                                  PlatformMetrics metrics) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.simulation = simulation;
        this.conditions = conditions;
        this.handlers = handlers;
        this.audit = audit;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional
    public WorkflowDefinitionResponse create(CreateWorkflowRequest request) {
        TenantPrincipal principal = currentTenant.require();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into workflow_definition (
                id, organization_id, workflow_code, name, description, scope,
                entity_type_concept_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
            """, id, principal.tenantId(), request.workflowCode(), request.name(),
            request.description(), request.scope(), request.entityTypeConceptId());
        WorkflowDefinitionResponse created = get(id);
        audit.record("workflow.definition.created.v1", "WorkflowDefinition", id, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinitionResponse> list() {
        return jdbc.query("""
            select id, workflow_code, name, description, scope, entity_type_concept_id,
                   active_version_id, created_at, updated_at
              from workflow_definition
             order by updated_at desc
            """, this::definition);
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionResponse get(UUID id) {
        return jdbc.queryForObject("""
            select id, workflow_code, name, description, scope, entity_type_concept_id,
                   active_version_id, created_at, updated_at
              from workflow_definition
             where id = ?
            """, this::definition, id);
    }

    @Transactional
    public WorkflowVersionResponse createVersion(UUID definitionId,
                                                 CreateWorkflowVersionRequest request) {
        TenantPrincipal principal = currentTenant.require();
        get(definitionId);
        int versionNumber = jdbc.queryForObject("""
            select coalesce(max(version_number), 0) + 1
              from workflow_version where workflow_definition_id = ?
            """, Integer.class, definitionId);
        UUID versionId = UUID.randomUUID();
        jdbc.update("""
            insert into workflow_version (
                id, organization_id, workflow_definition_id, version_number,
                configuration_json, status, created_at
            ) values (?, ?, ?, ?, ?::jsonb, 'DRAFT', now())
            """, versionId, principal.tenantId(), definitionId, versionNumber,
            json(orObject(request.configuration())));
        Map<String, UUID> nodeIds = new HashMap<>();
        request.nodes().forEach(node -> {
            requireConcept(node.nodeTypeConceptId(), "WORKFLOW_NODE_TYPE");
            UUID nodeId = UUID.randomUUID();
            if (nodeIds.put(node.nodeCode(), nodeId) != null) {
                throw new IllegalArgumentException("Duplicate workflow node code "
                    + node.nodeCode());
            }
            jdbc.update("""
                insert into workflow_node (
                    id, organization_id, workflow_version_id, node_code,
                    node_type_concept_id, name, description, configuration_json,
                    sort_order, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
                """, nodeId, principal.tenantId(), versionId, node.nodeCode(),
                node.nodeTypeConceptId(), node.name(), node.description(),
                json(orObject(node.configuration())), node.sortOrder());
        });
        for (TransitionDraft transition : request.transitions()) {
            UUID source = nodeIds.get(transition.sourceNodeCode());
            UUID target = nodeIds.get(transition.targetNodeCode());
            if (source == null || target == null) {
                throw new IllegalArgumentException("Workflow transition references unknown node");
            }
            requireConcept(transition.transitionConceptId(), "WORKFLOW_TRANSITION");
            jdbc.update("""
                insert into workflow_transition (
                    id, organization_id, workflow_version_id, source_node_id,
                    target_node_id, transition_concept_id, condition_expression_json,
                    authorization_policy_id, action_configuration_json, priority, created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, now())
                """, UUID.randomUUID(), principal.tenantId(), versionId, source, target,
                transition.transitionConceptId(), json(orObject(transition.condition())),
                transition.authorizationPolicyId(),
                json(orObject(transition.actionConfiguration())), transition.priority());
        }
        WorkflowVersionResponse created = version(versionId);
        audit.record("workflow.version.created.v1", "WorkflowVersion", versionId, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public WorkflowVersionResponse version(UUID versionId) {
        WorkflowVersionResponse base = jdbc.queryForObject("""
            select id, workflow_definition_id, version_number, configuration_json::text,
                   status, approved_by, approved_at, created_at
              from workflow_version where id = ?
            """, (result, row) -> new WorkflowVersionResponse(
                result.getObject("id", UUID.class),
                result.getObject("workflow_definition_id", UUID.class),
                result.getInt("version_number"), parse(result.getString(4)),
                result.getString("status"), result.getString("approved_by"),
                instant(result, "approved_at"), result.getTimestamp("created_at").toInstant(),
                List.of(), List.of()), versionId);
        List<WorkflowNodeResponse> nodes = jdbc.query("""
            select n.id, n.node_code, n.node_type_concept_id, c.concept_code,
                   n.name, n.description, n.configuration_json::text, n.sort_order
              from workflow_node n
              join ontology_concept c on c.id = n.node_type_concept_id
             where n.workflow_version_id = ? order by n.sort_order, n.node_code
            """, (result, row) -> new WorkflowNodeResponse(
                result.getObject("id", UUID.class), result.getString("node_code"),
                result.getObject("node_type_concept_id", UUID.class),
                result.getString("concept_code"), result.getString("name"),
                result.getString("description"), parse(result.getString(7)),
                result.getInt("sort_order")), versionId);
        List<WorkflowTransitionResponse> transitions = jdbc.query("""
            select id, source_node_id, target_node_id, transition_concept_id,
                   condition_expression_json::text, authorization_policy_id,
                   action_configuration_json::text, priority
              from workflow_transition where workflow_version_id = ?
             order by priority desc, created_at
            """, (result, row) -> new WorkflowTransitionResponse(
                result.getObject("id", UUID.class),
                result.getObject("source_node_id", UUID.class),
                result.getObject("target_node_id", UUID.class),
                result.getObject("transition_concept_id", UUID.class),
                parse(result.getString(5)),
                result.getObject("authorization_policy_id", UUID.class),
                parse(result.getString(7)), result.getInt("priority")), versionId);
        return new WorkflowVersionResponse(base.id(), base.workflowDefinitionId(),
            base.versionNumber(), base.configuration(), base.status(), base.approvedBy(),
            base.approvedAt(), base.createdAt(), nodes, transitions);
    }

    @Transactional
    public SimulationResponse simulate(UUID versionId, SimulationRequest request) {
        TenantPrincipal principal = currentTenant.require();
        WorkflowVersionResponse version = version(versionId);
        Set<String> supported = new HashSet<>();
        version.nodes().stream().map(WorkflowNodeResponse::nodeTypeConceptCode)
            .filter(handlers::supports).forEach(supported::add);
        Set<String> authorized = request.authorizedNodeCodes() == null
            ? Set.of() : Set.copyOf(request.authorizedNodeCodes());
        var result = simulation.simulate(new WorkflowSimulationInput(
            version.nodes().stream().map(node -> new WorkflowGraphNode(
                node.id(), node.nodeCode(), node.nodeTypeConceptCode(), node.configuration()))
                .toList(),
            version.transitions().stream().map(transition -> new WorkflowGraphTransition(
                transition.id(), transition.sourceNodeId(), transition.targetNodeId(),
                transition.condition(), transition.priority())).toList(),
            new WorkflowConditionContext(request.variables()),
            supported, authorized,
            request.maximumVisitsPerNode() == null ? 3 : request.maximumVisitsPerNode()));
        UUID runId = UUID.randomUUID();
        jdbc.update("""
            insert into workflow_simulation_run (
                id, organization_id, workflow_version_id, input_snapshot_json,
                result_json, valid, simulated_by, created_at
            ) values (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, now())
            """, runId, principal.tenantId(), versionId, json(request),
            json(result), result.valid(), principal.subject());
        jdbc.update("""
            update workflow_version set status = 'TESTING'
             where id = ? and status = 'DRAFT'
            """, versionId);
        audit.record("workflow.version.simulated.v1", "WorkflowVersion", versionId,
            null, result);
        return new SimulationResponse(runId, result);
    }

    @Transactional(readOnly = true)
    public SimulationResponse validate(UUID versionId) {
        WorkflowVersionResponse version = version(versionId);
        var result = simulation.simulate(new WorkflowSimulationInput(
            version.nodes().stream().map(node -> new WorkflowGraphNode(
                node.id(), node.nodeCode(), node.nodeTypeConceptCode(), node.configuration()))
                .toList(),
            version.transitions().stream().map(transition -> new WorkflowGraphTransition(
                transition.id(), transition.sourceNodeId(), transition.targetNodeId(),
                transition.condition(), transition.priority())).toList(),
            new WorkflowConditionContext(Map.of()), version.nodes().stream()
                .map(WorkflowNodeResponse::nodeTypeConceptCode).filter(handlers::supports)
                .collect(java.util.stream.Collectors.toSet()), Set.of(), 3));
        return new SimulationResponse(null, result);
    }

    @Transactional
    public WorkflowVersionResponse activate(UUID versionId) {
        TenantPrincipal principal = currentTenant.require();
        WorkflowVersionResponse before = version(versionId);
        Boolean validSimulation = jdbc.queryForObject("""
            select exists (
                select 1 from workflow_simulation_run
                 where workflow_version_id = ? and valid = true
            )
            """, Boolean.class, versionId);
        if (!Boolean.TRUE.equals(validSimulation)) {
            throw new IllegalStateException("A successful simulation is required before activation");
        }
        jdbc.update("""
            update workflow_version set status = 'RETIRED'
             where workflow_definition_id = ? and status = 'ACTIVE'
            """, before.workflowDefinitionId());
        jdbc.update("""
            update workflow_version
               set status = 'ACTIVE', approved_by = ?, approved_at = now()
             where id = ? and status in ('DRAFT', 'TESTING')
            """, principal.subject(), versionId);
        jdbc.update("""
            update workflow_definition
               set active_version_id = ?, updated_at = now()
             where id = ?
            """, versionId, before.workflowDefinitionId());
        WorkflowVersionResponse activated = version(versionId);
        audit.record("workflow.version.activated.v1", "WorkflowVersion", versionId,
            before, activated);
        return activated;
    }

    @Transactional
    public WorkflowInstanceResponse start(StartWorkflowRequest request) {
        TenantPrincipal principal = currentTenant.require();
        UUID versionId = jdbc.queryForObject("""
            select active_version_id from workflow_definition
             where id = ? and active_version_id is not null
            """, UUID.class, request.workflowDefinitionId());
        UUID activeStatus = concept("WORKFLOW_ACTIVE", "WORKFLOW_STATUS");
        UUID instanceId = UUID.randomUUID();
        JsonNode snapshot = orObject(request.contextSnapshot());
        jdbc.update("""
            insert into workflow_instance (
                id, organization_id, workflow_definition_id, workflow_version_id,
                project_id, subject_entity_type, subject_entity_id, status_concept_id,
                current_state_json, context_snapshot_json, started_by, started_at,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?::jsonb, ?, now(), now(), now())
            """, instanceId, principal.tenantId(), request.workflowDefinitionId(), versionId,
            request.projectId(), request.subjectEntityType(), request.subjectEntityId(),
            activeStatus, json(snapshot), principal.subject());
        UUID entry = jdbc.queryForObject("""
            select id from workflow_node
             where workflow_version_id = ?
               and coalesce((configuration_json ->> 'entry')::boolean, false) = true
             order by sort_order limit 1
            """, UUID.class, versionId);
        UUID tokenId = createToken(principal.tenantId(), instanceId, entry, null, activeStatus);
        outbox.publish(principal.tenantId(), "WorkflowInstance", instanceId,
            "workflow.instance.started.v1", "workflow.instance.started.v1",
            Map.of("workflowInstanceId", instanceId, "projectId",
                request.projectId() == null ? "" : request.projectId()), null);
        metrics.sprint7("workflow_instance_total");
        run(instanceId, tokenId, snapshot, principal);
        WorkflowInstanceResponse started = instance(instanceId);
        audit.record("workflow.instance.started.v1", "WorkflowInstance", instanceId,
            null, started);
        return started;
    }

    @Transactional(readOnly = true)
    public WorkflowInstanceResponse instance(UUID id) {
        return jdbc.queryForObject("""
            select i.id, i.workflow_definition_id, i.workflow_version_id, i.project_id,
                   i.subject_entity_type, i.subject_entity_id, i.status_concept_id,
                   c.concept_code, i.current_state_json::text,
                   i.context_snapshot_json::text, i.started_by, i.started_at,
                   i.completed_at, i.cancelled_at, i.version
              from workflow_instance i
              join ontology_concept c on c.id = i.status_concept_id
             where i.id = ?
            """, (result, row) -> new WorkflowInstanceResponse(
                result.getObject("id", UUID.class),
                result.getObject("workflow_definition_id", UUID.class),
                result.getObject("workflow_version_id", UUID.class),
                result.getObject("project_id", UUID.class),
                result.getString("subject_entity_type"),
                result.getObject("subject_entity_id", UUID.class),
                result.getObject("status_concept_id", UUID.class),
                result.getString("concept_code"), parse(result.getString(9)),
                parse(result.getString(10)), result.getString("started_by"),
                result.getTimestamp("started_at").toInstant(),
                instant(result, "completed_at"), instant(result, "cancelled_at"),
                result.getLong("version")), id);
    }

    @Transactional(readOnly = true)
    public WorkflowHistoryResponse history(UUID id) {
        instance(id);
        List<Map<String, Object>> executions = jdbc.query("""
            select e.id, e.workflow_token_id, e.node_id, n.node_code,
                   c.concept_code as status, e.input_snapshot_json::text,
                   e.output_snapshot_json::text, e.started_at, e.completed_at,
                   e.error_code, e.error_message, e.retry_count
              from workflow_execution e
              join workflow_node n on n.id = e.node_id
              join ontology_concept c on c.id = e.execution_status_concept_id
             where e.workflow_instance_id = ? order by e.created_at
            """, (result, row) -> executionMap(result), id);
        List<Map<String, Object>> transitions = jdbc.query("""
            select l.id, s.node_code as source_node_code, t.node_code as target_node_code,
                   l.transition_id, l.decision_context_json::text, l.triggered_by_type,
                   l.triggered_by_id, l.occurred_at
              from workflow_transition_log l
              join workflow_node s on s.id = l.source_node_id
              join workflow_node t on t.id = l.target_node_id
             where l.workflow_instance_id = ? order by l.occurred_at
            """, (result, row) -> transitionMap(result), id);
        return new WorkflowHistoryResponse(executions, transitions);
    }

    @Transactional
    public WorkflowInstanceResponse cancel(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        WorkflowInstanceResponse before = instance(id);
        UUID status = concept("WORKFLOW_CANCELLED", "WORKFLOW_STATUS");
        jdbc.update("""
            update workflow_instance set status_concept_id = ?, cancelled_at = now(),
                   updated_at = now(), version = version + 1
             where id = ? and completed_at is null and cancelled_at is null
            """, status, id);
        WorkflowInstanceResponse after = instance(id);
        audit.record("workflow.instance.cancelled.v1", "WorkflowInstance", id, before, after);
        outbox.publish(principal.tenantId(), "WorkflowInstance", id,
            "workflow.instance.cancelled.v1", "workflow.instance.cancelled.v1",
            Map.of("workflowInstanceId", id), null);
        return after;
    }

    @Transactional
    public WorkflowInstanceResponse retry(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        WorkflowInstanceResponse current = instance(id);
        UUID token = jdbc.queryForObject("""
            select workflow_token_id from workflow_execution
             where workflow_instance_id = ? and error_code is not null
             order by created_at desc limit 1
            """, UUID.class, id);
        run(id, token, current.contextSnapshot(), principal);
        audit.record("workflow.instance.retried.v1", "WorkflowInstance", id, current,
            instance(id));
        return instance(id);
    }

    @Transactional
    public WorkflowInstanceResponse resume(UUID executionId, JsonNode nodeOutput) {
        TenantPrincipal principal = currentTenant.require();
        WaitingExecution waiting = jdbc.queryForObject("""
            select e.workflow_instance_id, e.workflow_token_id, e.node_id,
                   i.context_snapshot_json::text
              from workflow_execution e
              join workflow_instance i on i.id = e.workflow_instance_id
             where e.id = ? and e.completed_at is null and e.error_code is null
            """, (result, row) -> new WaitingExecution(
                result.getObject("workflow_instance_id", UUID.class),
                result.getObject("workflow_token_id", UUID.class),
                result.getObject("node_id", UUID.class),
                parse(result.getString(4))), executionId);
        JsonNode output = nodeOutput == null ? JsonNodeFactory.instance.objectNode() : nodeOutput;
        jdbc.update("""
            update workflow_execution set execution_status_concept_id = ?,
                   output_snapshot_json = ?::jsonb, completed_at = now()
             where id = ?
            """, concept("EXECUTION_COMPLETED", "EXECUTION_STATUS"),
            json(output), executionId);
        Map<String, Object> storedVariables = mapper.convertValue(waiting.context(),
            new TypeReference<Map<String, Object>>() { });
        Map<String, Object> variables = new HashMap<>(storedVariables);
        variables.put("nodeOutput", mapper.convertValue(output, Object.class));
        List<RuntimeTransition> matched = transitions(waiting.nodeId()).stream()
            .filter(transition -> conditions.evaluate(new WorkflowConditionContext(variables),
                transition.condition()).matched())
            .sorted(Comparator.comparingInt(RuntimeTransition::priority).reversed())
            .toList();
        if (matched.isEmpty()) {
            throw new IllegalStateException("Completed waiting node has no matching transition");
        }
        RuntimeNode currentNode = runtimeNode(waiting.tokenId());
        int selectedCount = currentNode.configuration().path("parallel").asBoolean(false)
            ? matched.size() : 1;
        ArrayDeque<UUID> next = new ArrayDeque<>();
        for (int index = 0; index < selectedCount; index++) {
            RuntimeTransition transition = matched.get(index);
            UUID token = index == 0 ? waiting.tokenId()
                : createToken(principal.tenantId(), waiting.instanceId(),
                    transition.targetNodeId(), waiting.tokenId(),
                    concept("WORKFLOW_ACTIVE", "WORKFLOW_STATUS"));
            if (index == 0) {
                jdbc.update("""
                    update workflow_token set current_node_id = ?, updated_at = now(),
                           version = version + 1 where id = ?
                    """, transition.targetNodeId(), waiting.tokenId());
            }
            jdbc.update("""
                insert into workflow_transition_log (
                    id, organization_id, workflow_instance_id, source_node_id,
                    target_node_id, transition_id, decision_context_json,
                    triggered_by_type, triggered_by_id, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, 'USER', ?, now())
                """, UUID.randomUUID(), principal.tenantId(), waiting.instanceId(),
                waiting.nodeId(), transition.targetNodeId(), transition.id(), json(output),
                principal.subject());
            next.add(token);
        }
        while (!next.isEmpty()) {
            run(waiting.instanceId(), next.removeFirst(), waiting.context(), principal);
        }
        WorkflowInstanceResponse result = instance(waiting.instanceId());
        audit.record("workflow.node.resumed.v1", "WorkflowInstance",
            waiting.instanceId(), null, Map.of("executionId", executionId));
        return result;
    }

    private void run(UUID instanceId, UUID initialTokenId, JsonNode snapshot,
                     TenantPrincipal principal) {
        ArrayDeque<UUID> tokens = new ArrayDeque<>();
        tokens.add(initialTokenId);
        int steps = 0;
        Map<String, Object> variables = mapper.convertValue(snapshot,
            new TypeReference<Map<String, Object>>() { });
        while (!tokens.isEmpty()) {
            if (++steps > RUNTIME_STEP_LIMIT) {
                throw new IllegalStateException("Workflow runtime safety limit exceeded");
            }
            UUID tokenId = tokens.removeFirst();
            RuntimeNode node = runtimeNode(tokenId);
            UUID pending = concept("EXECUTION_PENDING", "EXECUTION_STATUS");
            UUID complete = concept("EXECUTION_COMPLETED", "EXECUTION_STATUS");
            UUID executionId = UUID.randomUUID();
            jdbc.update("""
                insert into workflow_execution (
                    id, organization_id, workflow_instance_id, workflow_token_id,
                    node_id, execution_status_concept_id, input_snapshot_json,
                    output_snapshot_json, started_at, retry_count, created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, '{}'::jsonb, now(), 0, now())
                """, executionId, principal.tenantId(), instanceId, tokenId,
                node.id(), pending, json(snapshot));
            outbox.publish(principal.tenantId(), "WorkflowInstance", instanceId,
                "workflow.node.entered.v1", "workflow.node.entered.v1",
                Map.of("workflowInstanceId", instanceId, "nodeId", node.id()), null);
            metrics.sprint7("workflow_node_execution_total");
            WorkflowModels.WorkflowNodeExecutionResult result;
            try {
                result = handlers.require(node.typeConceptCode()).execute(
                    new WorkflowNodeExecutionContext(principal.tenantId(), instanceId,
                        tokenId, executionId, node.id(), node.typeConceptCode(),
                        node.configuration(), snapshot, variables));
            } catch (RuntimeException failure) {
                metrics.sprint7("workflow_instance_failed_total");
                throw failure;
            }
            jdbc.update("""
                update workflow_execution
                   set execution_status_concept_id = ?, output_snapshot_json = ?::jsonb,
                       completed_at = case when ? then now() else null end
                 where id = ?
                """, result.completed() ? complete : pending, json(result.output()),
                result.completed(), executionId);
            if (result.completed()) {
                outbox.publish(principal.tenantId(), "WorkflowInstance", instanceId,
                    "workflow.node.completed.v1", "workflow.node.completed.v1",
                    Map.of("workflowInstanceId", instanceId, "nodeId", node.id(),
                        "workflowExecutionId", executionId), null);
            }
            if (result.waiting()) {
                continue;
            }
            if (node.configuration().path("terminal").asBoolean(false)) {
                completeInstance(instanceId, tokenId, principal);
                continue;
            }
            List<RuntimeTransition> matched = transitions(node.id()).stream()
                .filter(transition -> conditions.evaluate(
                    new WorkflowConditionContext(variables), transition.condition()).matched())
                .sorted(Comparator.comparingInt(RuntimeTransition::priority).reversed())
                .toList();
            if (matched.isEmpty()) {
                jdbc.update("""
                    update workflow_execution set error_code = 'WORKFLOW_DEAD_END',
                           error_message = 'No transition condition matched'
                     where id = ?
                    """, executionId);
                metrics.sprint7("workflow_dead_end_total");
                throw new IllegalStateException("Workflow reached a condition dead-end at "
                    + node.code());
            }
            boolean parallel = node.configuration().path("parallel").asBoolean(false);
            int selectedCount = parallel ? matched.size() : 1;
            for (int index = 0; index < selectedCount; index++) {
                RuntimeTransition transition = matched.get(index);
                UUID nextToken = index == 0 ? tokenId
                    : createToken(principal.tenantId(), instanceId, transition.targetNodeId(),
                        tokenId, concept("WORKFLOW_ACTIVE", "WORKFLOW_STATUS"));
                if (index == 0) {
                    jdbc.update("""
                        update workflow_token set current_node_id = ?, updated_at = now(),
                               version = version + 1 where id = ?
                        """, transition.targetNodeId(), tokenId);
                }
                jdbc.update("""
                    insert into workflow_transition_log (
                        id, organization_id, workflow_instance_id, source_node_id,
                        target_node_id, transition_id, decision_context_json,
                        triggered_by_type, triggered_by_id, occurred_at
                    ) values (?, ?, ?, ?, ?, ?, ?::jsonb, 'USER', ?, now())
                    """, UUID.randomUUID(), principal.tenantId(), instanceId, node.id(),
                    transition.targetNodeId(), transition.id(), json(snapshot),
                    principal.subject());
                outbox.publish(principal.tenantId(), "WorkflowInstance", instanceId,
                    "workflow.transition.executed.v1", "workflow.transition.executed.v1",
                    Map.of("workflowInstanceId", instanceId,
                        "transitionId", transition.id()), null);
                metrics.sprint7("workflow_transition_total");
                tokens.addLast(nextToken);
            }
        }
    }

    private void completeInstance(UUID instanceId, UUID tokenId, TenantPrincipal principal) {
        UUID complete = concept("WORKFLOW_COMPLETED", "WORKFLOW_STATUS");
        jdbc.update("""
            update workflow_token set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, complete, tokenId);
        Integer open = jdbc.queryForObject("""
            select count(*) from workflow_token
             where workflow_instance_id = ? and status_concept_id <> ?
            """, Integer.class, instanceId, complete);
        if (open != null && open == 0) {
            jdbc.update("""
                update workflow_instance set status_concept_id = ?, completed_at = now(),
                       updated_at = now(), version = version + 1 where id = ?
                """, complete, instanceId);
            outbox.publish(principal.tenantId(), "WorkflowInstance", instanceId,
                "workflow.instance.completed.v1", "workflow.instance.completed.v1",
                Map.of("workflowInstanceId", instanceId), null);
        }
    }

    private UUID createToken(UUID organizationId, UUID instanceId, UUID nodeId,
                             UUID parentId, UUID status) {
        UUID tokenId = UUID.randomUUID();
        jdbc.update("""
            insert into workflow_token (
                id, organization_id, workflow_instance_id, current_node_id,
                status_concept_id, parent_token_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, now(), now())
            """, tokenId, organizationId, instanceId, nodeId, status, parentId);
        return tokenId;
    }

    private RuntimeNode runtimeNode(UUID tokenId) {
        return jdbc.queryForObject("""
            select n.id, n.node_code, c.concept_code, n.configuration_json::text
              from workflow_token t
              join workflow_node n on n.id = t.current_node_id
              join ontology_concept c on c.id = n.node_type_concept_id
             where t.id = ?
            """, (result, row) -> new RuntimeNode(result.getObject("id", UUID.class),
                result.getString("node_code"), result.getString("concept_code"),
                parse(result.getString(4))), tokenId);
    }

    private List<RuntimeTransition> transitions(UUID nodeId) {
        return jdbc.query("""
            select id, target_node_id, condition_expression_json::text, priority
              from workflow_transition where source_node_id = ?
            """, (result, row) -> new RuntimeTransition(
                result.getObject("id", UUID.class),
                result.getObject("target_node_id", UUID.class),
                parse(result.getString(3)), result.getInt("priority")), nodeId);
    }

    private WorkflowDefinitionResponse definition(ResultSet result, int row)
        throws SQLException {
        return new WorkflowDefinitionResponse(result.getObject("id", UUID.class),
            result.getString("workflow_code"), result.getString("name"),
            result.getString("description"), result.getString("scope"),
            result.getObject("entity_type_concept_id", UUID.class),
            result.getObject("active_version_id", UUID.class),
            result.getTimestamp("created_at").toInstant(),
            result.getTimestamp("updated_at").toInstant());
    }

    private Map<String, Object> executionMap(ResultSet result) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", result.getObject("id", UUID.class));
        value.put("workflowTokenId", result.getObject("workflow_token_id", UUID.class));
        value.put("nodeId", result.getObject("node_id", UUID.class));
        value.put("nodeCode", result.getString("node_code"));
        value.put("status", result.getString("status"));
        value.put("input", parse(result.getString(6)));
        value.put("output", parse(result.getString(7)));
        value.put("startedAt", result.getTimestamp("started_at").toInstant());
        value.put("completedAt", instant(result, "completed_at"));
        value.put("errorCode", result.getString("error_code"));
        value.put("errorMessage", result.getString("error_message"));
        value.put("retryCount", result.getInt("retry_count"));
        return value;
    }

    private Map<String, Object> transitionMap(ResultSet result) throws SQLException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", result.getObject("id", UUID.class));
        value.put("sourceNodeCode", result.getString("source_node_code"));
        value.put("targetNodeCode", result.getString("target_node_code"));
        value.put("transitionId", result.getObject("transition_id", UUID.class));
        value.put("decisionContext", parse(result.getString(5)));
        value.put("triggeredByType", result.getString("triggered_by_type"));
        value.put("triggeredById", result.getString("triggered_by_id"));
        value.put("occurredAt", result.getTimestamp("occurred_at").toInstant());
        return value;
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

    private JsonNode orObject(JsonNode value) {
        return value == null || value.isNull() ? JsonNodeFactory.instance.objectNode() : value;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Workflow data cannot be serialized", exception);
        }
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored workflow JSON is invalid", exception);
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record RuntimeNode(UUID id, String code, String typeConceptCode,
                               JsonNode configuration) {
    }

    private record RuntimeTransition(UUID id, UUID targetNodeId,
                                     JsonNode condition, int priority) {
    }

    private record WaitingExecution(UUID instanceId, UUID tokenId, UUID nodeId,
                                    JsonNode context) {
    }
}
