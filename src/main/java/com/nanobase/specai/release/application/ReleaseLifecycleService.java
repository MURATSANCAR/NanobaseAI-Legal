package com.nanobase.specai.release.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.pilot.application.SensitiveDataSanitizer;
import com.nanobase.specai.release.api.ReleaseContracts.ApprovalDecisionRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ApprovalRequest;
import com.nanobase.specai.release.api.ReleaseContracts.CreateReleaseRequest;
import com.nanobase.specai.release.api.ReleaseContracts.DeploymentResultRequest;
import com.nanobase.specai.release.api.ReleaseContracts.DryRunRequest;
import com.nanobase.specai.release.api.ReleaseContracts.DryRunResultRequest;
import com.nanobase.specai.release.api.ReleaseContracts.GateResultRequest;
import com.nanobase.specai.release.api.ReleaseContracts.GoLiveDecisionRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ReleaseManifestRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ReleaseArtifactRequest;
import com.nanobase.specai.release.api.ReleaseContracts.ReleasePackageResponse;
import com.nanobase.specai.release.api.ReleaseContracts.StabilizationRequest;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReleaseLifecycleService {
    private static final Set<String> RC_FIXABLE_SCOPE = Set.of(
        "BLOCKER_FIX", "SECURITY_FIX", "ROLLBACK_FIX");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final ReleaseGatePolicy gatePolicy;
    private final SensitiveDataSanitizer sanitizer;
    private final AuditService audit;
    private final OutboxService outbox;

    public ReleaseLifecycleService(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        CurrentTenant currentTenant,
        ReleaseGatePolicy gatePolicy,
        SensitiveDataSanitizer sanitizer,
        AuditService audit,
        OutboxService outbox
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.gatePolicy = gatePolicy;
        this.sanitizer = sanitizer;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public Map<String, Object> create(CreateReleaseRequest request) {
        TenantPrincipal principal = tenant();
        UUID id = UUID.randomUUID();
        String type = request.releaseTypeCode().toUpperCase(Locale.ROOT);
        boolean rc = "RELEASE_CANDIDATE".equals(type);
        jdbc.update("""
            insert into release_record (
                id, organization_id, release_code, semantic_version,
                release_type_concept_id, status_concept_id, source_commit,
                build_number, scope_locked_at, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """, id, principal.tenantId(), request.releaseCode(),
            request.semanticVersion(), concept("RELEASE_TYPE", type),
            concept("RELEASE_STATUS", rc ? "SCOPE_LOCKED" : "DRAFT"),
            request.sourceCommit(), request.buildNumber(),
            rc ? Timestamp.from(Instant.now()) : null);
        if (rc) {
            UUID inScope = concept("SCOPE_STATUS", "IN_SCOPE");
            jdbc.update("""
                insert into release_scope (
                    id, organization_id, release_id, feature_definition_id,
                    scope_status_concept_id, notes, created_at, updated_at
                )
                select gen_random_uuid(), ?, ?, feature.id, ?,
                       'Scope captured and locked at RC creation', now(), now()
                  from feature_definition feature
                """, principal.tenantId(), id, inScope);
        }
        Map<String, Object> created = get(id);
        audit.record("release.created.v1", "ReleaseRecord", id, null, created);
        publish("release.created.v1", "ReleaseRecord", id, created);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
            select r.id, r.release_code, r.semantic_version,
                   type.concept_code as "releaseType", status.concept_code as status,
                   r.source_commit, r.build_number, r.scope_locked_at,
                   r.created_at, r.approved_at, r.released_at
              from release_record r
              join sprint9_concept type on type.id = r.release_type_concept_id
              join sprint9_concept status on status.id = r.status_concept_id
             order by r.created_at desc limit 100
            """);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID releaseId) {
        Map<String, Object> release = one("""
            select r.id, r.release_code, r.semantic_version,
                   type.concept_code as "releaseType", status.concept_code as status,
                   r.source_commit, r.build_number, r.scope_locked_at,
                   r.created_at, r.approved_at, r.released_at
              from release_record r
              join sprint9_concept type on type.id = r.release_type_concept_id
              join sprint9_concept status on status.id = r.status_concept_id
             where r.id = ?
            """, releaseId);
        release.put("scope", jdbc.queryForList("""
            select scope.id, feature.feature_code, feature.name,
                   status.concept_code as "scopeStatus", scope.notes,
                   scope.created_at, scope.updated_at
              from release_scope scope
              join feature_definition feature on feature.id = scope.feature_definition_id
              join sprint9_concept status on status.id = scope.scope_status_concept_id
             where scope.release_id = ? order by feature.feature_code
            """, releaseId));
        return release;
    }

    @Transactional
    public Map<String, Object> recordGate(UUID releaseId, GateResultRequest request) {
        TenantPrincipal principal = tenant();
        Map<String, Object> release = get(releaseId);
        String status = request.status().toUpperCase(Locale.ROOT);
        int controls = request.compensatingControls() != null
            && request.compensatingControls().isArray()
            ? request.compensatingControls().size() : 0;
        gatePolicy.validateWaiver(status, request.waiverReason(), controls,
            principal.roles().contains("SYSTEM_ADMIN")
                || principal.roles().contains("TENANT_ADMIN"));
        JsonNode evidence = sanitizer.sanitize(request.evidenceReferences()).value();
        if ("PASS".equals(status) && (!evidence.isArray() || evidence.isEmpty())) {
            throw new IllegalArgumentException("A passing gate requires evidence references");
        }
        UUID gateId = jdbc.query("""
            select id from release_gate_definition where gate_code = ?
            """, result -> result.next() ? result.getObject(1, UUID.class) : null,
            request.gateCode().toUpperCase(Locale.ROOT));
        if (gateId == null) {
            throw new IllegalArgumentException("Unknown release gate: " + request.gateCode());
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into release_gate_result (
                id, organization_id, release_id, gate_definition_id, status_concept_id,
                evidence_references_json, summary, waiver_reason,
                compensating_controls_json, waived_by, evaluated_at,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, now(), now(), now())
            on conflict (release_id, gate_definition_id) do update
                set status_concept_id = excluded.status_concept_id,
                    evidence_references_json = excluded.evidence_references_json,
                    summary = excluded.summary,
                    waiver_reason = excluded.waiver_reason,
                    compensating_controls_json = excluded.compensating_controls_json,
                    waived_by = excluded.waived_by,
                    evaluated_at = now(), updated_at = now()
            """, id, principal.tenantId(), releaseId, gateId,
            concept("GATE_STATUS", status), json(evidence),
            safeText(request.summary()), safeText(request.waiverReason()),
            json(request.compensatingControls() == null
                ? mapper.createArrayNode()
                : sanitizer.sanitize(request.compensatingControls()).value()),
            "WAIVED".equals(status) ? principal.subject() : null);
        jdbc.update("""
            update release_record set status_concept_id = ?
             where id = ? and released_at is null
            """, concept("RELEASE_STATUS",
                "FAIL".equals(status) ? "GATES_FAILED" : "GATES_RUNNING"), releaseId);
        Map<String, Object> result = gate(releaseId, request.gateCode());
        audit.record("release.gate.evaluated.v1", "ReleaseRecord", releaseId,
            release, get(releaseId));
        publish("release.gate.evaluated.v1", "ReleaseRecord", releaseId, result);
        return result;
    }

    @Transactional
    public Map<String, Object> createManifest(
        UUID releaseId,
        ReleaseManifestRequest request
    ) {
        TenantPrincipal principal = tenant();
        Map<String, Object> release = get(releaseId);
        validateDigest(request.backendImageDigest(), "backend");
        validateDigest(request.frontendImageDigest(), "frontend");
        ObjectNode content = mapper.createObjectNode();
        content.put("backendImageDigest", request.backendImageDigest());
        content.put("frontendImageDigest", request.frontendImageDigest());
        content.set("workerImageDigests", sanitizer.sanitize(
            request.workerImageDigests()).value());
        content.set("modelArtifacts", sanitizer.sanitize(request.modelArtifacts()).value());
        content.set("promptVersions", sanitizer.sanitize(request.promptVersions()).value());
        content.set("policyVersions", sanitizer.sanitize(request.policyVersions()).value());
        content.set("ontologyVersions", sanitizer.sanitize(request.ontologyVersions()).value());
        content.set("workflowVersions", sanitizer.sanitize(request.workflowVersions()).value());
        content.set("databaseMigrationVersions", sanitizer.sanitize(
            request.databaseMigrationVersions()).value());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into release_configuration_manifest (
                id, organization_id, release_id, manifest_version,
                backend_image_digest, frontend_image_digest, worker_image_digests_json,
                model_artifacts_json, prompt_versions_json, policy_versions_json,
                ontology_versions_json, workflow_versions_json,
                database_migration_versions_json, content_hash, created_at
            ) values (?, ?, ?, 1, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb,
                      ?::jsonb, ?::jsonb, ?::jsonb, ?, now())
            """, id, principal.tenantId(), releaseId,
            request.backendImageDigest(), request.frontendImageDigest(),
            json(content.get("workerImageDigests")), json(content.get("modelArtifacts")),
            json(content.get("promptVersions")), json(content.get("policyVersions")),
            json(content.get("ontologyVersions")), json(content.get("workflowVersions")),
            json(content.get("databaseMigrationVersions")), sanitizer.sha256(content));
        Map<String, Object> manifest = manifest(releaseId);
        audit.record("release.manifest.created.v1", "ReleaseRecord", releaseId,
            release, manifest);
        return manifest;
    }

    @Transactional
    public Map<String, Object> addArtifact(
        UUID releaseId,
        ReleaseArtifactRequest request
    ) {
        TenantPrincipal principal = tenant();
        get(releaseId);
        if (!request.sha256().matches("^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException("Release artifact requires a sha256 checksum");
        }
        if (request.signatureReference().isBlank()) {
            throw new IllegalArgumentException("Release artifact signature is required");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into release_artifact (
                id, organization_id, release_id, artifact_type_concept_id,
                artifact_reference, sha256, signature_reference, sbom_reference,
                created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
            """, id, principal.tenantId(), releaseId,
            concept("ARTIFACT_TYPE", request.artifactTypeCode()),
            request.artifactReference(), request.sha256(),
            request.signatureReference(), request.sbomReference());
        Map<String, Object> artifact = one("""
            select artifact.id, artifact.release_id,
                   type.concept_code as "artifactType", artifact.artifact_reference,
                   artifact.sha256, artifact.signature_reference,
                   artifact.sbom_reference, artifact.created_at
              from release_artifact artifact
              join sprint9_concept type on type.id = artifact.artifact_type_concept_id
             where artifact.id = ?
            """, id);
        audit.record("release.artifact.added.v1", "ReleaseArtifact", id, null, artifact);
        return artifact;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> manifest(UUID releaseId) {
        get(releaseId);
        return one("""
            select id, release_id, manifest_version, backend_image_digest,
                   frontend_image_digest, worker_image_digests_json,
                   model_artifacts_json, prompt_versions_json, policy_versions_json,
                   ontology_versions_json, workflow_versions_json,
                   database_migration_versions_json, content_hash, created_at
              from release_configuration_manifest where release_id = ?
            """, releaseId);
    }

    @Transactional
    public Map<String, Object> requestApproval(
        UUID releaseId,
        ApprovalRequest request
    ) {
        TenantPrincipal principal = tenant();
        ReleasePackageResponse current = packageFor(releaseId, false);
        List<String> gateMissing = current.missingEvidence().stream()
            .filter(value -> !"HUMAN_APPROVAL".equals(value)).toList();
        if (!gateMissing.isEmpty()) {
            throw new IllegalStateException(
                "Release approval cannot start; missing evidence: " + gateMissing);
        }
        UUID policyId = activePolicy("RELEASE_APPROVAL_DEFAULT");
        JsonNode configured = tree(jdbc.queryForObject("""
            select configuration_json::text from sprint9_policy_version where id = ?
            """, String.class, policyId));
        ArrayNode required = configured != null && configured.path("steps").isArray()
            ? (ArrayNode) configured.path("steps") : mapper.createArrayNode();
        ObjectNode steps = mapper.createObjectNode();
        required.forEach(step -> {
            ObjectNode state = mapper.createObjectNode();
            state.put("status", "PENDING");
            steps.set(step.asText(), state);
        });
        if (request != null && request.approvalSteps() != null
            && request.approvalSteps().isObject()) {
            request.approvalSteps().fieldNames().forEachRemaining(step -> {
                if (!steps.has(step)) {
                    throw new IllegalArgumentException(
                        "Approval chain is policy-owned; unknown step: " + step);
                }
            });
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into release_approval_request (
                id, organization_id, release_id, approval_policy_version_id,
                status_concept_id, approval_steps_json, requested_at, created_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, now(), now())
            """, id, principal.tenantId(), releaseId, policyId,
            concept("APPROVAL_STATUS", "REQUESTED"), json(steps));
        jdbc.update("""
            update release_record set status_concept_id = ? where id = ?
            """, concept("RELEASE_STATUS", "AWAITING_APPROVAL"), releaseId);
        Map<String, Object> created = approval(id);
        audit.record("release.approval.requested.v1", "ReleaseApprovalRequest",
            id, null, created);
        publish("release.approval.requested.v1", "ReleaseApprovalRequest", id, created);
        return created;
    }

    @Transactional
    public Map<String, Object> decideApproval(
        UUID approvalId,
        ApprovalDecisionRequest request
    ) {
        TenantPrincipal principal = tenant();
        Map<String, Object> before = approval(approvalId);
        String decision = request.decision().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) {
            throw new IllegalArgumentException("Approval decision must be APPROVE or REJECT");
        }
        JsonNode stored = tree(String.valueOf(before.get("approval_steps_json")));
        if (!stored.isObject() || !stored.has(request.step())) {
            throw new IllegalArgumentException("Approval step is not in the policy chain");
        }
        ObjectNode steps = (ObjectNode) stored.deepCopy();
        JsonNode existing = steps.path(request.step());
        if (!"PENDING".equals(existing.path("status").asText())) {
            throw new IllegalStateException("Approval step already has a decision");
        }
        ObjectNode result = mapper.createObjectNode();
        result.put("status", decision.equals("APPROVE") ? "APPROVED" : "REJECTED");
        result.put("decidedBy", principal.subject());
        result.put("decidedAt", Instant.now().toString());
        result.put("comment", safeText(request.comment()));
        steps.set(request.step(), result);
        boolean rejected = anyStepHasStatus(steps, "REJECTED");
        boolean complete = allStepsHaveStatus(steps, "APPROVED");
        String approvalStatus = rejected ? "REJECTED" : complete ? "APPROVED" : "IN_REVIEW";
        jdbc.update("""
            update release_approval_request
               set approval_steps_json = ?::jsonb, status_concept_id = ?,
                   completed_at = case when ? then now() else null end
             where id = ?
            """, json(steps), concept("APPROVAL_STATUS", approvalStatus),
            rejected || complete, approvalId);
        UUID releaseId = uuid(before, "release_id");
        if (rejected || complete) {
            jdbc.update("""
                update release_record set status_concept_id = ?,
                       approved_at = case when ? then now() else approved_at end
                 where id = ?
                """, concept("RELEASE_STATUS", complete ? "APPROVED" : "REJECTED"),
                complete, releaseId);
        }
        Map<String, Object> after = approval(approvalId);
        audit.record("release.approval.decision.v1", "ReleaseApprovalRequest",
            approvalId, before, after);
        if (complete) {
            publish("release.approved.v1", "ReleaseRecord", releaseId, get(releaseId));
        }
        return after;
    }

    @Transactional
    public Map<String, Object> requestDryRun(UUID releaseId, DryRunRequest request) {
        TenantPrincipal principal = tenant();
        get(releaseId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into release_dry_run (
                id, organization_id, release_id, environment, steps_json,
                status_concept_id, evidence_references_json, requested_by,
                created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, now())
            """, id, principal.tenantId(), releaseId, request.environment(),
            json(sanitizer.sanitize(request.steps()).value()),
            concept("GATE_STATUS", "NOT_RUN"),
            json(sanitizer.sanitize(request.evidenceReferences()).value()),
            principal.subject());
        jdbc.update("""
            update release_record set status_concept_id = ? where id = ?
            """, concept("RELEASE_STATUS", "DRY_RUN_REQUESTED"), releaseId);
        Map<String, Object> result = dryRun(id);
        audit.record("release.dry-run.requested.v1", "ReleaseDryRun", id, null, result);
        return result;
    }

    @Transactional
    public Map<String, Object> completeDryRun(
        UUID dryRunId,
        DryRunResultRequest request
    ) {
        Map<String, Object> before = dryRun(dryRunId);
        JsonNode evidence = sanitizer.sanitize(request.evidenceReferences()).value();
        if (request.passed() && (!evidence.isArray() || evidence.isEmpty())) {
            throw new IllegalArgumentException("A passed dry run requires evidence");
        }
        jdbc.update("""
            update release_dry_run
               set status_concept_id = ?, steps_json = ?::jsonb,
                   evidence_references_json = ?::jsonb,
                   started_at = coalesce(started_at, created_at), completed_at = now()
             where id = ? and completed_at is null
            """, concept("GATE_STATUS", request.passed() ? "PASS" : "FAIL"),
            json(sanitizer.sanitize(request.steps()).value()), json(evidence), dryRunId);
        Map<String, Object> after = dryRun(dryRunId);
        audit.record("release.dry-run.completed.v1", "ReleaseDryRun",
            dryRunId, before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> decideGoLive(
        UUID releaseId,
        GoLiveDecisionRequest request
    ) {
        TenantPrincipal principal = tenant();
        ReleasePackageResponse releasePackage = packageFor(releaseId, true);
        String decision = request.decision().toUpperCase(Locale.ROOT);
        if (Set.of("GO", "GO_WITH_CONDITIONS").contains(decision)
            && !releasePackage.eligibleForGoLive()) {
            throw new IllegalStateException(
                "GO decision is fail-closed; missing evidence: "
                    + releasePackage.missingEvidence());
        }
        if ("GO_WITH_CONDITIONS".equals(decision)
            && (request.conditions() == null || request.conditions().isEmpty())) {
            throw new IllegalArgumentException("GO_WITH_CONDITIONS requires conditions");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into go_live_decision (
                id, organization_id, release_id, decision_concept_id,
                conditions_json, open_risks_json, rollback_plan_reference,
                decided_by, decided_at, created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, now(), now())
            """, id, principal.tenantId(), releaseId,
            concept("GO_LIVE_DECISION", decision),
            json(sanitizer.sanitize(request.conditions()).value()),
            json(sanitizer.sanitize(request.openRisks()).value()),
            request.rollbackPlanReference(), principal.subject());
        Map<String, Object> result = one("""
            select d.id, d.release_id, decision.concept_code as decision,
                   d.conditions_json, d.open_risks_json, d.rollback_plan_reference,
                   d.decided_by, d.decided_at, d.created_at
              from go_live_decision d
              join sprint9_concept decision on decision.id = d.decision_concept_id
             where d.id = ?
            """, id);
        audit.record("go-live.decision.recorded.v1", "GoLiveDecision", id, null, result);
        publish("go-live.decision.recorded.v1", "GoLiveDecision", id, result);
        return result;
    }

    @Transactional
    public Map<String, Object> requestDeploy(UUID releaseId) {
        Map<String, Object> before = get(releaseId);
        ReleasePackageResponse releasePackage = packageFor(releaseId, true);
        if (!releasePackage.eligibleForGoLive()) {
            throw new IllegalStateException(
                "Release is not eligible for deployment: "
                    + releasePackage.missingEvidence());
        }
        Long go = jdbc.queryForObject("""
            select count(*) from go_live_decision decision
              join sprint9_concept concept on concept.id = decision.decision_concept_id
             where decision.release_id = ?
               and concept.concept_code in ('GO','GO_WITH_CONDITIONS')
               and not exists (
                   select 1 from go_live_decision newer
                    where newer.release_id = decision.release_id
                      and newer.decided_at > decision.decided_at
               )
            """, Long.class, releaseId);
        if (go == null || go == 0) {
            throw new IllegalStateException("Latest human go-live decision is not GO");
        }
        jdbc.update("""
            update release_record set status_concept_id = ? where id = ?
            """, concept("RELEASE_STATUS", "DEPLOYMENT_REQUESTED"), releaseId);
        Map<String, Object> after = get(releaseId);
        audit.record("release.deployment.requested.v1", "ReleaseRecord",
            releaseId, before, after);
        publish("release.deployment.requested.v1", "ReleaseRecord", releaseId, after);
        return after;
    }

    @Transactional
    public Map<String, Object> recordDeploymentResult(
        UUID releaseId,
        DeploymentResultRequest request
    ) {
        Map<String, Object> before = get(releaseId);
        requireState(value(before, "status"), Set.of("DEPLOYMENT_REQUESTED"), "release");
        JsonNode evidence = sanitizer.sanitize(request.evidence()).value();
        if (request.succeeded() && evidence.isEmpty()) {
            throw new IllegalArgumentException("Successful deployment requires runtime evidence");
        }
        jdbc.update("""
            update release_record set status_concept_id = ?,
                   released_at = case when ? then now() else released_at end
             where id = ?
            """, concept("RELEASE_STATUS",
                request.succeeded() ? "DEPLOYED" : "GATES_FAILED"),
            request.succeeded(), releaseId);
        Map<String, Object> after = get(releaseId);
        audit.record("release.deployment.result.v1", "ReleaseRecord",
            releaseId, Map.of("release", before), Map.of(
                "release", after, "evidence", evidence,
                "summary", safeText(request.summary())));
        if (request.succeeded()) {
            publish("release.deployed.v1", "ReleaseRecord", releaseId, after);
        }
        return after;
    }

    @Transactional
    public Map<String, Object> requestRollback(UUID releaseId) {
        Map<String, Object> before = get(releaseId);
        requireState(value(before, "status"), Set.of("DEPLOYED"), "release");
        jdbc.update("""
            update release_record set status_concept_id = ? where id = ?
            """, concept("RELEASE_STATUS", "ROLLBACK_REQUESTED"), releaseId);
        Map<String, Object> after = get(releaseId);
        audit.record("release.rollback.requested.v1", "ReleaseRecord",
            releaseId, before, after);
        publish("release.rollback.requested.v1", "ReleaseRecord", releaseId, after);
        return after;
    }

    @Transactional
    public Map<String, Object> recordRollbackResult(
        UUID releaseId,
        DeploymentResultRequest request
    ) {
        Map<String, Object> before = get(releaseId);
        requireState(value(before, "status"), Set.of("ROLLBACK_REQUESTED"), "release");
        if (!request.succeeded()) {
            throw new IllegalStateException(
                "Failed rollback remains a release blocker and cannot be marked rolled back");
        }
        JsonNode evidence = sanitizer.sanitize(request.evidence()).value();
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("Successful rollback requires runtime evidence");
        }
        jdbc.update("""
            update release_record set status_concept_id = ? where id = ?
            """, concept("RELEASE_STATUS", "ROLLED_BACK"), releaseId);
        Map<String, Object> after = get(releaseId);
        audit.record("release.rollback.result.v1", "ReleaseRecord",
            releaseId, before, Map.of("release", after, "evidence", evidence));
        publish("release.rolled-back.v1", "ReleaseRecord", releaseId, after);
        return after;
    }

    @Transactional
    public Map<String, Object> createStabilization(
        UUID releaseId,
        StabilizationRequest request
    ) {
        TenantPrincipal principal = tenant();
        Map<String, Object> release = get(releaseId);
        requireState(value(release, "status"), Set.of("DEPLOYED"), "release");
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("Stabilization end must be after start");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into stabilization_window (
                id, organization_id, release_id, start_at, end_at,
                monitoring_policy_version_id, support_policy_version_id,
                status_concept_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """, id, principal.tenantId(), releaseId, Timestamp.from(request.startAt()),
            Timestamp.from(request.endAt()), activePolicy("STABILIZATION_MONITORING_DEFAULT"),
            activePolicy("HYPERCARE_SUPPORT_DEFAULT"),
            concept("STABILIZATION_STATUS",
                request.startAt().isAfter(Instant.now()) ? "PLANNED" : "ACTIVE"));
        Map<String, Object> result = stabilization(releaseId);
        audit.record("stabilization.started.v1", "StabilizationWindow",
            id, null, result);
        publish("stabilization.started.v1", "StabilizationWindow", id, result);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stabilization(UUID releaseId) {
        get(releaseId);
        List<Map<String, Object>> windows = jdbc.queryForList("""
            select w.id, w.release_id, w.start_at, w.end_at,
                   w.monitoring_policy_version_id, w.support_policy_version_id,
                   status.concept_code as status, w.created_at, w.updated_at
              from stabilization_window w
              join sprint9_concept status on status.id = w.status_concept_id
             where w.release_id = ? order by w.created_at desc
            """, releaseId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("releaseId", releaseId);
        result.put("windows", windows);
        result.put("monitoringMetrics", List.of(
            "error_rate", "queue_depth", "model_latency", "parser_failure",
            "manual_review_rate", "user_feedback", "security_alert", "backup", "storage"));
        result.put("hypercareManagedBy", "DYNAMIC_WORKFLOW_POLICY");
        return result;
    }

    @Transactional(readOnly = true)
    public ReleasePackageResponse goLivePackage(UUID releaseId) {
        return packageFor(releaseId, true);
    }

    private ReleasePackageResponse packageFor(UUID releaseId, boolean requireDryRun) {
        Map<String, Object> release = get(releaseId);
        List<Map<String, Object>> gates = gates(releaseId);
        List<Map<String, Object>> artifacts = jdbc.queryForList("""
            select a.id, type.concept_code as "artifactType", a.artifact_reference,
                   a.sha256, a.signature_reference, a.sbom_reference, a.created_at
              from release_artifact a
              join sprint9_concept type on type.id = a.artifact_type_concept_id
             where a.release_id = ? order by a.created_at
            """, releaseId);
        List<Map<String, Object>> approvals = jdbc.queryForList("""
            select a.id, status.concept_code as status, a.approval_steps_json,
                   a.requested_at, a.completed_at, a.created_at
              from release_approval_request a
              join sprint9_concept status on status.id = a.status_concept_id
             where a.release_id = ? order by a.created_at
            """, releaseId);
        List<Map<String, Object>> blockers = jdbc.queryForList("""
            select f.id, f.feedback_code, f.title, severity.concept_code as severity,
                   triage.priority_score, triage.analysis_summary
              from error_triage_record triage
              join feedback_case f on f.id = triage.feedback_case_id
              join sprint9_concept severity on severity.id = f.severity_concept_id
              join sprint9_concept status on status.id = f.status_concept_id
             where triage.release_blocker = true
               and status.concept_code not in ('RESOLVED','CLOSED')
             order by triage.priority_score desc
            """);
        List<Map<String, Object>> debt = jdbc.queryForList("""
            select debt.id, f.feedback_code, severity.concept_code as severity,
                   acceptance.concept_code as "acceptanceStatus", debt.business_impact,
                   debt.technical_impact, debt.workaround, debt.target_release,
                   debt.accepted_by, debt.accepted_at
              from quality_debt_item debt
              join feedback_case f on f.id = debt.feedback_case_id
              join sprint9_concept severity on severity.id = debt.severity_concept_id
              join sprint9_concept acceptance on acceptance.id = debt.acceptance_status_concept_id
             order by debt.created_at
            """);
        List<Map<String, Object>> dryRuns = jdbc.queryForList("""
            select run.id, run.environment, status.concept_code as status,
                   run.steps_json, run.evidence_references_json, run.requested_by,
                   run.started_at, run.completed_at, run.created_at
              from release_dry_run run
              join sprint9_concept status on status.id = run.status_concept_id
             where run.release_id = ? order by run.created_at desc
            """, releaseId);
        List<Map<String, Object>> decisions = jdbc.queryForList("""
            select decision.id, concept.concept_code as decision,
                   decision.conditions_json, decision.open_risks_json,
                   decision.rollback_plan_reference, decision.decided_by,
                   decision.decided_at
              from go_live_decision decision
              join sprint9_concept concept on concept.id = decision.decision_concept_id
             where decision.release_id = ? order by decision.decided_at desc
            """, releaseId);
        Map<String, String> statuses = gates.stream()
            .filter(value -> !value(value, "status").isBlank())
            .collect(Collectors.toMap(
            value -> value(value, "gateCode", "gatecode"),
            value -> value(value, "status"),
            (first, second) -> second));
        List<String> required = jdbc.queryForList("""
            select gate_code from release_gate_definition
             where required_by_default = true order by gate_code
            """, String.class);
        boolean manifestPresent = count("""
            select count(*) from release_configuration_manifest where release_id = ?
            """, releaseId) == 1;
        boolean approvalsComplete = approvals.stream()
            .anyMatch(value -> "APPROVED".equals(value(value, "status")));
        ReleaseGatePolicy.GateEvaluation evaluation = gatePolicy.evaluate(
            required, statuses, manifestPresent, blockers.size(), approvalsComplete);
        List<String> missing = new ArrayList<>(evaluation.missingEvidence());
        if (artifacts.stream().noneMatch(value ->
            "BACKEND_IMAGE".equals(value(value, "artifactType", "artifacttype")))) {
            missing.add("SIGNED_BACKEND_ARTIFACT");
        }
        if (artifacts.stream().noneMatch(value ->
            "FRONTEND_IMAGE".equals(value(value, "artifactType", "artifacttype")))) {
            missing.add("SIGNED_FRONTEND_ARTIFACT");
        }
        if (artifacts.stream().noneMatch(value ->
            "SBOM".equals(value(value, "artifactType", "artifacttype"))
                || value.get("sbom_reference") != null)) {
            missing.add("SBOM_ARTIFACT");
        }
        if (requireDryRun && dryRuns.stream()
            .noneMatch(value -> "PASS".equals(value(value, "status")))) {
            missing.add("RELEASE_DRY_RUN");
        }
        Map<String, Object> manifest = manifestPresent
            ? manifest(releaseId) : Map.of();
        return new ReleasePackageResponse(release, manifest, gates, artifacts,
            approvals, blockers, debt, dryRuns, decisions, missing.isEmpty(),
            List.copyOf(missing));
    }

    private List<Map<String, Object>> gates(UUID releaseId) {
        return jdbc.queryForList("""
            select definition.gate_code as "gateCode", definition.name,
                   definition.required_by_default,
                   status.concept_code as status, result.evidence_references_json,
                   result.summary, result.waiver_reason,
                   result.compensating_controls_json, result.waived_by,
                   result.evaluated_at, result.updated_at
              from release_gate_definition definition
              left join release_gate_result result
                on result.gate_definition_id = definition.id and result.release_id = ?
              left join sprint9_concept status on status.id = result.status_concept_id
             order by definition.gate_code
            """, releaseId);
    }

    private Map<String, Object> gate(UUID releaseId, String gateCode) {
        return one("""
            select result.id, definition.gate_code as "gateCode", definition.name,
                   status.concept_code as status, result.evidence_references_json,
                   result.summary, result.waiver_reason,
                   result.compensating_controls_json, result.waived_by,
                   result.evaluated_at, result.updated_at
              from release_gate_result result
              join release_gate_definition definition on definition.id = result.gate_definition_id
              join sprint9_concept status on status.id = result.status_concept_id
             where result.release_id = ? and definition.gate_code = ?
            """, releaseId, gateCode.toUpperCase(Locale.ROOT));
    }

    private Map<String, Object> approval(UUID id) {
        return one("""
            select a.id, a.release_id, a.approval_policy_version_id,
                   status.concept_code as status, a.approval_steps_json,
                   a.requested_at, a.completed_at, a.created_at
              from release_approval_request a
              join sprint9_concept status on status.id = a.status_concept_id
             where a.id = ?
            """, id);
    }

    private Map<String, Object> dryRun(UUID id) {
        return one("""
            select run.id, run.release_id, run.environment, run.steps_json,
                   status.concept_code as status, run.evidence_references_json,
                   run.requested_by, run.started_at, run.completed_at, run.created_at
              from release_dry_run run
              join sprint9_concept status on status.id = run.status_concept_id
             where run.id = ?
            """, id);
    }

    private UUID activePolicy(String code) {
        UUID id = jdbc.query("""
            select id from sprint9_policy_version
             where policy_code = ? and status = 'ACTIVE'
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc, version_number desc limit 1
            """, result -> result.next() ? result.getObject(1, UUID.class) : null,
            code, tenant().tenantId());
        if (id == null) {
            throw new IllegalStateException("Active release policy not found: " + code);
        }
        return id;
    }

    private UUID concept(String catalogCode, String conceptCode) {
        UUID id = jdbc.query("""
            select concept.id from sprint9_concept concept
              join sprint9_concept_catalog catalog on catalog.id = concept.catalog_id
             where catalog.catalog_code = ? and concept.concept_code = ?
               and concept.active = true and catalog.active = true
               and (concept.organization_id = ? or concept.organization_id is null)
             order by (concept.organization_id is not null) desc limit 1
            """, result -> result.next() ? result.getObject(1, UUID.class) : null,
            catalogCode, conceptCode.toUpperCase(Locale.ROOT), tenant().tenantId());
        if (id == null) {
            throw new IllegalArgumentException(
                "Active " + catalogCode + " concept not found: " + conceptCode);
        }
        return id;
    }

    private Map<String, Object> one(String sql, Object... arguments) {
        try {
            return new LinkedHashMap<>(jdbc.queryForMap(sql, arguments));
        } catch (EmptyResultDataAccessException missing) {
            throw new IllegalArgumentException("Tenant-scoped release resource not found");
        }
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Stored release JSON is invalid", invalid);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Release JSON cannot be serialized", invalid);
        }
    }

    private String safeText(String value) {
        if (value == null) {
            return null;
        }
        return sanitizer.sanitize(mapper.getNodeFactory().textNode(value)).value().asText();
    }

    private void validateDigest(String digest, String component) {
        if (!digest.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(
                component + " image must use an immutable sha256 digest");
        }
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
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private void requireState(String actual, Set<String> expected, String resource) {
        if (!expected.contains(actual)) {
            throw new IllegalStateException(resource + " state " + actual
                + " is not one of " + expected);
        }
    }

    private boolean anyStepHasStatus(ObjectNode steps, String status) {
        var values = steps.elements();
        while (values.hasNext()) {
            if (status.equals(values.next().path("status").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean allStepsHaveStatus(ObjectNode steps, String status) {
        var values = steps.elements();
        boolean found = false;
        while (values.hasNext()) {
            found = true;
            if (!status.equals(values.next().path("status").asText())) {
                return false;
            }
        }
        return found;
    }
}
