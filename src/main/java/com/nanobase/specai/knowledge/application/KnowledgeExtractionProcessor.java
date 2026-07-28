package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.knowledge.application.KnowledgeAiGateway.KnowledgeRequest;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeExtractionProcessor {
    private final TenantDatabaseContext tenantDatabase;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final KnowledgeAiGateway gateway;
    private final KnowledgeExtractionJobService jobs;
    private final OutboxService outbox;

    public KnowledgeExtractionProcessor(TenantDatabaseContext tenantDatabase,
                                        JdbcTemplate jdbc, ObjectMapper mapper,
                                        KnowledgeAiGateway gateway,
                                        KnowledgeExtractionJobService jobs,
                                        OutboxService outbox) {
        this.tenantDatabase = tenantDatabase;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.gateway = gateway;
        this.jobs = jobs;
        this.outbox = outbox;
    }

    @Transactional
    public void process(UUID organizationId, UUID jobId) {
        tenantDatabase.apply(organizationId);
        Map<String, Object> job = job(organizationId, jobId);
        if (!"QUEUED".equals(job.get("status"))) {
            return;
        }
        UUID documentId = (UUID) job.get("document_id");
        UUID versionId = (UUID) job.get("document_version_id");
        UUID profileId = (UUID) job.get("profile_id");
        UUID correlationId = (UUID) job.get("correlation_id");
        Profile profile = profile(organizationId, profileId);
        List<Map<String, Object>> fragments = evidenceFragments(
            organizationId, documentId, versionId);
        jdbc.update("""
            update knowledge_extraction_job set status = 'RUNNING', started_at = now(),
                total_fragment_count = ?, updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, fragments.size(), jobId, organizationId);
        jobs.event(organizationId, jobId, "STARTED", 0,
            "Knowledge extraction started", Map.of("fragmentCount", fragments.size()));
        outbox.publish(organizationId, "KnowledgeExtraction", jobId,
            "KnowledgeExtractionStarted", "knowledge.extraction.started.v1",
            Map.of("jobId", jobId, "fragmentCount", fragments.size()), correlationId);
        try {
            var response = gateway.extract(new KnowledgeRequest(jobId, organizationId,
                "nanobase-spec-ai", profile.modelProfile(), profile.promptComponents(),
                profile.outputSchema(), profile.ontologyConcepts(), fragments, 4096,
                correlationId));
            PersistResult persisted = persistOutput(organizationId, jobId, versionId,
                profile.ontologyVersionId(), response.output());
            jdbc.update("""
                update knowledge_extraction_job set status = 'COMPLETED',
                    processed_fragment_count = total_fragment_count,
                    extracted_entity_count = ?, manual_review_count = ?,
                    completed_at = now(), updated_at = now(), version = version + 1
                where id = ? and organization_id = ?
                """, persisted.entities(), persisted.reviews(), jobId, organizationId);
            jobs.event(organizationId, jobId, "COMPLETED", 100,
                "Knowledge extraction completed",
                Map.of("entityCount", persisted.entities(),
                    "manualReviewCount", persisted.reviews()));
            outbox.publish(organizationId, "KnowledgeExtraction", jobId,
                "KnowledgeExtractionCompleted", "knowledge.extraction.completed.v1",
                Map.of("jobId", jobId, "entityCount", persisted.entities(),
                    "manualReviewCount", persisted.reviews()), correlationId);
        } catch (RuntimeException failure) {
            jdbc.update("""
                update knowledge_extraction_job set status = 'FAILED',
                    error_code = ?, error_message = ?, completed_at = now(),
                    updated_at = now(), version = version + 1
                where id = ? and organization_id = ?
                """, failure.getClass().getSimpleName(),
                truncate(failure.getMessage()), jobId, organizationId);
            jobs.event(organizationId, jobId, "FAILED", 100,
                "Knowledge extraction failed",
                Map.of("errorCode", failure.getClass().getSimpleName()));
            outbox.publish(organizationId, "KnowledgeExtraction", jobId,
                "KnowledgeExtractionFailed", "knowledge.extraction.failed.v1",
                Map.of("jobId", jobId, "errorCode", failure.getClass().getSimpleName()),
                correlationId);
            throw failure;
        }
    }

    private List<Map<String, Object>> evidenceFragments(UUID organizationId,
                                                        UUID documentId,
                                                        UUID versionId) {
        List<Map<String, Object>> clauses = jdbc.queryForList("""
            select id, raw_text, normalized_text, page_start, bounding_boxes_json::text,
                   content_hash
            from clause
            where organization_id = ? and document_version_id = ?
            order by sort_order
            """, organizationId, versionId);
        List<Map<String, Object>> fragments = new ArrayList<>();
        for (Map<String, Object> clause : clauses) {
            String contentHash = String.valueOf(clause.get("content_hash"));
            List<UUID> existing = jdbc.query("""
                select id from evidence_fragment
                where organization_id = ? and document_version_id = ? and content_hash = ?
                """, (result, row) -> result.getObject(1, UUID.class),
                organizationId, versionId, contentHash);
            UUID fragmentId = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
            if (existing.isEmpty()) {
                jdbc.update("""
                    insert into evidence_fragment (
                        id, organization_id, document_id, document_version_id, clause_id,
                        page_number, fragment_text, normalized_text, bounding_boxes_json,
                        content_hash, parser_quality, ocr_quality, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 1, 1, now())
                    """, fragmentId, organizationId, documentId, versionId,
                    clause.get("id"), clause.get("page_start"), clause.get("raw_text"),
                    clause.get("normalized_text"), clause.get("bounding_boxes_json"),
                    contentHash);
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("fragmentId", fragmentId.toString());
            safe.put("text", clause.get("raw_text"));
            safe.put("pageNumber", clause.get("page_start"));
            fragments.add(safe);
        }
        return List.copyOf(fragments);
    }

    private PersistResult persistOutput(UUID organizationId, UUID jobId,
                                        UUID versionId, UUID ontologyVersionId,
                                        JsonNode output) {
        Map<String, UUID> fragmentIds = new HashMap<>();
        jdbc.query("""
            select id from evidence_fragment
            where organization_id = ? and document_version_id = ?
            """, result -> {
                while (result.next()) {
                    UUID id = result.getObject(1, UUID.class);
                    fragmentIds.put(id.toString(), id);
                }
                return null;
            }, organizationId, versionId);
        Map<String, UUID> entities = new HashMap<>();
        int reviews = 0;
        for (JsonNode entity : output.path("entities")) {
            List<UUID> sources = sourceIds(entity.path("sourceFragments"), fragmentIds);
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("Every extracted entity needs evidence");
            }
            UUID type = concept(organizationId, ontologyVersionId,
                entity.path("entityTypeConcept").asText(), sources.getFirst());
            if (type == null) {
                reviews++;
                continue;
            }
            UUID entityId = UUID.randomUUID();
            String temporaryId = entity.path("temporaryId").asText();
            jdbc.update("""
                insert into knowledge_entity (
                    id, organization_id, entity_code, entity_type_concept_id,
                    name, description, status, attributes_json, source_type,
                    source_reference_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'EXTRACTED', '{}'::jsonb,
                          'KNOWLEDGE_EXTRACTION', ?, now(), now())
                """, entityId, organizationId, code(jobId, temporaryId), type,
                entity.path("name").asText(), nullable(entity.path("description")),
                sources.getFirst());
            entities.put(temporaryId, entityId);
            for (JsonNode attribute : entity.path("attributes")) {
                reviews += persistAttribute(organizationId, ontologyVersionId, entityId,
                    attribute, fragmentIds);
            }
        }
        for (JsonNode relation : output.path("relations")) {
            UUID source = entities.get(relation.path("sourceTemporaryId").asText());
            UUID target = entities.get(relation.path("targetTemporaryId").asText());
            List<UUID> sources = sourceIds(relation.path("sourceFragments"), fragmentIds);
            if (source == null || target == null || sources.isEmpty()) {
                reviews++;
                continue;
            }
            UUID concept = concept(organizationId, ontologyVersionId,
                relation.path("relationConcept").asText(), sources.getFirst());
            if (concept == null) {
                reviews++;
                continue;
            }
            jdbc.update("""
                insert into knowledge_relation (
                    id, organization_id, source_entity_id, target_entity_id,
                    relation_concept_id, attributes_json, confidence,
                    source_fragment_id, review_status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, 'PENDING', now(), now())
                """, UUID.randomUUID(), organizationId, source, target, concept,
                relation.path("attributes").isMissingNode() ? "{}"
                    : relation.path("attributes").toString(),
                confidence(relation), sources.getFirst());
        }
        for (JsonNode capability : output.path("capabilities")) {
            UUID owner = entities.get(capability.path("ownerTemporaryId").asText());
            List<UUID> sources = sourceIds(capability.path("sourceFragments"), fragmentIds);
            if (owner == null || sources.isEmpty()) {
                reviews++;
                continue;
            }
            UUID concept = concept(organizationId, ontologyVersionId,
                capability.path("capabilityConcept").asText(), sources.getFirst());
            if (concept == null) {
                reviews++;
                continue;
            }
            UUID capabilityId = UUID.randomUUID();
            jdbc.update("""
                insert into capability (
                    id, organization_id, owner_entity_id, capability_concept_id,
                    name, description, capability_attributes_json, status, confidence,
                    review_status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, 'EXTRACTED', ?, 'PENDING',
                          now(), now())
                """, capabilityId, organizationId, owner, concept,
                capability.path("name").asText(), nullable(capability.path("description")),
                capability.path("attributes").isMissingNode() ? "{}"
                    : capability.path("attributes").toString(), confidence(capability));
            for (UUID source : sources) {
                UUID role = firstConceptByType(organizationId, "EVIDENCE_ROLE");
                jdbc.update("""
                    insert into capability_evidence (
                        id, organization_id, capability_id, evidence_fragment_id,
                        evidence_role_concept_id, strength, created_at
                    ) values (?, ?, ?, ?, ?, ?, now())
                    """, UUID.randomUUID(), organizationId, capabilityId, source,
                    role, confidence(capability));
            }
        }
        return new PersistResult(entities.size(), reviews);
    }

    private int persistAttribute(UUID organizationId, UUID ontologyVersionId,
                                 UUID entityId, JsonNode attribute,
                                 Map<String, UUID> fragmentIds) {
        List<UUID> sources = sourceIds(attribute.path("sourceFragments"), fragmentIds);
        if (sources.isEmpty()) {
            return 1;
        }
        UUID concept = concept(organizationId, ontologyVersionId,
            attribute.path("attributeConcept").asText(), sources.getFirst());
        if (concept == null) {
            return 1;
        }
        JsonNode value = attribute.path("value");
        String type = value.path("type").asText("UNSUPPORTED");
        jdbc.update("""
            insert into entity_attribute (
                id, organization_id, entity_id, attribute_concept_id, value_type,
                text_value, numeric_value, numeric_value_end, boolean_value,
                date_value, json_value, unit_concept_id, confidence,
                source_fragment_id, review_status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?::jsonb, ?, ?,
                      ?, 'PENDING', now(), now())
            """, UUID.randomUUID(), organizationId, entityId, concept, type,
            text(value, "textValue"), decimal(value, "numericValue"),
            decimal(value, "numericValueEnd"),
            value.has("booleanValue") ? value.path("booleanValue").booleanValue() : null,
            text(value, "dateValue"), value.toString(),
            resolveOptionalConcept(organizationId, ontologyVersionId,
                value.path("unit").asText(null)), confidence(attribute),
            sources.getFirst());
        return 0;
    }

    private UUID concept(UUID organizationId, UUID ontologyVersionId,
                         String code, UUID sourceFragmentId) {
        UUID resolved = resolveOptionalConcept(organizationId, ontologyVersionId, code);
        if (resolved != null) {
            return resolved;
        }
        jdbc.update("""
            insert into candidate_concept (
                id, organization_id, ontology_version_id, proposed_code,
                proposed_name, proposed_type, source_fragment_id, metadata_json,
                created_at
            ) values (?, ?, ?, ?, ?, 'MODEL_DISCOVERED', ?, '{}'::jsonb, now())
            on conflict (organization_id, ontology_version_id, proposed_code,
                         source_fragment_id) do nothing
            """, UUID.randomUUID(), organizationId, ontologyVersionId,
            code.isBlank() ? "UNNAMED" : code, code.isBlank() ? "Unnamed" : code,
            sourceFragmentId);
        return null;
    }

    private UUID resolveOptionalConcept(UUID organizationId, UUID ontologyVersionId,
                                        String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where ontology_version_id = ? and concept_code = ? and active = true
              and (organization_id = ? or organization_id is null)
            limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            ontologyVersionId, code, organizationId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private UUID firstConceptByType(UUID organizationId, String conceptType) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept where concept_type = ? and active = true
              and (organization_id = ? or organization_id is null)
            order by sort_order limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            conceptType, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("Ontology concept type is not configured");
        }
        return ids.getFirst();
    }

    private List<UUID> sourceIds(JsonNode sources, Map<String, UUID> allowed) {
        List<UUID> ids = new ArrayList<>();
        if (sources.isArray()) {
            for (JsonNode source : sources) {
                String value = source.isTextual() ? source.asText()
                    : source.path("fragmentId").asText();
                UUID id = allowed.get(value);
                if (id == null) {
                    throw new IllegalArgumentException(
                        "Model returned an unknown evidence fragment ID");
                }
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private Profile profile(UUID organizationId, UUID id) {
        return jdbc.query("""
            select profile.ontology_version_id, schema.json_schema::text,
                   prompt.component_configuration_json::text
            from knowledge_extraction_profile profile
            join output_schema_version schema
              on schema.id = profile.output_schema_version_id
            join prompt_package_version prompt
              on prompt.id = profile.prompt_package_version_id
            where profile.id = ? and profile.organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Knowledge extraction profile is missing");
                }
                JsonNode promptConfig = tree(result.getString(3));
                List<String> components = new ArrayList<>();
                for (JsonNode code : promptConfig.path("components")) {
                    String content = jdbc.queryForObject("""
                        select content_template from prompt_component
                        where component_code = ?
                          and (organization_id = ? or organization_id is null)
                        order by (organization_id is not null) desc limit 1
                        """, String.class, code.asText(), organizationId);
                    components.add(content);
                }
                UUID ontologyVersionId = result.getObject(1, UUID.class);
                List<Map<String, Object>> concepts = jdbc.queryForList("""
                    select concept_code as code, name, concept_type as type,
                           metadata_json::text as metadata
                    from ontology_concept
                    where ontology_version_id = ? and active = true
                      and (organization_id = ? or organization_id is null)
                    order by sort_order, concept_code
                    """, ontologyVersionId, organizationId);
                String modelProfile = jdbc.queryForObject("""
                    select profile_code from model_profile
                    where active = true and (organization_id = ? or organization_id is null)
                    order by (organization_id is not null) desc limit 1
                    """, String.class, organizationId);
                return new Profile(ontologyVersionId, tree(result.getString(2)),
                    List.copyOf(components), concepts, modelProfile);
            }, id, organizationId);
    }

    private Map<String, Object> job(UUID organizationId, UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select * from knowledge_extraction_job
            where id = ? and organization_id = ? for update
            """, id, organizationId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Knowledge extraction job not found");
        }
        return rows.getFirst();
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored extraction configuration is invalid",
                exception);
        }
    }

    private String nullable(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode()
            ? null : value.asText();
    }

    private String text(JsonNode value, String field) {
        return value.hasNonNull(field) ? value.path(field).asText() : null;
    }

    private java.math.BigDecimal decimal(JsonNode value, String field) {
        return value.hasNonNull(field) && value.path(field).isNumber()
            ? value.path(field).decimalValue() : null;
    }

    private double confidence(JsonNode node) {
        return Math.max(0, Math.min(1, node.path("confidence").asDouble(0.5)));
    }

    private String code(UUID jobId, String temporaryId) {
        String safe = temporaryId == null ? "ENTITY"
            : temporaryId.replaceAll("[^A-Za-z0-9_-]", "_");
        return "EXT-" + jobId.toString().substring(0, 8) + "-" + safe;
    }

    private String truncate(String value) {
        if (value == null) {
            return "Knowledge extraction failed";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private record Profile(UUID ontologyVersionId, JsonNode outputSchema,
                           List<String> promptComponents,
                           List<Map<String, Object>> ontologyConcepts,
                           String modelProfile) {
    }

    private record PersistResult(int entities, int reviews) {
    }
}
