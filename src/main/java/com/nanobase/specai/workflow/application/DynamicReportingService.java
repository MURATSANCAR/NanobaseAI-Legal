package com.nanobase.specai.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.application.ObjectStorage;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.workflow.api.ReportingContracts.CreateReportDefinitionRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.CreateReportVersionRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.DownloadUrlResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.GenerateReportRequest;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportArtifactResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportDefinitionResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportJobResponse;
import com.nanobase.specai.workflow.api.ReportingContracts.ReportSectionDraft;
import com.nanobase.specai.workflow.application.ReportFormatRenderer.RenderedReport;
import com.nanobase.specai.workflow.application.ReportSectionDataProvider.ReportSectionContext;
import com.nanobase.specai.workflow.application.WorkflowModels.WorkflowConditionContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicReportingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final List<ReportSectionDataProvider> sectionProviders;
    private final List<ReportFormatRenderer> renderers;
    private final WorkflowConditionEngine conditions;
    private final ObjectStorage storage;
    private final AuditService audit;
    private final OutboxService outbox;

    public DynamicReportingService(JdbcTemplate jdbc, ObjectMapper mapper,
                                   CurrentTenant currentTenant,
                                   List<ReportSectionDataProvider> sectionProviders,
                                   List<ReportFormatRenderer> renderers,
                                   WorkflowConditionEngine conditions,
                                   ObjectStorage storage, AuditService audit,
                                   OutboxService outbox) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.sectionProviders = List.copyOf(sectionProviders);
        this.renderers = List.copyOf(renderers);
        this.conditions = conditions;
        this.storage = storage;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public ReportDefinitionResponse createDefinition(CreateReportDefinitionRequest request) {
        TenantPrincipal principal = currentTenant.require();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into report_definition (
                id, organization_id, report_code, name, description, scope,
                subject_entity_type_concept_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, now(), now())
            """, id, principal.tenantId(), request.reportCode(), request.name(),
            request.description(), request.scope(), request.subjectEntityTypeConceptId());
        ReportDefinitionResponse created = definition(id);
        audit.record("report.definition.created.v1", "ReportDefinition", id, null, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<ReportDefinitionResponse> definitions() {
        return jdbc.query("""
            select id, report_code, name, description, scope,
                   subject_entity_type_concept_id, active_version_id,
                   created_at, updated_at
              from report_definition order by updated_at desc
            """, (result, row) -> definition(result, row));
    }

    @Transactional
    public Map<String, Object> createVersion(UUID definitionId,
                                             CreateReportVersionRequest request) {
        TenantPrincipal principal = currentTenant.require();
        definition(definitionId);
        int number = jdbc.queryForObject("""
            select coalesce(max(version_number), 0) + 1
              from report_definition_version where report_definition_id = ?
            """, Integer.class, definitionId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into report_definition_version (
                id, organization_id, report_definition_id, version_number,
                section_configuration_json, data_policy_version_id,
                template_version_id, status, created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?, 'DRAFT', now())
            """, id, principal.tenantId(), definitionId, number,
            json(orObject(request.sectionConfiguration())), request.dataPolicyVersionId(),
            request.templateVersionId());
        for (ReportSectionDraft section : request.sections()) {
            requireConcept(section.sectionTypeConceptId(), "REPORT_SECTION_TYPE");
            jdbc.update("""
                insert into report_section_definition (
                    id, organization_id, report_definition_version_id, section_code,
                    section_type_concept_id, title_template, data_query_configuration_json,
                    render_configuration_json, visibility_condition_json, sort_order, created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, now())
                """, UUID.randomUUID(), principal.tenantId(), id, section.sectionCode(),
                section.sectionTypeConceptId(), section.titleTemplate(),
                json(orObject(section.dataQueryConfiguration())),
                json(orObject(section.renderConfiguration())),
                json(orObject(section.visibilityCondition())), section.sortOrder());
        }
        Map<String, Object> created = version(id);
        audit.record("report.definition.version.created.v1", "ReportDefinitionVersion",
            id, null, created);
        return created;
    }

    @Transactional
    public Map<String, Object> activateVersion(UUID versionId) {
        TenantPrincipal principal = currentTenant.require();
        VersionRow version = versionRow(versionId);
        jdbc.update("""
            update report_definition_version set status = 'RETIRED'
             where report_definition_id = ? and status = 'ACTIVE'
            """, version.definitionId());
        jdbc.update("""
            update report_definition_version
               set status = 'ACTIVE', approved_by = ?, approved_at = now()
             where id = ? and status in ('DRAFT', 'TESTING')
            """, principal.subject(), versionId);
        jdbc.update("""
            update report_definition set active_version_id = ?, updated_at = now()
             where id = ?
            """, versionId, version.definitionId());
        Map<String, Object> activated = version(versionId);
        audit.record("report.definition.version.activated.v1", "ReportDefinitionVersion",
            versionId, null, activated);
        return activated;
    }

    @Transactional(readOnly = true)
    public ObjectNode preview(UUID versionId, UUID projectId) {
        Snapshot snapshot = buildSnapshot(projectId, versionId, null, false);
        return compose(versionId, projectId, snapshot.id(), snapshot.payload());
    }

    @Transactional
    public ReportJobResponse generate(UUID projectId, GenerateReportRequest request) {
        TenantPrincipal principal = currentTenant.require();
        VersionRow version = versionRow(request.reportDefinitionVersionId());
        if (!"ACTIVE".equals(version.status())) {
            throw new IllegalStateException("Only an active report definition can generate reports");
        }
        Snapshot snapshot = buildSnapshot(projectId, request.reportDefinitionVersionId(),
            request.staleOverrideApprovalId(), true);
        UUID running = concept("REPORT_JOB_RUNNING", "REPORT_JOB_STATUS");
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
            insert into report_generation_job (
                id, organization_id, project_id, report_definition_version_id,
                template_version_id, data_snapshot_id, status_concept_id, progress,
                requested_by, started_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, 10, ?, now(), now(), now())
            """, jobId, principal.tenantId(), projectId,
            request.reportDefinitionVersionId(), version.templateVersionId(),
            snapshot.id(), running, principal.subject());
        outbox.publish(principal.tenantId(), "ReportGenerationJob", jobId,
            "report.generation.requested.v1", "report.generation.requested.v1",
            Map.of("reportGenerationJobId", jobId, "projectId", projectId), null);
        ObjectNode report = compose(request.reportDefinitionVersionId(), projectId,
            snapshot.id(), snapshot.payload());
        String reportName = jdbc.queryForObject("""
            select d.name from report_definition d
              join report_definition_version v on v.report_definition_id = d.id
             where v.id = ?
            """, String.class, request.reportDefinitionVersionId());
        int artifactIndex = 0;
        for (UUID formatId : request.formatConceptIds()) {
            String formatCode = conceptCode(formatId, "REPORT_FORMAT");
            ReportFormatRenderer renderer = renderers.stream()
                .filter(candidate -> candidate.supports(formatCode)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No report renderer for format " + formatCode));
            RenderedReport rendered = renderer.render(reportName, report);
            byte[] bytes = rendered.content();
            String hash = sha256(bytes);
            String fileName = safeFileName(reportName) + "-v1." + rendered.extension();
            String key = principal.tenantId() + "/reports/" + projectId + "/" + jobId
                + "/" + formatCode.toLowerCase() + "/" + fileName;
            storage.put(key, new ByteArrayInputStream(bytes), bytes.length,
                rendered.mimeType());
            jdbc.update("""
                insert into report_artifact (
                    id, organization_id, report_generation_job_id, format_concept_id,
                    object_storage_key, file_name, mime_type, file_size, sha256,
                    version_number, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now())
                """, UUID.randomUUID(), principal.tenantId(), jobId, formatId, key,
                fileName, rendered.mimeType(), bytes.length, hash);
            artifactIndex++;
            jdbc.update("""
                update report_generation_job set progress = ?, updated_at = now()
                 where id = ?
                """, 10 + (artifactIndex * 80 / request.formatConceptIds().size()), jobId);
        }
        UUID completed = concept("REPORT_JOB_COMPLETED", "REPORT_JOB_STATUS");
        jdbc.update("""
            update report_generation_job set status_concept_id = ?, progress = 100,
                   completed_at = now(), updated_at = now(), version = version + 1
             where id = ?
            """, completed, jobId);
        ReportJobResponse result = job(jobId);
        audit.record("report.generation.completed.v1", "ReportGenerationJob", jobId,
            null, result);
        outbox.publish(principal.tenantId(), "ReportGenerationJob", jobId,
            "report.generation.completed.v1", "report.generation.completed.v1",
            Map.of("reportGenerationJobId", jobId, "projectId", projectId,
                "artifactCount", result.artifacts().size()), null);
        return result;
    }

    @Transactional(readOnly = true)
    public ReportJobResponse job(UUID jobId) {
        ReportJobResponse base = jdbc.queryForObject("""
            select j.id, j.project_id, j.report_definition_version_id,
                   j.template_version_id, j.data_snapshot_id, j.status_concept_id,
                   c.concept_code, j.progress, j.requested_by, j.started_at,
                   j.completed_at, j.error_code, j.error_message
              from report_generation_job j
              join ontology_concept c on c.id = j.status_concept_id
             where j.id = ?
            """, (result, row) -> new ReportJobResponse(
                result.getObject("id", UUID.class),
                result.getObject("project_id", UUID.class),
                result.getObject("report_definition_version_id", UUID.class),
                result.getObject("template_version_id", UUID.class),
                result.getObject("data_snapshot_id", UUID.class),
                result.getObject("status_concept_id", UUID.class),
                result.getString("concept_code"), result.getInt("progress"),
                result.getString("requested_by"), instant(result, "started_at"),
                instant(result, "completed_at"), result.getString("error_code"),
                result.getString("error_message"), List.of()), jobId);
        return new ReportJobResponse(base.id(), base.projectId(),
            base.reportDefinitionVersionId(), base.templateVersionId(),
            base.dataSnapshotId(), base.statusConceptId(), base.statusConceptCode(),
            base.progress(), base.requestedBy(), base.startedAt(), base.completedAt(),
            base.errorCode(), base.errorMessage(), artifacts(jobId));
    }

    @Transactional(readOnly = true)
    public List<ReportArtifactResponse> artifacts(UUID jobId) {
        return jdbc.query("""
            select a.id, a.format_concept_id, c.concept_code, a.file_name,
                   a.mime_type, a.file_size, a.sha256, a.version_number, a.created_at
              from report_artifact a
              join ontology_concept c on c.id = a.format_concept_id
             where a.report_generation_job_id = ? order by a.created_at
            """, (result, row) -> new ReportArtifactResponse(
                result.getObject("id", UUID.class),
                result.getObject("format_concept_id", UUID.class),
                result.getString("concept_code"), result.getString("file_name"),
                result.getString("mime_type"), result.getLong("file_size"),
                result.getString("sha256"), result.getInt("version_number"),
                result.getTimestamp("created_at").toInstant()), jobId);
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse download(UUID artifactId) {
        String key = jdbc.queryForObject("""
            select object_storage_key from report_artifact where id = ?
            """, String.class, artifactId);
        Duration validity = Duration.ofMinutes(5);
        return new DownloadUrlResponse(storage.signedDownloadUrl(key, validity).toString(),
            Instant.now().plus(validity));
    }

    private Snapshot buildSnapshot(UUID projectId, UUID definitionVersionId,
                                   UUID staleOverrideApprovalId, boolean persist) {
        VersionRow version = versionRow(definitionVersionId);
        JsonNode policy = jdbc.queryForObject("""
            select configuration_json::text from report_data_policy_version where id = ?
            """, (result, row) -> parse(result.getString(1)), version.dataPolicyVersionId());
        int staleCount = count("""
            select count(*) from analysis_staleness_record
             where entity_id in (
                 select id from requirement where project_id = ?
                 union select id from risk_record where project_id = ?
                 union select id from conflict_record where project_id = ?
             ) and resolved_at is null
            """, projectId, projectId, projectId);
        String behavior = policy.path("staleBehavior").asText("BLOCK");
        if (staleCount > 0 && ("BLOCK".equals(behavior) || "REANALYZE".equals(behavior))) {
            throw new IllegalStateException("Report data policy blocks stale analysis");
        }
        if (staleCount > 0 && "REQUIRE_APPROVAL".equals(behavior)
            && staleOverrideApprovalId == null) {
            throw new IllegalStateException("Stale report data requires approval");
        }
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("projectId", projectId.toString());
        payload.put("projectName", jdbc.queryForObject(
            "select name from tender_project where id = ?", String.class, projectId));
        ObjectNode counts = payload.putObject("counts");
        counts.put("requirements", count(
            "select count(*) from requirement where project_id = ?", projectId));
        counts.put("complianceEvaluations", count(
            "select count(*) from compliance_evaluation where project_id = ?", projectId));
        counts.put("risks", count(
            "select count(*) from risk_record where project_id = ?", projectId));
        counts.put("conflicts", count(
            "select count(*) from conflict_record where project_id = ?", projectId));
        counts.put("ambiguities", count(
            "select count(*) from ambiguity_finding where project_id = ?", projectId));
        counts.put("openTasks", count("""
            select count(*) from task_record
             where project_id = ? and completed_at is null and cancelled_at is null
            """, projectId));
        counts.put("pendingApprovals", count("""
            select count(*) from approval_request
             where project_id = ? and completed_at is null
            """, projectId));
        counts.put("clarifications", count(
            "select count(*) from clarification_request where project_id = ?", projectId));
        ObjectNode staleness = payload.putObject("staleness");
        staleness.put("count", staleCount);
        staleness.put("policyBehavior", behavior);
        staleness.put("overrideApprovalId",
            staleOverrideApprovalId == null ? "" : staleOverrideApprovalId.toString());
        long requirementVersion = maximum(
            "select coalesce(max(version), 0) from requirement where project_id = ?", projectId);
        long complianceVersion = maximum(
            "select coalesce(max(version), 0) from compliance_evaluation where project_id = ?",
            projectId);
        long riskVersion = maximum(
            "select coalesce(max(version), 0) from risk_record where project_id = ?", projectId);
        long conflictVersion = maximum(
            "select coalesce(max(version), 0) from conflict_record where project_id = ?",
            projectId);
        long taskVersion = maximum(
            "select coalesce(max(version), 0) from task_record where project_id = ?", projectId);
        long workflowVersion = maximum("""
            select coalesce(max(version), 0) from workflow_instance where project_id = ?
            """, projectId);
        ObjectNode cutoffs = payload.putObject("versionCutoffs");
        cutoffs.put("requirement", requirementVersion);
        cutoffs.put("compliance", complianceVersion);
        cutoffs.put("risk", riskVersion);
        cutoffs.put("conflict", conflictVersion);
        cutoffs.put("task", taskVersion);
        cutoffs.put("workflow", workflowVersion);
        UUID knowledgeSnapshot = jdbc.query("""
            select id from knowledge_snapshot where project_id = ?
             order by created_at desc limit 1
            """, result -> result.next() ? result.getObject(1, UUID.class) : null, projectId);
        String contentHash = sha256(json(payload).getBytes(StandardCharsets.UTF_8));
        UUID snapshotId = UUID.randomUUID();
        if (persist) {
            TenantPrincipal principal = currentTenant.require();
            UUID existing = jdbc.query("""
                select id from report_data_snapshot where content_hash = ? limit 1
                """, result -> result.next() ? result.getObject(1, UUID.class) : null,
                contentHash);
            if (existing != null) {
                snapshotId = existing;
            } else {
                jdbc.update("""
                    insert into report_data_snapshot (
                        id, organization_id, project_id, snapshot_type_concept_id,
                        requirement_version_cutoff, compliance_version_cutoff,
                        risk_version_cutoff, conflict_version_cutoff, task_version_cutoff,
                        workflow_version_cutoff, knowledge_snapshot_id,
                        staleness_summary_json, snapshot_payload_json, content_hash, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, now())
                    """, snapshotId, principal.tenantId(), projectId,
                    concept("REPORT_SNAPSHOT", "SNAPSHOT_TYPE"), requirementVersion,
                    complianceVersion, riskVersion, conflictVersion, taskVersion,
                    workflowVersion, knowledgeSnapshot, json(staleness), json(payload),
                    contentHash);
            }
        }
        return new Snapshot(snapshotId, payload);
    }

    private ObjectNode compose(UUID versionId, UUID projectId, UUID snapshotId,
                               ObjectNode payload) {
        Map<String, Object> verified = mapper.convertValue(payload,
            new TypeReference<Map<String, Object>>() { });
        ObjectNode report = JsonNodeFactory.instance.objectNode();
        report.put("snapshotId", snapshotId.toString());
        report.put("projectId", projectId.toString());
        ArrayNode sections = report.putArray("sections");
        List<SectionRow> rows = jdbc.query("""
            select s.section_code, c.concept_code, s.title_template,
                   s.data_query_configuration_json::text,
                   s.render_configuration_json::text,
                   s.visibility_condition_json::text
              from report_section_definition s
              join ontology_concept c on c.id = s.section_type_concept_id
             where s.report_definition_version_id = ? order by s.sort_order
            """, (result, row) -> new SectionRow(result.getString(1), result.getString(2),
                result.getString(3), parse(result.getString(4)),
                parse(result.getString(5)), parse(result.getString(6))), versionId);
        for (SectionRow row : rows) {
            if (!conditions.evaluate(new WorkflowConditionContext(verified),
                row.visibility()).matched()) {
                continue;
            }
            ReportSectionDataProvider provider = sectionProviders.stream()
                .filter(candidate -> candidate.supports(row.typeConceptCode())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No report section provider for " + row.typeConceptCode()));
            ObjectNode section = sections.addObject();
            section.put("code", row.code());
            section.put("type", row.typeConceptCode());
            section.put("title", row.title());
            section.set("renderConfiguration", row.render());
            section.set("data", provider.load(new ReportSectionContext(
                currentTenant.require().tenantId(), projectId, snapshotId,
                row.query(), verified)));
        }
        return report;
    }

    private Map<String, Object> version(UUID id) {
        VersionRow row = versionRow(id);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", row.id());
        response.put("reportDefinitionId", row.definitionId());
        response.put("versionNumber", row.versionNumber());
        response.put("dataPolicyVersionId", row.dataPolicyVersionId());
        response.put("templateVersionId", row.templateVersionId());
        response.put("status", row.status());
        return response;
    }

    private VersionRow versionRow(UUID id) {
        return jdbc.queryForObject("""
            select id, report_definition_id, version_number, data_policy_version_id,
                   template_version_id, status
              from report_definition_version where id = ?
            """, (result, row) -> new VersionRow(
                result.getObject("id", UUID.class),
                result.getObject("report_definition_id", UUID.class),
                result.getInt("version_number"),
                result.getObject("data_policy_version_id", UUID.class),
                result.getObject("template_version_id", UUID.class),
                result.getString("status")), id);
    }

    private ReportDefinitionResponse definition(UUID id) {
        return jdbc.queryForObject("""
            select id, report_code, name, description, scope,
                   subject_entity_type_concept_id, active_version_id,
                   created_at, updated_at
              from report_definition where id = ?
            """, this::definition, id);
    }

    private ReportDefinitionResponse definition(ResultSet result, int row)
        throws SQLException {
        return new ReportDefinitionResponse(result.getObject("id", UUID.class),
            result.getString("report_code"), result.getString("name"),
            result.getString("description"), result.getString("scope"),
            result.getObject("subject_entity_type_concept_id", UUID.class),
            result.getObject("active_version_id", UUID.class),
            result.getTimestamp("created_at").toInstant(),
            result.getTimestamp("updated_at").toInstant());
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long maximum(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private void requireConcept(UUID id, String type) {
        conceptCode(id, type);
    }

    private String conceptCode(UUID id, String type) {
        return jdbc.queryForObject("""
            select concept_code from ontology_concept
             where id = ? and concept_type = ? and active = true
            """, String.class, id, type);
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
            throw new IllegalArgumentException("Report data cannot be serialized", exception);
        }
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored report JSON is invalid", exception);
        }
    }

    private static String safeFileName(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "-")
            .replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "report" : safe.substring(0, Math.min(100, safe.length()));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record VersionRow(UUID id, UUID definitionId, int versionNumber,
                              UUID dataPolicyVersionId, UUID templateVersionId,
                              String status) {
    }

    private record Snapshot(UUID id, ObjectNode payload) {
    }

    private record SectionRow(String code, String typeConceptCode, String title,
                              JsonNode query, JsonNode render, JsonNode visibility) {
    }
}
