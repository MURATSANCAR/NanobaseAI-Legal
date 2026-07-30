package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.analysis.application.AnalysisProfileService;
import com.nanobase.specai.analysis.api.ExtractionEventStream;
import com.nanobase.specai.analysis.domain.AnalysisProfile;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeExtractionJobService {
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");
    private final JdbcTemplate jdbc;
    private final CurrentTenant currentTenant;
    private final AnalysisProfileService analysisProfiles;
    private final ObjectMapper mapper;
    private final OutboxService outbox;
    private final ExtractionEventStream eventStream;

    public KnowledgeExtractionJobService(JdbcTemplate jdbc, CurrentTenant currentTenant,
                                         AnalysisProfileService analysisProfiles,
                                         ObjectMapper mapper, OutboxService outbox,
                                         ExtractionEventStream eventStream) {
        this.jdbc = jdbc;
        this.currentTenant = currentTenant;
        this.analysisProfiles = analysisProfiles;
        this.mapper = mapper;
        this.outbox = outbox;
        this.eventStream = eventStream;
    }

    @Transactional
    public Map<String, Object> create(UUID documentId, UUID documentRoleConceptId) {
        TenantPrincipal principal = currentTenant.require();
        AnalysisProfile analysis = analysisProfiles.createSnapshot(documentId);
        UUID role = documentRoleConceptId == null
            ? resolveConcept(principal.tenantId(), "GENERIC_KNOWLEDGE_DOCUMENT")
            : requireConcept(principal.tenantId(), documentRoleConceptId);
        UUID knowledgePolicy = policy(principal.tenantId(), "KNOWLEDGE_EXTRACTION");
        Prompt prompt = prompt(principal.tenantId(), "KNOWLEDGE_EXTRACTION");
        UUID profileId = UUID.randomUUID();
        UUID terminologySnapshotId = UUID.nameUUIDFromBytes(
            ("knowledge:" + analysis.id()).getBytes(StandardCharsets.UTF_8));
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("analysisProfileId", analysis.id().toString());
        snapshot.put("documentVersionId", analysis.documentVersionId().toString());
        snapshot.put("ontologyVersionId", analysis.ontologyVersionId().toString());
        snapshot.put("knowledgePolicyVersionId", knowledgePolicy.toString());
        snapshot.put("promptPackageVersionId", prompt.versionId().toString());
        snapshot.put("outputSchemaVersionId", prompt.outputSchemaVersionId().toString());
        snapshot.put("documentRoleConceptId", role.toString());
        String snapshotJson = snapshot.toString();
        jdbc.update("""
            insert into knowledge_extraction_profile (
                id, organization_id, document_version_id, analysis_profile_id,
                ontology_version_id, terminology_snapshot_id, policy_version_id,
                prompt_package_version_id, output_schema_version_id,
                model_routing_policy_id, confidence_policy_id, document_role_concept_id,
                snapshot_json, content_hash, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
            """, profileId, principal.tenantId(), analysis.documentVersionId(), analysis.id(),
            analysis.ontologyVersionId(), terminologySnapshotId, knowledgePolicy,
            prompt.versionId(), prompt.outputSchemaVersionId(), analysis.modelRoutingPolicyId(),
            analysis.confidencePolicyId(), role, snapshotJson, sha256(snapshotJson));
        UUID jobId = UUID.randomUUID();
        UUID correlationId = RequestContext.current().correlationId();
        jdbc.update("""
            insert into knowledge_extraction_job (
                id, organization_id, document_id, document_version_id, profile_id,
                status, correlation_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, 'QUEUED', ?, now(), now())
            """, jobId, principal.tenantId(), documentId, analysis.documentVersionId(),
            profileId, correlationId);
        event(principal.tenantId(), jobId, "QUEUED", 0,
            "Knowledge extraction queued", Map.of());
        outbox.publish(principal.tenantId(), "KnowledgeExtraction", jobId,
            "KnowledgeExtractionRequested", "knowledge.extraction.requested.v1",
            new KnowledgeRequested(jobId, documentId, analysis.documentVersionId(), profileId),
            correlationId);
        return get(jobId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID jobId) {
        UUID organizationId = currentTenant.require().tenantId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, document_id, document_version_id, profile_id, status,
                   total_fragment_count, processed_fragment_count,
                   extracted_entity_count, manual_review_count,
                   error_code, error_message, correlation_id, started_at,
                   completed_at, created_at, updated_at, version,
                   document_purpose_code, current_stage_code, existing_knowledge_used
            from knowledge_extraction_job
            where id = ? and organization_id = ?
            """, jobId, organizationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Knowledge extraction job not found");
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(UUID jobId) {
        UUID organizationId = currentTenant.require().tenantId();
        get(jobId);
        return jdbc.queryForList("""
            select id, event_type as "eventType", progress, message,
                   metadata_json::text as "metadataJson", occurred_at as "occurredAt"
            from knowledge_extraction_event
            where organization_id = ? and extraction_job_id = ?
            order by occurred_at, id
            """, organizationId, jobId);
    }

    @Transactional
    public Map<String, Object> cancel(UUID jobId) {
        UUID organizationId = currentTenant.require().tenantId();
        String status = String.valueOf(get(jobId).get("status"));
        if (TERMINAL.contains(status)) {
            throw new IllegalStateException("Knowledge extraction job is already terminal");
        }
        jdbc.update("""
            update knowledge_extraction_job
            set status = 'CANCELLED', completed_at = now(), updated_at = now(),
                version = version + 1
            where id = ? and organization_id = ?
            """, jobId, organizationId);
        event(organizationId, jobId, "CANCELLED", 100,
            "Knowledge extraction cancelled", Map.of());
        return get(jobId);
    }

    void event(UUID organizationId, UUID jobId, String type, int progress,
               String message, Object metadata) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
            insert into knowledge_extraction_event (
                id, organization_id, extraction_job_id, event_type, progress,
                message, metadata_json, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, now())
            """, eventId, organizationId, jobId, type, progress,
            message, json(metadata));
        eventStream.publish(jobId, type, Map.of(
            "id", eventId,
            "eventType", type,
            "progress", progress,
            "message", message,
            "metadata", metadata == null ? Map.of() : metadata
        ));
        if (TERMINAL.contains(type)) {
            eventStream.complete(jobId);
        }
    }

    private UUID policy(UUID organizationId, String policyType) {
        List<UUID> ids = jdbc.query("""
            select version.id
            from policy_version version
            join policy_definition definition on definition.id = version.policy_definition_id
            where definition.policy_type = ? and version.status = 'ACTIVE'
              and (version.organization_id = ? or version.organization_id is null)
            order by (version.organization_id is not null) desc, version.version_number desc
            limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            policyType, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Active policy is not configured: " + policyType);
        }
        return ids.getFirst();
    }

    private Prompt prompt(UUID organizationId, String taskType) {
        List<Prompt> prompts = jdbc.query("""
            select version.id, version.output_schema_id
            from prompt_package package
            join prompt_package_version version on version.id = package.active_version_id
            where package.task_type = ? and version.status = 'ACTIVE'
              and (package.organization_id = ? or package.organization_id is null)
            order by (package.organization_id is not null) desc, version.version_number desc
            limit 1
            """, (result, row) -> new Prompt(result.getObject(1, UUID.class),
                result.getObject(2, UUID.class)), taskType, organizationId);
        if (prompts.isEmpty()) {
            throw new IllegalStateException("Active prompt package is not configured");
        }
        return prompts.getFirst();
    }

    private UUID resolveConcept(UUID organizationId, String code) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept where concept_code = ? and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc limit 1
            """, (result, row) -> result.getObject(1, UUID.class), code, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Required ontology concept is not configured");
        }
        return ids.getFirst();
    }

    private UUID requireConcept(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from ontology_concept where id = ? and active = true
              and (organization_id = ? or organization_id is null)
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Document role concept is not active");
        }
        return id;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Knowledge event cannot be serialized", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record KnowledgeRequested(UUID jobId, UUID documentId,
                                     UUID documentVersionId, UUID profileId) {
    }

    private record Prompt(UUID versionId, UUID outputSchemaVersionId) {
    }
}
