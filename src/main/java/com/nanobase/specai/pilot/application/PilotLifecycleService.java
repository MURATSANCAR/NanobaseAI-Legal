package com.nanobase.specai.pilot.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.pilot.api.PilotContracts.AssignFeedbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CanaryRequest;
import com.nanobase.specai.pilot.api.PilotContracts.AdjudicationRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ConfigurationRollbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ConceptResponse;
import com.nanobase.specai.pilot.api.PilotContracts.CreateConfigurationSnapshotRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateExperimentRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateFeedbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateImprovementCandidateRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreatePilotSessionRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateDisagreementRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateQualityDebtRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateReproductionPackageRequest;
import com.nanobase.specai.pilot.api.PilotContracts.CreateRegressionSuiteRequest;
import com.nanobase.specai.pilot.api.PilotContracts.PilotDashboardResponse;
import com.nanobase.specai.pilot.api.PilotContracts.RecordExperimentResultRequest;
import com.nanobase.specai.pilot.api.PilotContracts.RecordPilotEventRequest;
import com.nanobase.specai.pilot.api.PilotContracts.RecordPilotMetricRequest;
import com.nanobase.specai.pilot.api.PilotContracts.ResolveFeedbackRequest;
import com.nanobase.specai.pilot.api.PilotContracts.RootCauseSuggestionResponse;
import com.nanobase.specai.pilot.api.PilotContracts.StartExperimentRunRequest;
import com.nanobase.specai.pilot.api.PilotContracts.TriageFeedbackRequest;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotLifecycleService {
    private static final Set<String> TELEMETRY_KEYS = Set.of(
        "duration_ms", "page_count", "clause_count", "requirement_count",
        "model_profile", "schema_failure", "grounding_result", "retry_count",
        "manual_review", "expert_decision_type", "correction_type",
        "queue_duration_ms", "parser_warning_code", "ocr_quality_level");
    private static final Set<String> ERROR_CLASSIFICATIONS = Set.of(
        "BUG", "MODEL_ERROR", "DATA_ERROR", "CONFIGURATION_ERROR", "LABEL_ERROR");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final SensitiveDataSanitizer sanitizer;
    private final ErrorRootCauseAnalyzer rootCauseAnalyzer;
    private final ErrorPriorityPolicy priorityPolicy;
    private final ExperimentProviderRegistry experimentProviders;
    private final AuditService audit;
    private final OutboxService outbox;

    public PilotLifecycleService(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        CurrentTenant currentTenant,
        SensitiveDataSanitizer sanitizer,
        ErrorRootCauseAnalyzer rootCauseAnalyzer,
        ErrorPriorityPolicy priorityPolicy,
        ExperimentProviderRegistry experimentProviders,
        AuditService audit,
        OutboxService outbox
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.sanitizer = sanitizer;
        this.rootCauseAnalyzer = rootCauseAnalyzer;
        this.priorityPolicy = priorityPolicy;
        this.experimentProviders = experimentProviders;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<ConceptResponse> concepts(String catalogCode) {
        UUID organizationId = tenant().tenantId();
        return jdbc.query("""
            select c.id, catalog.catalog_code, c.concept_code, c.name, c.description,
                   c.metadata_json::text, c.sort_order
              from sprint9_concept c
              join sprint9_concept_catalog catalog on catalog.id = c.catalog_id
             where catalog.catalog_code = ? and c.active = true and catalog.active = true
               and (c.organization_id = ? or c.organization_id is null)
             order by (c.organization_id is not null) desc, c.sort_order, c.concept_code
            """, (result, row) -> new ConceptResponse(
                result.getObject("id", UUID.class),
                result.getString("catalog_code"),
                result.getString("concept_code"),
                result.getString("name"),
                result.getString("description"),
                tree(result.getString(6)),
                result.getInt("sort_order")), catalogCode, organizationId);
    }

    @Transactional
    public Map<String, Object> createConfigurationSnapshot(
        CreateConfigurationSnapshotRequest request
    ) {
        TenantPrincipal principal = tenant();
        UUID typeId = concept("SNAPSHOT_TYPE", request.snapshotTypeCode());
        ObjectNode payload = mapper.createObjectNode();
        payload.set("modelDeployments", arrayOrEmpty(request.modelDeployments()));
        payload.set("modelProfiles", arrayOrEmpty(request.modelProfiles()));
        payload.set("promptVersions", arrayOrEmpty(request.promptVersions()));
        payload.set("policyVersions", arrayOrEmpty(request.policyVersions()));
        payload.set("ontologyVersions", arrayOrEmpty(request.ontologyVersions()));
        payload.set("terminologySnapshots", arrayOrEmpty(request.terminologySnapshots()));
        payload.set("outputSchemaVersions", arrayOrEmpty(request.outputSchemaVersions()));
        payload.set("workflowVersions", arrayOrEmpty(request.workflowVersions()));
        payload.set("featureFlags", objectOrEmpty(request.featureFlags()));
        SensitiveDataSanitizer.SanitizedPayload sanitized = sanitizer.sanitize(payload);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
            insert into configuration_snapshot (
                id, organization_id, snapshot_type_concept_id, model_deployments_json,
                model_profiles_json, prompt_versions_json, policy_versions_json,
                ontology_versions_json, terminology_snapshots_json,
                output_schema_versions_json, workflow_versions_json, feature_flags_json,
                deployment_configuration_hash, created_at
            ) values (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
                      ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, now())
            on conflict (organization_id, deployment_configuration_hash) do nothing
            """, id, principal.tenantId(), typeId,
            json(sanitized.value().get("modelDeployments")),
            json(sanitized.value().get("modelProfiles")),
            json(sanitized.value().get("promptVersions")),
            json(sanitized.value().get("policyVersions")),
            json(sanitized.value().get("ontologyVersions")),
            json(sanitized.value().get("terminologySnapshots")),
            json(sanitized.value().get("outputSchemaVersions")),
            json(sanitized.value().get("workflowVersions")),
            json(sanitized.value().get("featureFlags")), sanitized.contentHash());
        if (inserted == 0) {
            id = jdbc.queryForObject("""
                select id from configuration_snapshot
                 where organization_id = ? and deployment_configuration_hash = ?
                """, UUID.class, principal.tenantId(), sanitized.contentHash());
        } else {
            audit.record("configuration.snapshot.created.v1", "ConfigurationSnapshot",
                id, null, Map.of("hash", sanitized.contentHash(),
                    "removedPaths", sanitized.removedPaths()));
        }
        return snapshot(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(UUID id) {
        return one("""
            select s.id, c.concept_code as "snapshotType", s.model_deployments_json,
                   s.model_profiles_json, s.prompt_versions_json, s.policy_versions_json,
                   s.ontology_versions_json, s.terminology_snapshots_json,
                   s.output_schema_versions_json, s.workflow_versions_json,
                   s.feature_flags_json, s.deployment_configuration_hash, s.created_at
              from configuration_snapshot s
              join sprint9_concept c on c.id = s.snapshot_type_concept_id
             where s.id = ?
            """, id);
    }

    @Transactional
    public Map<String, Object> createPilotSession(CreatePilotSessionRequest request) {
        TenantPrincipal principal = tenant();
        requireProject(request.projectId());
        requireOwned("configuration_snapshot", request.configurationSnapshotId());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into pilot_session (
                id, organization_id, project_id, pilot_phase_concept_id, started_at,
                status_concept_id, configuration_snapshot_id, created_by, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
            """, id, principal.tenantId(), request.projectId(),
            concept("PILOT_PHASE", request.pilotPhaseCode()),
            Timestamp.from(request.startedAt() == null ? Instant.now() : request.startedAt()),
            concept("PILOT_STATUS", "ACTIVE"), request.configurationSnapshotId(),
            principal.subject());
        audit.record("pilot.session.started.v1", "PilotSession", id, null,
            Map.of("projectId", request.projectId(), "phase", request.pilotPhaseCode()));
        return pilotSession(id);
    }

    @Transactional
    public Map<String, Object> recordPilotEvent(
        UUID sessionId,
        RecordPilotEventRequest request
    ) {
        TenantPrincipal principal = tenant();
        requireOwned("pilot_session", sessionId);
        validateTelemetry(request.metadata());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into pilot_event (
                id, organization_id, pilot_session_id, event_type_concept_id,
                entity_type, entity_id, correlation_id, metadata_json, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
            """, id, principal.tenantId(), sessionId,
            concept("PILOT_EVENT_TYPE", request.eventTypeCode()), request.entityType(),
            request.entityId(), request.correlationId() == null
                ? RequestContext.current().correlationId() : request.correlationId(),
            json(request.metadata()));
        return one("""
            select id, pilot_session_id, entity_type, entity_id, correlation_id,
                   metadata_json, occurred_at from pilot_event where id = ?
            """, id);
    }

    @Transactional
    public Map<String, Object> recordPilotMetric(
        UUID sessionId,
        RecordPilotMetricRequest request
    ) {
        TenantPrincipal principal = tenant();
        requireOwned("pilot_session", sessionId);
        JsonNode dimensions = objectOrEmpty(request.dimensions());
        SensitiveDataSanitizer.SanitizedPayload sanitized = sanitizer.sanitize(dimensions);
        UUID metricId = jdbc.query("""
            select id from pilot_metric_definition
             where metric_code = ? and active = true
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc limit 1
            """, result -> result.next() ? result.getObject(1, UUID.class) : null,
            request.metricCode(), principal.tenantId());
        if (metricId == null) {
            throw new IllegalArgumentException("Active pilot metric not found: "
                + request.metricCode());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into pilot_metric_snapshot (
                id, organization_id, pilot_session_id, metric_definition_id,
                metric_value, dimension_values_json, measured_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, ?)
            """, id, principal.tenantId(), sessionId, metricId, request.metricValue(),
            json(sanitized.value()), Timestamp.from(request.measuredAt() == null
                ? Instant.now() : request.measuredAt()));
        return one("""
            select id, pilot_session_id, metric_definition_id, metric_value,
                   dimension_values_json, measured_at
              from pilot_metric_snapshot where id = ?
            """, id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> pilotSession(UUID id) {
        return one("""
            select p.id, p.project_id, phase.concept_code as "pilotPhase",
                   p.started_at, p.completed_at, status.concept_code as status,
                   p.configuration_snapshot_id, p.created_by, p.created_at
              from pilot_session p
              join sprint9_concept phase on phase.id = p.pilot_phase_concept_id
              join sprint9_concept status on status.id = p.status_concept_id
             where p.id = ?
            """, id);
    }

    @Transactional
    public Map<String, Object> createFeedback(CreateFeedbackRequest request) {
        TenantPrincipal principal = tenant();
        requireProject(request.projectId());
        if (request.pilotSessionId() != null) {
            requireOwned("pilot_session", request.pilotSessionId());
        }
        UUID id = UUID.randomUUID();
        String code = code("FB", id);
        String description = safeText(request.description());
        String expected = safeText(request.expectedBehavior());
        String actual = safeText(request.actualBehavior());
        jdbc.update("""
            insert into feedback_case (
                id, organization_id, project_id, pilot_session_id, feedback_code,
                feedback_type_concept_id, classification_concept_id, severity_concept_id,
                entity_type, entity_id, title, description, expected_behavior,
                actual_behavior, status_concept_id, reported_by, reported_at,
                created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), now(), 0)
            """, id, principal.tenantId(), request.projectId(), request.pilotSessionId(), code,
            concept("FEEDBACK_TYPE", request.feedbackTypeCode()),
            concept("FEEDBACK_CLASSIFICATION", request.classificationCode()),
            concept("SEVERITY", request.severityCode()), request.entityType(),
            request.entityId(), safeText(request.title()), description, expected, actual,
            concept("FEEDBACK_STATUS", "OPEN"), principal.subject());
        if (request.evidence() != null) {
            request.evidence().forEach(draft -> {
                SensitiveDataSanitizer.SanitizedPayload sanitized =
                    sanitizer.sanitize(draft.sanitizedSnapshot());
                jdbc.update("""
                    insert into feedback_evidence (
                        id, organization_id, feedback_case_id, evidence_type_concept_id,
                        reference_entity_type, reference_entity_id,
                        sanitized_snapshot_json, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?::jsonb, now())
                    """, UUID.randomUUID(), principal.tenantId(), id,
                    concept("EVIDENCE_TYPE", draft.evidenceTypeCode()),
                    draft.referenceEntityType(), draft.referenceEntityId(),
                    json(sanitized.value()));
            });
        }
        Map<String, Object> created = feedback(id);
        audit.record("feedback.created.v1", "FeedbackCase", id, null, created);
        publish("feedback.created.v1", "FeedbackCase", id, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> feedbackList(
        String status,
        String severity,
        UUID projectId
    ) {
        TenantPrincipal principal = tenant();
        StringBuilder query = new StringBuilder("""
            select f.id, f.feedback_code, f.project_id, f.pilot_session_id,
                   type.concept_code as "feedbackType",
                   classification.concept_code as classification,
                   severity.concept_code as severity, f.entity_type, f.entity_id,
                   f.title, status.concept_code as status,
                   team.concept_code as "assignedTeam", f.reported_by, f.reported_at,
                   f.resolved_at, f.updated_at, f.version
              from feedback_case f
              join sprint9_concept type on type.id = f.feedback_type_concept_id
              join sprint9_concept classification on classification.id = f.classification_concept_id
              join sprint9_concept severity on severity.id = f.severity_concept_id
              join sprint9_concept status on status.id = f.status_concept_id
              left join sprint9_concept team on team.id = f.assigned_team_concept_id
             where f.organization_id = ?
            """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(principal.tenantId());
        if (status != null && !status.isBlank()) {
            query.append(" and status.concept_code = ?");
            arguments.add(status);
        }
        if (severity != null && !severity.isBlank()) {
            query.append(" and severity.concept_code = ?");
            arguments.add(severity);
        }
        if (projectId != null) {
            query.append(" and f.project_id = ?");
            arguments.add(projectId);
        }
        query.append(" order by f.reported_at desc limit 250");
        return jdbc.queryForList(query.toString(), arguments.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> feedback(UUID id) {
        Map<String, Object> result = one("""
            select f.id, f.feedback_code, f.project_id, f.pilot_session_id,
                   type.concept_code as "feedbackType",
                   classification.concept_code as classification,
                   severity.concept_code as severity, f.entity_type, f.entity_id,
                   f.title, f.description, f.expected_behavior, f.actual_behavior,
                   status.concept_code as status, team.concept_code as "assignedTeam",
                   f.reported_by, f.reported_at, f.resolved_at, f.created_at,
                   f.updated_at, f.version
              from feedback_case f
              join sprint9_concept type on type.id = f.feedback_type_concept_id
              join sprint9_concept classification on classification.id = f.classification_concept_id
              join sprint9_concept severity on severity.id = f.severity_concept_id
              join sprint9_concept status on status.id = f.status_concept_id
              left join sprint9_concept team on team.id = f.assigned_team_concept_id
             where f.id = ?
            """, id);
        result.put("evidence", jdbc.queryForList("""
            select e.id, type.concept_code as "evidenceType",
                   e.reference_entity_type, e.reference_entity_id,
                   e.sanitized_snapshot_json, e.created_at
              from feedback_evidence e
              join sprint9_concept type on type.id = e.evidence_type_concept_id
             where e.feedback_case_id = ? order by e.created_at
            """, id));
        result.put("triage", jdbc.queryForList("""
            select t.id, cause.concept_code as "rootCause",
                   t.secondary_cause_concepts_json, reproducibility.concept_code,
                   t.affected_scope_json, t.impact_score, t.frequency_score,
                   t.priority_score, t.release_blocker, t.analysis_summary,
                   t.analyzer_recommendation_json, t.triaged_by, t.triaged_at, t.version
              from error_triage_record t
              join sprint9_concept cause on cause.id = t.root_cause_concept_id
              join sprint9_concept reproducibility
                on reproducibility.id = t.reproducibility_concept_id
             where t.feedback_case_id = ?
            """, id));
        return result;
    }

    @Transactional
    public Map<String, Object> assignFeedback(UUID id, AssignFeedbackRequest request) {
        Map<String, Object> before = feedback(id);
        int updated = jdbc.update("""
            update feedback_case
               set assigned_team_concept_id = ?, updated_at = now(), version = version + 1
             where id = ? and resolved_at is null
            """, concept("ASSIGNED_TEAM", request.assignedTeamCode()), id);
        requireUpdated(updated, "Open feedback case");
        Map<String, Object> after = feedback(id);
        audit.record("feedback.assigned.v1", "FeedbackCase", id, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> triageFeedback(UUID id, TriageFeedbackRequest request) {
        TenantPrincipal principal = tenant();
        Map<String, Object> feedback = feedback(id);
        JsonNode signals = objectOrEmpty(request.analysisSignals());
        RootCauseAnalysisResult suggestion = rootCauseAnalyzer.analyze(
            new ErrorAnalysisContext(
                value(feedback, "feedbackType", "feedbacktype"),
                value(feedback, "classification"),
                value(feedback, "severity"),
                signals,
                Map.of("feedbackId", id)),
            defaultRootCausePolicy());
        boolean tenantRisk = signals.path("tenantIsolationRisk").asBoolean(false);
        boolean dataLossRisk = signals.path("dataLossRisk").asBoolean(false);
        boolean auditRisk = signals.path("auditIntegrityRisk").asBoolean(false);
        var priority = priorityPolicy.evaluate(
            request.impactScore(), request.frequencyScore(),
            value(feedback, "severity"), request.rootCauseCode(),
            tenantRisk, dataLossRisk, auditRisk);
        UUID triageId = UUID.randomUUID();
        List<String> secondary = request.secondaryCauseCodes() == null
            ? List.of() : request.secondaryCauseCodes();
        secondary.forEach(code -> concept("ROOT_CAUSE", code));
        RootCauseSuggestionResponse response = suggestion(suggestion);
        int inserted = jdbc.update("""
            insert into error_triage_record (
                id, organization_id, feedback_case_id, root_cause_concept_id,
                secondary_cause_concepts_json, reproducibility_concept_id,
                affected_scope_json, impact_score, frequency_score, priority_score,
                release_blocker, analysis_summary, analyzer_recommendation_json,
                triaged_by, triaged_at, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb,
                      ?, now(), now(), now(), 0)
            on conflict (feedback_case_id) do nothing
            """, triageId, principal.tenantId(), id,
            concept("ROOT_CAUSE", request.rootCauseCode()), json(secondary),
            concept("REPRODUCIBILITY", request.reproducibilityCode()),
            json(objectOrEmpty(request.affectedScope())), request.impactScore(),
            request.frequencyScore(), priority.priorityScore(), priority.releaseBlocker(),
            safeText(request.analysisSummary()), json(response), principal.subject());
        if (inserted == 0) {
            throw new IllegalStateException(
                "Triage is immutable after human submission; create a reviewed revision workflow");
        }
        jdbc.update("""
            update feedback_case
               set status_concept_id = ?, updated_at = now(), version = version + 1
             where id = ?
            """, concept("FEEDBACK_STATUS", "TRIAGED"), id);
        Map<String, Object> after = feedback(id);
        audit.record("feedback.triaged.v1", "FeedbackCase", id, feedback, after);
        publish("feedback.triaged.v1", "FeedbackCase", id, after);
        return after;
    }

    @Transactional
    public Map<String, Object> resolveFeedback(UUID id, ResolveFeedbackRequest request) {
        TenantPrincipal principal = tenant();
        Map<String, Object> before = feedback(id);
        String classification = value(before, "classification");
        if (ERROR_CLASSIFICATIONS.contains(classification)
            && !request.createRegressionCase()) {
            throw new IllegalArgumentException(
                "Accepted error feedback requires a regression case before resolution");
        }
        if (request.createRegressionCase()) {
            if (request.regressionSuiteId() == null
                || request.regressionCaseTypeCode() == null
                || request.sanitizedInput() == null
                || request.expectedBehavior() == null) {
                throw new IllegalArgumentException(
                    "Regression suite, type, sanitized input and expected behavior are required");
            }
            requireOwned("regression_suite", request.regressionSuiteId());
            SensitiveDataSanitizer.SanitizedPayload sanitized =
                sanitizer.sanitize(request.sanitizedInput());
            UUID snapshotId = persistInputSnapshot(principal.tenantId(), sanitized);
            jdbc.update("""
                insert into regression_case (
                    id, organization_id, source_feedback_case_id, suite_id, case_code,
                    input_snapshot_id, expected_behavior_json, severity_concept_id,
                    case_type_concept_id, active, created_at, updated_at
                ) select ?, ?, ?, ?, ?, ?, ?::jsonb, f.severity_concept_id, ?, true,
                         now(), now()
                    from feedback_case f where f.id = ?
                """, UUID.randomUUID(), principal.tenantId(), id, request.regressionSuiteId(),
                code("REG", id), snapshotId,
                json(sanitizer.sanitize(request.expectedBehavior()).value()),
                concept("REGRESSION_CASE_TYPE", request.regressionCaseTypeCode()), id);
        }
        int updated = jdbc.update("""
            update feedback_case
               set status_concept_id = ?, resolved_at = now(), updated_at = now(),
                   version = version + 1
             where id = ? and resolved_at is null
            """, concept("FEEDBACK_STATUS", "RESOLVED"), id);
        requireUpdated(updated, "Unresolved feedback case");
        UUID commentId = UUID.randomUUID();
        jdbc.update("""
            insert into feedback_comment (
                id, organization_id, feedback_case_id, author_user_id, comment_text,
                visibility_concept_id, created_at
            ) values (?, ?, ?, ?, ?, ?, now())
            """, commentId, principal.tenantId(), id, principal.subject(),
            safeText(request.resolutionSummary()), concept("VISIBILITY", "TENANT"));
        Map<String, Object> after = feedback(id);
        audit.record("feedback.resolved.v1", "FeedbackCase", id, before, after);
        publish("feedback.resolved.v1", "FeedbackCase", id, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> feedbackHistory(UUID id) {
        requireOwned("feedback_case", id);
        return jdbc.queryForList("""
            select id, event_type, user_id, before_json, after_json,
                   correlation_id, created_at
              from audit_event
             where entity_type = 'FeedbackCase' and entity_id = ?
             order by created_at, id
            """, id);
    }

    @Transactional
    public Map<String, Object> createReproductionPackage(
        CreateReproductionPackageRequest request
    ) {
        TenantPrincipal principal = tenant();
        requireOwned("feedback_case", request.feedbackCaseId());
        requireOwned("configuration_snapshot", request.configurationSnapshotId());
        SensitiveDataSanitizer.SanitizedPayload input =
            sanitizer.sanitize(request.sanitizedInput());
        SensitiveDataSanitizer.SanitizedPayload expected =
            sanitizer.sanitize(request.expectedOutput());
        SensitiveDataSanitizer.SanitizedPayload actual =
            sanitizer.sanitize(request.actualOutput());
        SensitiveDataSanitizer.SanitizedPayload instructions =
            sanitizer.sanitize(request.executionInstructions());
        UUID inputId = persistInputSnapshot(principal.tenantId(), input);
        ObjectNode content = mapper.createObjectNode();
        content.put("feedbackCaseId", request.feedbackCaseId().toString());
        content.put("configurationSnapshotId", request.configurationSnapshotId().toString());
        content.set("input", input.value());
        content.set("expected", expected.value());
        content.set("actual", actual.value());
        content.set("instructions", instructions.value());
        String hash = sanitizer.sha256(content);
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
            insert into reproduction_package (
                id, organization_id, feedback_case_id, sanitized_input_snapshot_id,
                configuration_snapshot_id, expected_output_json, actual_output_json,
                execution_instructions_json, content_hash, created_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, now())
            on conflict (organization_id, content_hash) do nothing
            """, id, principal.tenantId(), request.feedbackCaseId(), inputId,
            request.configurationSnapshotId(), json(expected.value()), json(actual.value()),
            json(instructions.value()), hash);
        if (inserted == 0) {
            id = jdbc.queryForObject("""
                select id from reproduction_package
                 where organization_id = ? and content_hash = ?
                """, UUID.class, principal.tenantId(), hash);
        } else {
            audit.record("reproduction.package.created.v1", "ReproductionPackage",
                id, null, Map.of("feedbackCaseId", request.feedbackCaseId(), "hash", hash));
        }
        return one("""
            select id, feedback_case_id, sanitized_input_snapshot_id,
                   configuration_snapshot_id, expected_output_json, actual_output_json,
                   execution_instructions_json, content_hash, created_at
              from reproduction_package where id = ?
            """, id);
    }

    @Transactional
    public Map<String, Object> createRegressionSuite(CreateRegressionSuiteRequest request) {
        TenantPrincipal principal = tenant();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into regression_suite (
                id, organization_id, suite_code, name, scope, status_concept_id,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, now(), now())
            """, id, principal.tenantId(), request.suiteCode().toUpperCase(Locale.ROOT),
            safeText(request.name()), request.scope(),
            concept("REGRESSION_STATUS", "ACTIVE"));
        Map<String, Object> created = one("""
            select s.id, s.suite_code, s.name, s.scope, status.concept_code as status,
                   s.created_at, s.updated_at
              from regression_suite s
              join sprint9_concept status on status.id = s.status_concept_id
             where s.id = ?
            """, id);
        audit.record("regression.suite.created.v1", "RegressionSuite", id, null, created);
        return created;
    }

    @Transactional
    public Map<String, Object> createImprovementCandidate(
        CreateImprovementCandidateRequest request
    ) {
        TenantPrincipal principal = tenant();
        requireOwned("error_triage_record", request.rootCauseRecordId());
        requireOwned("configuration_snapshot", request.baselineConfigurationSnapshotId());
        requireOwned("configuration_snapshot", request.candidateConfigurationSnapshotId());
        if (request.baselineConfigurationSnapshotId()
            .equals(request.candidateConfigurationSnapshotId())) {
            throw new IllegalArgumentException("Baseline and candidate snapshots must differ");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into improvement_candidate (
                id, organization_id, candidate_code, candidate_type_concept_id,
                root_cause_record_id, title, description, target_component_concept_id,
                baseline_configuration_snapshot_id, candidate_configuration_snapshot_id,
                expected_improvement_json, risk_assessment_json, status_concept_id,
                created_by, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, now(), now(), 0)
            """, id, principal.tenantId(), code("IMP", id),
            concept("CANDIDATE_TYPE", request.candidateTypeCode()),
            request.rootCauseRecordId(), safeText(request.title()),
            safeText(request.description()),
            concept("TARGET_COMPONENT", request.targetComponentCode()),
            request.baselineConfigurationSnapshotId(),
            request.candidateConfigurationSnapshotId(),
            json(sanitizer.sanitize(request.expectedImprovement()).value()),
            json(sanitizer.sanitize(request.riskAssessment()).value()),
            concept("CANDIDATE_STATUS", "DRAFT"), principal.subject());
        Map<String, Object> created = candidate(id);
        audit.record("improvement.candidate.created.v1", "ImprovementCandidate",
            id, null, created);
        publish("improvement.candidate.created.v1", "ImprovementCandidate", id, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> candidates() {
        return jdbc.queryForList("""
            select c.id, c.candidate_code, type.concept_code as "candidateType",
                   c.title, target.concept_code as "targetComponent",
                   status.concept_code as status, c.baseline_configuration_snapshot_id,
                   c.candidate_configuration_snapshot_id, c.expected_improvement_json,
                   c.risk_assessment_json, c.created_by, c.created_at, c.updated_at, c.version
              from improvement_candidate c
              join sprint9_concept type on type.id = c.candidate_type_concept_id
              join sprint9_concept target on target.id = c.target_component_concept_id
              join sprint9_concept status on status.id = c.status_concept_id
             order by c.updated_at desc limit 250
            """);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> candidate(UUID id) {
        Map<String, Object> result = one("""
            select c.id, c.candidate_code, type.concept_code as "candidateType",
                   c.root_cause_record_id, c.title, c.description,
                   target.concept_code as "targetComponent",
                   c.baseline_configuration_snapshot_id,
                   c.candidate_configuration_snapshot_id, c.expected_improvement_json,
                   c.risk_assessment_json, status.concept_code as status,
                   c.created_by, c.created_at, c.updated_at, c.version
              from improvement_candidate c
              join sprint9_concept type on type.id = c.candidate_type_concept_id
              join sprint9_concept target on target.id = c.target_component_concept_id
              join sprint9_concept status on status.id = c.status_concept_id
             where c.id = ?
            """, id);
        result.put("experiments", jdbc.queryForList("""
            select e.id, e.experiment_code, type.concept_code as "experimentType",
                   status.concept_code as status, e.created_at, e.updated_at
              from experiment_definition e
              join sprint9_concept type on type.id = e.experiment_type_concept_id
              join sprint9_concept status on status.id = e.status_concept_id
             where e.improvement_candidate_id = ? order by e.created_at desc
            """, id));
        return result;
    }

    @Transactional
    public Map<String, Object> createExperiment(
        UUID candidateId,
        CreateExperimentRequest request
    ) {
        TenantPrincipal principal = tenant();
        Map<String, Object> candidate = candidate(candidateId);
        UUID id = UUID.randomUUID();
        UUID baseline = uuid(candidate, "baseline_configuration_snapshot_id");
        UUID candidateSnapshot = uuid(candidate, "candidate_configuration_snapshot_id");
        jdbc.update("""
            insert into experiment_definition (
                id, organization_id, improvement_candidate_id, experiment_code, name,
                description, experiment_type_concept_id, baseline_snapshot_id,
                candidate_snapshot_ids_json, dataset_ids_json, metric_configuration_json,
                quality_gate_version_id, status_concept_id, created_by,
                created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?,
                      ?, now(), now(), 0)
            """, id, principal.tenantId(), candidateId, code("EXP", id),
            safeText(request.name()), safeText(request.description()),
            concept("EXPERIMENT_TYPE", request.experimentTypeCode()), baseline,
            json(List.of(candidateSnapshot)),
            json(request.datasetIds() == null ? List.of() : request.datasetIds()),
            json(sanitizer.sanitize(request.metricConfiguration()).value()),
            request.qualityGateVersionId(), concept("EXPERIMENT_STATUS", "DRAFT"),
            principal.subject());
        jdbc.update("""
            update improvement_candidate set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("CANDIDATE_STATUS", "OFFLINE_EVALUATION"), candidateId);
        Map<String, Object> created = experiment(id);
        audit.record("experiment.definition.created.v1", "ExperimentDefinition",
            id, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> experiments() {
        return jdbc.queryForList("""
            select e.id, e.experiment_code, e.name, type.concept_code as "experimentType",
                   status.concept_code as status, e.improvement_candidate_id,
                   e.created_by, e.created_at, e.updated_at, e.version
              from experiment_definition e
              join sprint9_concept type on type.id = e.experiment_type_concept_id
              join sprint9_concept status on status.id = e.status_concept_id
             order by e.updated_at desc limit 250
            """);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> experiment(UUID id) {
        Map<String, Object> result = one("""
            select e.id, e.experiment_code, e.name, e.description,
                   type.concept_code as "experimentType",
                   e.baseline_snapshot_id, e.candidate_snapshot_ids_json,
                   e.dataset_ids_json, e.metric_configuration_json,
                   e.quality_gate_version_id, status.concept_code as status,
                   e.improvement_candidate_id, e.created_by, e.created_at,
                   e.updated_at, e.version
              from experiment_definition e
              join sprint9_concept type on type.id = e.experiment_type_concept_id
              join sprint9_concept status on status.id = e.status_concept_id
             where e.id = ?
            """, id);
        result.put("runs", jdbc.queryForList("""
            select r.id, r.run_number, status.concept_code as status,
                   r.started_at, r.completed_at, r.runtime_configuration_json,
                   r.result_summary_json, r.created_at
              from experiment_run r
              join sprint9_concept status on status.id = r.status_concept_id
             where r.experiment_definition_id = ? order by r.run_number desc
            """, id));
        return result;
    }

    @Transactional
    public Map<String, Object> startExperimentRun(
        UUID experimentId,
        StartExperimentRunRequest request
    ) {
        TenantPrincipal principal = tenant();
        Map<String, Object> experiment = experiment(experimentId);
        int runNumber = jdbc.queryForObject("""
            select coalesce(max(run_number), 0) + 1 from experiment_run
             where experiment_definition_id = ?
            """, Integer.class, experimentId);
        UUID id = UUID.randomUUID();
        JsonNode runtime = sanitizer.sanitize(request == null
            ? JsonNodeFactory.instance.objectNode()
            : request.runtimeConfiguration()).value();
        var plan = experimentProviders.require(value(experiment, "experimentType",
            "experimenttype")).plan(experimentId,
                value(experiment, "experimentType", "experimenttype"), runtime);
        ObjectNode plannedRuntime = mapper.createObjectNode();
        plannedRuntime.set("configuration", plan.runtimeConfiguration());
        plannedRuntime.put("providerCode", plan.providerCode());
        plannedRuntime.put("routingKey", plan.routingKey());
        jdbc.update("""
            insert into experiment_run (
                id, organization_id, experiment_definition_id, run_number,
                status_concept_id, runtime_configuration_json, created_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, now())
            """, id, principal.tenantId(), experimentId, runNumber,
            concept("EXPERIMENT_STATUS", "QUEUED"),
            json(plannedRuntime));
        jdbc.update("""
            update experiment_definition set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("EXPERIMENT_STATUS", "QUEUED"), experimentId);
        Map<String, Object> run = experimentRun(id);
        audit.record("experiment.requested.v1", "ExperimentRun", id, null, run);
        publish("experiment.requested.v1", "ExperimentRun", id, run);
        return run;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> experimentRun(UUID id) {
        Map<String, Object> result = one("""
            select r.id, r.experiment_definition_id, r.run_number,
                   status.concept_code as status, r.started_at, r.completed_at,
                   r.runtime_configuration_json, r.result_summary_json, r.created_at
              from experiment_run r
              join sprint9_concept status on status.id = r.status_concept_id
             where r.id = ?
            """, id);
        result.put("results", experimentResults(id));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> experimentResults(UUID runId) {
        requireOwned("experiment_run", runId);
        return jdbc.queryForList("""
            select id, candidate_snapshot_id, metrics_json, regression_summary_json,
                   resource_usage_json, failure_summary_json, created_at
              from experiment_result where experiment_run_id = ? order by created_at
            """, runId);
    }

    @Transactional
    public Map<String, Object> recordExperimentResult(
        UUID runId,
        RecordExperimentResultRequest request
    ) {
        TenantPrincipal principal = tenant();
        Map<String, Object> run = experimentRun(runId);
        UUID experimentId = uuid(run, "experiment_definition_id");
        Map<String, Object> experiment = experiment(experimentId);
        UUID candidateId = uuid(experiment, "improvement_candidate_id");
        requireOwned("configuration_snapshot", request.candidateSnapshotId());
        UUID resultId = UUID.randomUUID();
        jdbc.update("""
            insert into experiment_result (
                id, organization_id, experiment_run_id, candidate_snapshot_id,
                metrics_json, regression_summary_json, resource_usage_json,
                failure_summary_json, created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, now())
            """, resultId, principal.tenantId(), runId, request.candidateSnapshotId(),
            json(sanitizer.sanitize(request.metrics()).value()),
            json(sanitizer.sanitize(request.regressionSummary()).value()),
            json(sanitizer.sanitize(request.resourceUsage()).value()),
            json(sanitizer.sanitize(request.failureSummary()).value()));
        String status = request.qualityGatePassed() ? "PASS" : "FAIL";
        ObjectNode summary = mapper.createObjectNode();
        summary.put("qualityGatePassed", request.qualityGatePassed());
        summary.put("resultId", resultId.toString());
        jdbc.update("""
            update experiment_run
               set status_concept_id = ?, started_at = coalesce(started_at, created_at),
                   completed_at = now(), result_summary_json = ?::jsonb
             where id = ?
            """, concept("EXPERIMENT_STATUS", status), json(summary), runId);
        jdbc.update("""
            update experiment_definition set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("EXPERIMENT_STATUS", status), experimentId);
        if (!request.qualityGatePassed()) {
            jdbc.update("""
                update improvement_candidate set status_concept_id = ?, updated_at = now(),
                       version = version + 1 where id = ?
                """, concept("CANDIDATE_STATUS", "REJECTED"), candidateId);
        }
        Map<String, Object> completed = experimentRun(runId);
        audit.record("experiment.completed.v1", "ExperimentRun", runId, run, completed);
        publish("experiment.completed.v1", "ExperimentRun", runId, completed);
        publish(request.qualityGatePassed()
                ? "quality-gate.passed.v1" : "quality-gate.failed.v1",
            "ExperimentRun", runId, completed);
        return completed;
    }

    @Transactional
    public Map<String, Object> startShadow(UUID candidateId) {
        TenantPrincipal principal = tenant();
        Map<String, Object> candidate = candidate(candidateId);
        requireOfflinePass(candidateId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into shadow_execution (
                id, organization_id, active_configuration_snapshot_id,
                candidate_configuration_snapshot_id, workload_type, comparison_json,
                status, created_at, improvement_candidate_id
            ) values (?, ?, ?, ?, 'PILOT_FEEDBACK_REPLAY', '{}'::jsonb, 'QUEUED',
                      now(), ?)
            """, id, principal.tenantId(),
            uuid(candidate, "baseline_configuration_snapshot_id"),
            uuid(candidate, "candidate_configuration_snapshot_id"), candidateId);
        jdbc.update("""
            update improvement_candidate set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("CANDIDATE_STATUS", "SHADOW"), candidateId);
        Map<String, Object> shadow = one("""
            select id, active_configuration_snapshot_id,
                   candidate_configuration_snapshot_id, workload_type,
                   comparison_json, status, created_at, completed_at
              from shadow_execution where id = ?
            """, id);
        audit.record("shadow.started.v1", "ShadowExecution", id, null, shadow);
        publish("shadow.started.v1", "ShadowExecution", id, shadow);
        return shadow;
    }

    @Transactional
    public Map<String, Object> completeShadow(UUID shadowId, JsonNode comparison, boolean passed) {
        Map<String, Object> before = one("""
            select id, improvement_candidate_id, status, comparison_json, created_at
              from shadow_execution where id = ?
            """, shadowId);
        requireState(value(before, "status"), Set.of("QUEUED", "RUNNING"), "shadow");
        ObjectNode sanitizedComparison = mapper.createObjectNode();
        sanitizedComparison.setAll((ObjectNode) objectOrEmpty(
            sanitizer.sanitize(comparison).value()));
        sanitizedComparison.put("passed", passed);
        jdbc.update("""
            update shadow_execution set status = ?, comparison_json = ?::jsonb,
                   completed_at = now() where id = ?
            """, passed ? "COMPLETED" : "FAILED",
            json(sanitizedComparison), shadowId);
        Map<String, Object> after = one("""
            select id, improvement_candidate_id, status, comparison_json,
                   created_at, completed_at from shadow_execution where id = ?
            """, shadowId);
        audit.record("shadow.completed.v1", "ShadowExecution", shadowId, before, after);
        publish("shadow.completed.v1", "ShadowExecution", shadowId, after);
        return after;
    }

    @Transactional
    public Map<String, Object> startCanary(UUID candidateId, CanaryRequest request) {
        TenantPrincipal principal = tenant();
        Map<String, Object> candidate = candidate(candidateId);
        Long passedShadow = jdbc.queryForObject("""
            select count(*) from shadow_execution
             where improvement_candidate_id = ? and status = 'COMPLETED'
               and coalesce((comparison_json->>'passed')::boolean, false) = true
            """, Long.class, candidateId);
        if (passedShadow == null || passedShadow == 0) {
            throw new IllegalStateException("A passed shadow comparison is required before canary");
        }
        if (request.projectId() != null) {
            requireProject(request.projectId());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into canary_assignment (
                id, organization_id, project_id, user_group, configuration_snapshot_id,
                traffic_percentage, status, rollback_configuration_snapshot_id,
                valid_from, created_at, updated_at, improvement_candidate_id,
                comparison_json
            ) values (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, now(), now(), now(), ?, '{}'::jsonb)
            """, id, principal.tenantId(), request.projectId(), request.userGroup(),
            uuid(candidate, "candidate_configuration_snapshot_id"),
            request.trafficPercentage(),
            uuid(candidate, "baseline_configuration_snapshot_id"), candidateId);
        jdbc.update("""
            update improvement_candidate set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("CANDIDATE_STATUS", "CANARY"), candidateId);
        Map<String, Object> canary = one("""
            select id, project_id, user_group, configuration_snapshot_id,
                   traffic_percentage, status, rollback_configuration_snapshot_id,
                   valid_from, valid_until, comparison_json, created_at, updated_at
              from canary_assignment where id = ?
            """, id);
        audit.record("canary.started.v1", "CanaryAssignment", id, null, canary);
        publish("canary.started.v1", "CanaryAssignment", id, canary);
        return canary;
    }

    @Transactional
    public Map<String, Object> completeCanary(UUID canaryId, JsonNode comparison, boolean passed) {
        Map<String, Object> before = one("""
            select id, improvement_candidate_id, status, comparison_json
              from canary_assignment where id = ?
            """, canaryId);
        requireState(value(before, "status"), Set.of("ACTIVE", "PAUSED"), "canary");
        UUID candidateId = uuid(before, "improvement_candidate_id");
        ObjectNode sanitizedComparison = mapper.createObjectNode();
        sanitizedComparison.setAll((ObjectNode) objectOrEmpty(
            sanitizer.sanitize(comparison).value()));
        sanitizedComparison.put("passed", passed);
        jdbc.update("""
            update canary_assignment set status = ?, comparison_json = ?::jsonb,
                   completed_at = now(), valid_until = now(), updated_at = now()
             where id = ?
            """, passed ? "COMPLETED" : "ROLLED_BACK",
            json(sanitizedComparison), canaryId);
        jdbc.update("""
            update improvement_candidate set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("CANDIDATE_STATUS", passed ? "APPROVED" : "ROLLED_BACK"),
            candidateId);
        Map<String, Object> after = one("""
            select id, improvement_candidate_id, status, comparison_json,
                   completed_at, valid_until from canary_assignment where id = ?
            """, canaryId);
        audit.record("canary.stopped.v1", "CanaryAssignment", canaryId, before, after);
        publish("canary.stopped.v1", "CanaryAssignment", canaryId, after);
        return after;
    }

    @Transactional
    public Map<String, Object> activateCandidate(UUID candidateId) {
        TenantPrincipal principal = tenant();
        Map<String, Object> before = candidate(candidateId);
        requireState(value(before, "status"), Set.of("APPROVED"), "candidate");
        UUID snapshotId = uuid(before, "candidate_configuration_snapshot_id");
        UUID previousId = uuid(before, "baseline_configuration_snapshot_id");
        jdbc.update("""
            insert into configuration_activation_history (
                id, organization_id, configuration_snapshot_id,
                previous_configuration_snapshot_id, improvement_candidate_id,
                action, reason, approved_by_json, activated_by, created_at
            ) values (?, ?, ?, ?, ?, 'ACTIVATE', ?, ?::jsonb, ?, now())
            """, UUID.randomUUID(), principal.tenantId(), snapshotId, previousId,
            candidateId, "Offline, shadow and canary evidence passed",
            json(List.of(principal.subject())), principal.subject());
        jdbc.update("""
            update improvement_candidate set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("CANDIDATE_STATUS", "ACTIVE"), candidateId);
        Map<String, Object> after = candidate(candidateId);
        audit.record("configuration.activated.v1", "ImprovementCandidate",
            candidateId, before, after);
        publish("configuration.activated.v1", "ImprovementCandidate", candidateId, after);
        return after;
    }

    @Transactional
    public Map<String, Object> rejectCandidate(UUID candidateId) {
        Map<String, Object> before = candidate(candidateId);
        if ("ACTIVE".equals(value(before, "status"))) {
            throw new IllegalStateException("Active configuration must use rollback");
        }
        jdbc.update("""
            update improvement_candidate set status_concept_id = ?, updated_at = now(),
                   version = version + 1 where id = ?
            """, concept("CANDIDATE_STATUS", "REJECTED"), candidateId);
        Map<String, Object> after = candidate(candidateId);
        audit.record("improvement.candidate.rejected.v1", "ImprovementCandidate",
            candidateId, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> rollbackConfiguration(ConfigurationRollbackRequest request) {
        TenantPrincipal principal = tenant();
        requireOwned("configuration_snapshot", request.activeSnapshotId());
        requireOwned("configuration_snapshot", request.targetSnapshotId());
        if (request.activeSnapshotId().equals(request.targetSnapshotId())) {
            throw new IllegalArgumentException("Rollback target must differ from active snapshot");
        }
        if (request.approvers().stream().filter(value -> value != null && !value.isBlank())
            .distinct().count() < 2) {
            throw new IllegalArgumentException(
                "Configuration rollback requires two distinct approvers");
        }
        Long validTarget = jdbc.queryForObject("""
            select count(*) from configuration_activation_history history
             where history.organization_id = ?
               and history.configuration_snapshot_id = ?
               and history.previous_configuration_snapshot_id = ?
               and history.action = 'ACTIVATE'
            """, Long.class, principal.tenantId(), request.activeSnapshotId(),
            request.targetSnapshotId());
        if (validTarget == null || validTarget == 0) {
            throw new IllegalStateException(
                "Rollback target is not the recorded predecessor of the active snapshot");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into configuration_activation_history (
                id, organization_id, configuration_snapshot_id,
                previous_configuration_snapshot_id, action, reason,
                approved_by_json, activated_by, created_at
            ) values (?, ?, ?, ?, 'ROLLBACK', ?, ?::jsonb, ?, now())
            """, id, principal.tenantId(), request.targetSnapshotId(),
            request.activeSnapshotId(), safeText(request.reason()),
            json(request.approvers()), principal.subject());
        Map<String, Object> result = one("""
            select id, configuration_snapshot_id, previous_configuration_snapshot_id,
                   action, reason, approved_by_json, activated_by, created_at
              from configuration_activation_history where id = ?
            """, id);
        audit.record("configuration.rolled-back.v1", "ConfigurationActivation",
            id, null, result);
        publish("configuration.rolled-back.v1", "ConfigurationActivation", id, result);
        return result;
    }

    @Transactional
    public Map<String, Object> createQualityDebt(CreateQualityDebtRequest request) {
        TenantPrincipal principal = tenant();
        Map<String, Object> feedback = feedback(request.feedbackCaseId());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into quality_debt_item (
                id, organization_id, feedback_case_id, debt_type_concept_id,
                severity_concept_id, business_impact, technical_impact, workaround,
                target_release, owner_user_id, acceptance_status_concept_id,
                compensating_controls_json, created_at, updated_at, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now(), 0)
            """, id, principal.tenantId(), request.feedbackCaseId(),
            concept("QUALITY_DEBT_TYPE", request.debtTypeCode()),
            concept("SEVERITY", request.severityCode()),
            safeText(request.businessImpact()), safeText(request.technicalImpact()),
            safeText(request.workaround()), request.targetRelease(), request.ownerUserId(),
            concept("QUALITY_DEBT_STATUS", "PROPOSED"),
            json(sanitizer.sanitize(request.compensatingControls()).value()));
        Map<String, Object> result = qualityDebt(id);
        audit.record("quality-debt.proposed.v1", "QualityDebtItem", id,
            Map.of("feedback", feedback), result);
        return result;
    }

    @Transactional
    public Map<String, Object> acceptQualityDebt(UUID id) {
        TenantPrincipal principal = tenant();
        Map<String, Object> before = qualityDebt(id);
        String severity = value(before, "severity");
        String cause = value(before, "rootCause", "rootcause");
        if ("CRITICAL".equals(severity)
            || Set.of("AUTHORIZATION", "SECURITY_SCAN", "INFRASTRUCTURE").contains(cause)) {
            throw new IllegalStateException(
                "Critical security, tenant isolation or data-loss risk cannot be quality debt");
        }
        Object controls = before.get("compensating_controls_json");
        JsonNode parsedControls = tree(controls == null ? "[]" : String.valueOf(controls));
        if (!parsedControls.isArray() || parsedControls.isEmpty()) {
            throw new IllegalStateException(
                "Accepted quality debt requires compensating controls");
        }
        jdbc.update("""
            update quality_debt_item
               set acceptance_status_concept_id = ?, accepted_by = ?, accepted_at = now(),
                   updated_at = now(), version = version + 1
             where id = ? and accepted_at is null
            """, concept("QUALITY_DEBT_STATUS", "ACCEPTED"), principal.subject(), id);
        Map<String, Object> after = qualityDebt(id);
        audit.record("quality-debt.accepted.v1", "QualityDebtItem", id, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> createDisagreement(CreateDisagreementRequest request) {
        TenantPrincipal principal = tenant();
        if (request.reviewerAId().equals(request.reviewerBId())) {
            throw new IllegalArgumentException("Independent reviewers must differ");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into review_disagreement (
                id, organization_id, entity_type, entity_id, reviewer_a_id,
                reviewer_b_id, decision_a_json, decision_b_json,
                disagreement_concept_id, status_concept_id, created_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, now())
            """, id, principal.tenantId(), request.entityType(), request.entityId(),
            request.reviewerAId(), request.reviewerBId(),
            json(sanitizer.sanitize(request.decisionA()).value()),
            json(sanitizer.sanitize(request.decisionB()).value()),
            concept("DISAGREEMENT", request.disagreementCode()),
            concept("REVIEW_STATUS", "IN_ADJUDICATION"));
        Map<String, Object> result = disagreement(id);
        audit.record("review.disagreement.created.v1", "ReviewDisagreement", id,
            null, result);
        return result;
    }

    @Transactional
    public Map<String, Object> adjudicate(UUID id, AdjudicationRequest request) {
        TenantPrincipal principal = tenant();
        Map<String, Object> before = disagreement(id);
        if (principal.subject().equals(value(before, "reviewer_a_id"))
            || principal.subject().equals(value(before, "reviewer_b_id"))) {
            throw new IllegalStateException(
                "Adjudicator must be independent from both reviewers");
        }
        int updated = jdbc.update("""
            update review_disagreement
               set adjudicator_user_id = ?, final_decision_json = ?::jsonb,
                   status_concept_id = ?, resolved_at = now()
             where id = ? and resolved_at is null
            """, principal.subject(),
            json(sanitizer.sanitize(request.finalDecision()).value()),
            concept("REVIEW_STATUS", "RESOLVED"), id);
        requireUpdated(updated, "Open disagreement");
        Map<String, Object> after = disagreement(id);
        audit.record("review.disagreement.adjudicated.v1", "ReviewDisagreement",
            id, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public PilotDashboardResponse dashboard() {
        UUID organizationId = tenant().tenantId();
        long total = count("select count(*) from feedback_case where organization_id = ?",
            organizationId);
        long open = count("""
            select count(*) from feedback_case f join sprint9_concept c on c.id = f.status_concept_id
             where f.organization_id = ? and c.concept_code not in ('RESOLVED','CLOSED')
            """, organizationId);
        long blockers = count("""
            select count(*) from error_triage_record
             where organization_id = ? and release_blocker = true
            """, organizationId);
        Double meanHours = jdbc.queryForObject("""
            select coalesce(avg(extract(epoch from (resolved_at - reported_at))) / 3600, 0)
              from feedback_case where organization_id = ? and resolved_at is not null
            """, Double.class, organizationId);
        long regressionCases = count("""
            select count(*) from regression_case where organization_id = ?
            """, organizationId);
        Double satisfaction = jdbc.queryForObject("""
            select avg((answer.value)::numeric)
              from user_satisfaction_survey survey,
                   lateral jsonb_each_text(survey.answers_json) answer
             where survey.organization_id = ?
               and answer.value ~ '^[0-9]+([.][0-9]+)?$'
            """, Double.class, organizationId);
        JsonNode config = uiConfiguration("PILOT_QUALITY_DASHBOARD");
        List<Map<String, Object>> distribution = jdbc.queryForList("""
            select cause.concept_code as cause, count(*) as count
              from error_triage_record triage
              join sprint9_concept cause on cause.id = triage.root_cause_concept_id
             where triage.organization_id = ?
             group by cause.concept_code order by count(*) desc
            """, organizationId);
        List<Map<String, Object>> trend = jdbc.queryForList("""
            select date_trunc('week', reported_at) as week, count(*) as reported,
                   count(*) filter (where resolved_at is not null) as resolved
              from feedback_case where organization_id = ?
             group by date_trunc('week', reported_at) order by week
            """, organizationId);
        List<Map<String, Object>> recurring = jdbc.queryForList("""
            select type.concept_code as "feedbackType", f.entity_type, count(*) as count
              from feedback_case f
              join sprint9_concept type on type.id = f.feedback_type_concept_id
             where f.organization_id = ?
             group by type.concept_code, f.entity_type
            having count(*) > 1 order by count(*) desc limit 10
            """, organizationId);
        String uatStatus = count("""
            select count(*) from go_live_checklist_item
             where organization_id = ? and category = 'UAT' and status <> 'PASS'
            """, organizationId) == 0 ? "NO_OPEN_RECORDED_ITEMS" : "OPEN";
        return new PilotDashboardResponse(Instant.now(), config, total, open, blockers,
            meanHours == null ? 0 : meanHours, regressionCases, rate("manual_review"),
            rate("expert_correction"), satisfaction, uatStatus, distribution, trend, recurring);
    }

    private RootCauseSuggestionResponse suggestion(RootCauseAnalysisResult result) {
        List<Map<String, Object>> factors = result.contributingFactors().stream()
            .map(factor -> Map.<String, Object>of(
                "concept", factor.concept(),
                "effect", factor.effect(),
                "evidence", factor.evidence()))
            .toList();
        return new RootCauseSuggestionResponse(result.primaryCauseConcept(),
            result.confidence(), factors, result.recommendedInvestigationAreas(),
            result.explanation());
    }

    private Map<String, Object> qualityDebt(UUID id) {
        return one("""
            select debt.id, debt.feedback_case_id, type.concept_code as "debtType",
                   severity.concept_code as severity,
                   acceptance.concept_code as "acceptanceStatus",
                   cause.concept_code as "rootCause", debt.business_impact,
                   debt.technical_impact, debt.workaround, debt.target_release,
                   debt.owner_user_id, debt.accepted_by, debt.accepted_at,
                   debt.compensating_controls_json, debt.created_at, debt.updated_at,
                   debt.version
              from quality_debt_item debt
              join sprint9_concept type on type.id = debt.debt_type_concept_id
              join sprint9_concept severity on severity.id = debt.severity_concept_id
              join sprint9_concept acceptance on acceptance.id = debt.acceptance_status_concept_id
              join feedback_case feedback on feedback.id = debt.feedback_case_id
              left join error_triage_record triage on triage.feedback_case_id = feedback.id
              left join sprint9_concept cause on cause.id = triage.root_cause_concept_id
             where debt.id = ?
            """, id);
    }

    private Map<String, Object> disagreement(UUID id) {
        return one("""
            select d.id, d.entity_type, d.entity_id, d.reviewer_a_id, d.reviewer_b_id,
                   d.decision_a_json, d.decision_b_json,
                   disagreement.concept_code as disagreement,
                   status.concept_code as status, d.adjudicator_user_id,
                   d.final_decision_json, d.resolved_at, d.created_at
              from review_disagreement d
              join sprint9_concept disagreement on disagreement.id = d.disagreement_concept_id
              join sprint9_concept status on status.id = d.status_concept_id
             where d.id = ?
            """, id);
    }

    private ErrorAnalysisPolicyVersion defaultRootCausePolicy() {
        return new ErrorAnalysisPolicyVersion("ROOT_CAUSE_EXPLAINABLE_DEFAULT", 1,
            Map.of("parentClauseMissing", 1.0, "lowOcrQuality", 0.8,
                "ungrounded", 1.0, "tenantIsolationRisk", 1.0), 0.50);
    }

    private void requireOfflinePass(UUID candidateId) {
        Long passed = jdbc.queryForObject("""
            select count(*)
              from experiment_definition e
              join sprint9_concept type on type.id = e.experiment_type_concept_id
              join sprint9_concept status on status.id = e.status_concept_id
             where e.improvement_candidate_id = ?
               and type.concept_code in (
                   'OFFLINE_EVALUATION','PARSER_BENCHMARK','MODEL_COMPARISON',
                   'PROMPT_COMPARISON','POLICY_COMPARISON','ROUTING_COMPARISON',
                   'PERFORMANCE_BENCHMARK','SECURITY_REGRESSION')
               and status.concept_code = 'PASS'
            """, Long.class, candidateId);
        if (passed == null || passed == 0) {
            throw new IllegalStateException("A passed offline evaluation is required");
        }
    }

    private UUID persistInputSnapshot(
        UUID organizationId,
        SensitiveDataSanitizer.SanitizedPayload sanitized
    ) {
        UUID id = UUID.randomUUID();
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("sanitizerVersion", "1");
        manifest.set("removedPaths", mapper.valueToTree(sanitized.removedPaths()));
        int inserted = jdbc.update("""
            insert into sanitized_input_snapshot (
                id, organization_id, snapshot_json, content_hash,
                sanitization_manifest_json, created_at
            ) values (?, ?, ?::jsonb, ?, ?::jsonb, now())
            on conflict (organization_id, content_hash) do nothing
            """, id, organizationId, json(sanitized.value()), sanitized.contentHash(),
            json(manifest));
        return inserted == 1 ? id : jdbc.queryForObject("""
            select id from sanitized_input_snapshot
             where organization_id = ? and content_hash = ?
            """, UUID.class, organizationId, sanitized.contentHash());
    }

    private JsonNode uiConfiguration(String code) {
        UUID organizationId = tenant().tenantId();
        String value = jdbc.query("""
            select configuration_json::text from ui_configuration
             where configuration_code = ? and active = true
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc limit 1
            """, result -> result.next() ? result.getString(1) : "{}",
            code, organizationId);
        return tree(value);
    }

    private void validateTelemetry(JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            throw new IllegalArgumentException("Pilot telemetry metadata must be an object");
        }
        metadata.fieldNames().forEachRemaining(key -> {
            if (!TELEMETRY_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                    "Pilot telemetry metadata key is not allowlisted: " + key);
            }
        });
        if (metadata.toString().length() > 32_768) {
            throw new IllegalArgumentException("Pilot telemetry metadata exceeds 32 KiB");
        }
    }

    private void requireProject(UUID projectId) {
        Long count = jdbc.queryForObject("""
            select count(*) from tender_project
             where id = ? and organization_id = ?
            """, Long.class, projectId, tenant().tenantId());
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Tenant-scoped project not found");
        }
    }

    private void requireOwned(String table, UUID id) {
        if (!Set.of(
            "configuration_snapshot", "pilot_session", "feedback_case",
            "error_triage_record", "regression_suite", "experiment_run").contains(table)) {
            throw new IllegalArgumentException("Unsupported ownership target");
        }
        Long count = jdbc.queryForObject(
            "select count(*) from " + table + " where id = ? and organization_id = ?",
            Long.class, id, tenant().tenantId());
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Tenant-scoped " + table + " not found");
        }
    }

    private UUID concept(String catalogCode, String conceptCode) {
        if (conceptCode == null || conceptCode.isBlank()) {
            throw new IllegalArgumentException(catalogCode + " concept is required");
        }
        UUID organizationId = tenant().tenantId();
        UUID value = jdbc.query("""
            select c.id
              from sprint9_concept c
              join sprint9_concept_catalog catalog on catalog.id = c.catalog_id
             where catalog.catalog_code = ? and c.concept_code = ?
               and c.active = true and catalog.active = true
               and (c.organization_id = ? or c.organization_id is null)
             order by (c.organization_id is not null) desc limit 1
            """, result -> result.next() ? result.getObject(1, UUID.class) : null,
            catalogCode, conceptCode.toUpperCase(Locale.ROOT), organizationId);
        if (value == null) {
            throw new IllegalArgumentException(
                "Active " + catalogCode + " concept not found: " + conceptCode);
        }
        return value;
    }

    private Map<String, Object> one(String sql, Object... arguments) {
        try {
            return new LinkedHashMap<>(jdbc.queryForMap(sql, arguments));
        } catch (EmptyResultDataAccessException missing) {
            throw new IllegalArgumentException("Tenant-scoped resource not found");
        }
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private double rate(String eventType) {
        UUID organizationId = tenant().tenantId();
        Long all = jdbc.queryForObject("""
            select count(*) from pilot_event where organization_id = ?
            """, Long.class, organizationId);
        if (all == null || all == 0) {
            return 0;
        }
        Long matching = jdbc.queryForObject("""
            select count(*) from pilot_event e
              join sprint9_concept type on type.id = e.event_type_concept_id
             where e.organization_id = ?
               and lower(type.concept_code) = ?
            """, Long.class, organizationId, eventType);
        return matching == null ? 0
            : Math.round((matching.doubleValue() / all.doubleValue()) * 10_000.0) / 10_000.0;
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Stored JSON is invalid", invalid);
        }
    }

    private JsonNode arrayOrEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return mapper.createArrayNode();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("Snapshot collection fields must be arrays");
        }
        return value;
    }

    private JsonNode objectOrEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return mapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object");
        }
        return value;
    }

    private String safeText(String value) {
        if (value == null) {
            return null;
        }
        JsonNode sanitized = sanitizer.sanitize(mapper.getNodeFactory().textNode(value)).value();
        String result = sanitized.asText();
        return result.length() <= 8_000 ? result : result.substring(0, 8_000);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("JSON payload cannot be serialized", invalid);
        }
    }

    private String code(String prefix, UUID id) {
        return prefix + "-" + id.toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private TenantPrincipal tenant() {
        return currentTenant.require();
    }

    private void publish(
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Object payload
    ) {
        outbox.publish(tenant().tenantId(), aggregateType, aggregateId, eventType,
            eventType, payload, RequestContext.current().correlationId());
    }

    private void requireUpdated(int updated, String resource) {
        if (updated != 1) {
            throw new IllegalStateException(resource + " is not in the required state");
        }
    }

    private void requireState(String actual, Set<String> expected, String resource) {
        if (!expected.contains(actual)) {
            throw new IllegalStateException(resource + " state " + actual
                + " is not one of " + expected);
        }
    }

    private String value(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }

    private UUID uuid(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            throw new IllegalStateException("Required identifier is absent: " + key);
        }
        return UUID.fromString(String.valueOf(value));
    }

    private Map<String, Object> pilotSessionRow(ResultSet result, int row) throws SQLException {
        Map<String, Object> value = new HashMap<>();
        value.put("id", result.getObject("id", UUID.class));
        value.put("startedAt", instant(result, "started_at"));
        return value;
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
