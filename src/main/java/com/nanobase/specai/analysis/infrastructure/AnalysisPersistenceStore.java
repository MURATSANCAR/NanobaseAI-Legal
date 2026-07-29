package com.nanobase.specai.analysis.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.application.AnalysisModels.ExtractionResponse;
import com.nanobase.specai.analysis.application.AnalysisModels.GroundingEvidence;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelRoutingResult;
import com.nanobase.specai.analysis.api.ExtractionEventStream;
import com.nanobase.specai.analysis.domain.Requirement;
import com.nanobase.specai.document.domain.Clause;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisPersistenceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExtractionEventStream eventStream;

    public AnalysisPersistenceStore(JdbcTemplate jdbc, ObjectMapper mapper,
                                    ExtractionEventStream eventStream) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.eventStream = eventStream;
    }

    public void event(UUID organizationId, UUID jobId, String eventType, int progress,
                      String message, Object metadata, Instant now) {
        jdbc.update("""
            insert into requirement_extraction_event (
                id, organization_id, extraction_job_id, event_type, progress,
                message, metadata_json, occurred_at
            ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
            """, UUID.randomUUID(), organizationId, jobId, eventType, progress,
            truncate(message, 1000), json(metadata), Timestamp.from(now));
        eventStream.publish(jobId, eventType, Map.of(
            "eventType", eventType,
            "progress", progress,
            "message", message == null ? "" : message,
            "metadata", metadata == null ? Map.of() : metadata,
            "occurredAt", now));
        if (List.of("COMPLETED", "FAILED", "CANCELLED").contains(eventType)) {
            eventStream.complete(jobId);
        }
    }

    public List<Map<String, Object>> events(UUID organizationId, UUID jobId) {
        return jdbc.query("""
            select id, event_type, progress, message, metadata_json, occurred_at
            from requirement_extraction_event
            where organization_id = ? and extraction_job_id = ?
            order by occurred_at, id
            """, (result, row) -> Map.of(
                "id", result.getObject("id", UUID.class),
                "eventType", result.getString("event_type"),
                "progress", result.getInt("progress"),
                "message", result.getString("message"),
                "metadata", jsonNode(String.valueOf(result.getObject("metadata_json"))),
                "occurredAt", result.getTimestamp("occurred_at").toInstant()
            ), organizationId, jobId);
    }

    public UUID routingDecision(UUID organizationId, UUID jobId, UUID clauseId,
                                ModelRoutingResult routing, UUID policyVersionId,
                                Object signals, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into model_routing_decision (
                id, organization_id, extraction_job_id, source_clause_id,
                selected_profile_id, selected_deployment_id, reason_codes_json,
                signal_snapshot_json, policy_version_id, created_at
            ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
            """, id, organizationId, jobId, clauseId, routing.profileId(),
            routing.deploymentId(), json(routing.reasonCodes()), json(signals),
            policyVersionId, Timestamp.from(now));
        return id;
    }

    public void modelRun(UUID organizationId, UUID jobId, Clause clause,
                         UUID routingDecisionId, UUID promptVersionId,
                         UUID schemaVersionId, ExtractionResponse response,
                         boolean schemaValid, String errorCode, Instant startedAt) {
        jdbc.update("""
            insert into model_run (
                id, organization_id, extraction_job_id, source_clause_id,
                routing_decision_id, logical_model_name, prompt_package_version_id,
                output_schema_version_id, status, latency_ms, input_token_count,
                output_token_count, schema_valid, error_code, created_at, completed_at
            ) values (?, ?, ?, ?, ?, 'nanobase-spec-ai', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, response.modelRunId(), organizationId, jobId, clause.id(),
            routingDecisionId, promptVersionId, schemaVersionId,
            schemaValid ? "COMPLETED" : "REJECTED", response.latencyMs(),
            response.inputTokens(), response.outputTokens(), schemaValid,
            errorCode, Timestamp.from(startedAt),
            response.completedAt() == null ? null : Timestamp.from(response.completedAt()));
    }

    public void sourceFragments(Requirement requirement, Clause clause,
                                List<String> fragments,
                                List<GroundingEvidence> evidence, Instant now) {
        for (String fragment : fragments) {
            String method = evidence.stream()
                .filter(item -> fragment.equals(item.fragment()))
                .map(GroundingEvidence::method).findFirst().orElse("INDETERMINATE");
            int start = clause.rawText().indexOf(fragment);
            Integer charStart = start < 0 ? null : start;
            Integer charEnd = start < 0 ? null : start + fragment.length();
            jdbc.update("""
                insert into requirement_source_fragment (
                    id, organization_id, requirement_id, source_clause_id,
                    fragment_text, normalized_fragment_text, char_start, char_end,
                    page_start, page_end, bounding_boxes_json, grounding_method,
                    grounding_status, metadata_json, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?,
                          ?, '{}'::jsonb, ?)
                """, UUID.randomUUID(), requirement.organizationId(), requirement.id(),
                clause.id(), fragment, normalize(fragment), charStart, charEnd,
                clause.pageStart(), clause.pageEnd(), clause.boundingBoxesJson(), method,
                "INDETERMINATE".equals(method) ? "INDETERMINATE" : "GROUNDED",
                Timestamp.from(now));
        }
    }

    public List<Map<String, Object>> sourceFragments(UUID organizationId, UUID requirementId) {
        return jdbc.query("""
            select id, source_clause_id, fragment_text, char_start, char_end,
                   page_start, page_end, bounding_boxes_json, grounding_method,
                   grounding_status, metadata_json
            from requirement_source_fragment
            where organization_id = ? and requirement_id = ?
            order by page_start, char_start nulls last
            """, (result, row) -> Map.ofEntries(
                Map.entry("id", result.getObject("id", UUID.class)),
                Map.entry("sourceClauseId",
                    result.getObject("source_clause_id", UUID.class)),
                Map.entry("fragmentText", result.getString("fragment_text")),
                Map.entry("pageStart", result.getInt("page_start")),
                Map.entry("pageEnd", result.getInt("page_end")),
                Map.entry("boundingBoxes",
                    jsonNode(String.valueOf(result.getObject("bounding_boxes_json")))),
                Map.entry("groundingMethod", result.getString("grounding_method")),
                Map.entry("groundingStatus", result.getString("grounding_status"))
            ), organizationId, requirementId);
    }

    public UUID feedback(UUID organizationId, UUID projectId, String entityType, UUID entityId,
                         String feedbackType, Object original, Object corrected, String reason,
                         String reviewerId, UUID analysisProfileId, UUID ontologyVersionId,
                         UUID policyVersionId, UUID promptVersionId, UUID modelRunId,
                         boolean approvedForLearning, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into expert_feedback (
                id, organization_id, project_id, entity_type, entity_id, feedback_type,
                original_snapshot_json, corrected_snapshot_json, reason, reviewer_id,
                analysis_profile_id, ontology_version_id, policy_version_id,
                prompt_version_id, model_run_id, approved_for_learning, created_at
            ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, organizationId, projectId, entityType, entityId, feedbackType,
            json(original), json(corrected), reason, reviewerId, analysisProfileId,
            ontologyVersionId, policyVersionId, promptVersionId, modelRunId,
            approvedForLearning, Timestamp.from(now));
        return id;
    }

    public UUID terminologySnapshot(UUID organizationId, UUID projectId,
                                    String catalogIdsJson, Instant now) {
        String source = catalogIdsJson == null ? "[]" : catalogIdsJson;
        UUID id = UUID.nameUUIDFromBytes(
            (organizationId + ":" + projectId + ":" + source)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash;
        try {
            hash = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        jdbc.update("""
            insert into terminology_snapshot (
                id, organization_id, project_id, catalog_ids_json,
                entries_hash, snapshot_json, created_at
            ) values (?, ?, ?, cast(? as jsonb), ?, cast(? as jsonb), ?)
            on conflict (id) do nothing
            """, id, organizationId, projectId, source, hash,
            json(Map.of("catalogIds", jsonNode(source))), Timestamp.from(now));
        return id;
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
            .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maximum) {
        if (value == null) {
            return "";
        }
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analysis data cannot be serialized", exception);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode jsonNode(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored analysis JSON is invalid", exception);
        }
    }
}
