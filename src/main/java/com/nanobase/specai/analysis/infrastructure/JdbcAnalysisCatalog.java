package com.nanobase.specai.analysis.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.analysis.application.AnalysisCatalogPort;
import com.nanobase.specai.analysis.application.AnalysisModels.ConceptMatch;
import com.nanobase.specai.analysis.application.AnalysisModels.ModelCandidate;
import com.nanobase.specai.analysis.application.AnalysisModels.PolicyDocument;
import com.nanobase.specai.analysis.application.AnalysisModels.ProfileInputs;
import com.nanobase.specai.analysis.application.AnalysisModels.PromptMaterial;
import com.nanobase.specai.analysis.application.AnalysisModels.TerminologyMatch;
import com.nanobase.specai.analysis.application.AnalysisModels.UnitMatch;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAnalysisCatalog implements AnalysisCatalogPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcAnalysisCatalog(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public ProfileInputs resolveProfileInputs(UUID organizationId, String sector,
                                              String documentType, String language) {
        UUID ontology = requiredUuid("""
            select version_row.id
            from ontology_version version_row
            join ontology root on root.id = version_row.ontology_id
            where version_row.status = 'ACTIVE'
              and (root.organization_id = ? or root.organization_id is null)
            order by (root.organization_id is not null) desc, version_row.version_number desc
            limit 1
            """, organizationId);
        List<UUID> terminology = jdbc.query("""
            select catalog.id
            from terminology_catalog catalog
            where catalog.status = 'ACTIVE'
              and (catalog.organization_id = ? or catalog.organization_id is null)
              and (catalog.language is null or catalog.language = ?)
              and (catalog.sector is null or catalog.sector = ?)
              and (catalog.document_type is null or catalog.document_type = ?)
            order by (catalog.organization_id is not null) desc, catalog.created_at
            """, (result, row) -> result.getObject("id", UUID.class),
            organizationId, language, sector, documentType);
        UUID extractionPolicy = activePolicy(organizationId, "EXTRACTION");
        UUID modelPolicy = activePolicy(organizationId, "MODEL_ROUTING");
        UUID confidencePolicy = activePolicy(organizationId, "CONFIDENCE");
        Map<String, Object> prompt = jdbc.queryForMap("""
            select version_row.id, version_row.output_schema_id
            from prompt_package_version version_row
            join prompt_package package_row on package_row.id = version_row.prompt_package_id
            where version_row.status = 'ACTIVE'
              and (package_row.organization_id = ? or package_row.organization_id is null)
            order by (package_row.organization_id is not null) desc,
                     version_row.version_number desc
            limit 1
            """, organizationId);
        return new ProfileInputs(ontology, List.copyOf(terminology), extractionPolicy,
            uuid(prompt.get("id")), uuid(prompt.get("output_schema_id")), modelPolicy,
            confidencePolicy);
    }

    @Override
    public PolicyDocument policy(UUID organizationId, UUID policyVersionId) {
        return jdbc.query("""
            select version_row.id, definition.policy_code, definition.policy_type,
                   version_row.configuration_json
            from policy_version version_row
            join policy_definition definition
              on definition.id = version_row.policy_definition_id
            where version_row.id = ?
              and (version_row.organization_id = ? or version_row.organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Policy version not found: "
                        + policyVersionId);
                }
                return new PolicyDocument(result.getObject("id", UUID.class),
                    result.getString("policy_code"), result.getString("policy_type"),
                    json(result.getObject("configuration_json")));
            }, policyVersionId, organizationId);
    }

    @Override
    public JsonNode outputSchema(UUID organizationId, UUID outputSchemaVersionId) {
        return jdbc.query("""
            select json_schema
            from output_schema_version
            where id = ? and (organization_id = ? or organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Output schema version not found: "
                        + outputSchemaVersionId);
                }
                return json(result.getObject("json_schema"));
            }, outputSchemaVersionId, organizationId);
    }

    @Override
    public PromptMaterial prompt(UUID organizationId, UUID promptPackageVersionId) {
        return jdbc.query("""
            select component_configuration_json, output_schema_id
            from prompt_package_version
            where id = ? and (organization_id = ? or organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Prompt package version not found: "
                        + promptPackageVersionId);
                }
                JsonNode configuration = json(result.getObject("component_configuration_json"));
                List<String> codes = new ArrayList<>();
                configuration.path("components").forEach(item -> codes.add(item.asText()));
                List<String> components = new ArrayList<>();
                for (String code : codes) {
                    List<String> content = jdbc.query("""
                        select content_template
                        from prompt_component
                        where component_code = ?
                          and (organization_id = ? or organization_id is null)
                        order by (organization_id is not null) desc
                        limit 1
                        """, (rows, row) -> rows.getString(1), code, organizationId);
                    if (content.isEmpty()) {
                        throw new IllegalStateException("Prompt component not found: " + code);
                    }
                    components.add(content.getFirst());
                }
                return new PromptMaterial(promptPackageVersionId,
                    result.getObject("output_schema_id", UUID.class),
                    List.copyOf(components), configuration);
            }, promptPackageVersionId, organizationId);
    }

    @Override
    public List<TerminologyMatch> terminologyMatches(UUID organizationId,
                                                      List<UUID> catalogIds,
                                                      String normalizedText) {
        if (catalogIds == null || catalogIds.isEmpty() || normalizedText == null) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(
            catalogIds.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(organizationId);
        arguments.addAll(catalogIds);
        return jdbc.query("""
            select id, catalog_id, term, normalized_term, term_type, semantic_role,
                   weight, metadata_json
            from terminology_entry
            where active = true and approval_status = 'APPROVED'
              and (organization_id = ? or organization_id is null)
              and catalog_id in (%s)
            """.formatted(placeholders), (result, row) -> {
                String term = result.getString("normalized_term");
                if (!normalizedText.contains(term)) {
                    return null;
                }
                return new TerminologyMatch(result.getObject("id", UUID.class),
                    result.getObject("catalog_id", UUID.class), result.getString("term"),
                    result.getString("term_type"), result.getString("semantic_role"),
                    result.getDouble("weight"), json(result.getObject("metadata_json")));
            }, arguments.toArray()).stream().filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<ConceptMatch> concept(UUID organizationId, UUID ontologyVersionId,
                                          String code) {
        return jdbc.query("""
            select id, concept_code, name, concept_type, metadata_json
            from ontology_concept
            where ontology_version_id = ? and concept_code = ? and active = true
              and (organization_id = ? or organization_id is null)
            """, result -> result.next()
                ? Optional.of(new ConceptMatch(result.getObject("id", UUID.class),
                    result.getString("concept_code"), result.getString("name"),
                    result.getString("concept_type"), json(result.getObject("metadata_json"))))
                : Optional.empty(), ontologyVersionId, code, organizationId);
    }

    @Override
    public Optional<UnitMatch> unitByAlias(UUID organizationId, String language,
                                           String normalizedAlias) {
        return jdbc.query("""
            select unit_row.id, unit_row.canonical_code, unit_row.symbol,
                   unit_row.dimension, unit_row.conversion_metadata_json
            from measurement_unit_alias alias_row
            join measurement_unit unit_row on unit_row.id = alias_row.unit_id
            where alias_row.normalized_alias = ?
              and alias_row.active = true and unit_row.active = true
              and (alias_row.language is null or alias_row.language = ?)
              and (unit_row.organization_id = ? or unit_row.organization_id is null)
            order by (unit_row.organization_id is not null) desc
            limit 1
            """, result -> result.next()
                ? Optional.of(new UnitMatch(result.getObject("id", UUID.class),
                    result.getString("canonical_code"), result.getString("symbol"),
                    result.getString("dimension"),
                    json(result.getObject("conversion_metadata_json"))))
                : Optional.empty(), normalizedAlias, language, organizationId);
    }

    @Override
    public List<ModelCandidate> modelCandidates(UUID organizationId) {
        return jdbc.query("""
            select profile.id as profile_id, profile.profile_code,
                   deployment.id as deployment_id, deployment.health_status,
                   profile.selection_policy_json
            from model_profile profile
            join model_deployment deployment on deployment.active = true
            join model_definition definition
              on definition.id = deployment.model_definition_id
             and definition.status = 'ACTIVE'
            where profile.active = true
              and (profile.organization_id = ? or profile.organization_id is null)
              and (
                not jsonb_exists(profile.selection_policy_json, 'deploymentIds')
                or profile.selection_policy_json -> 'deploymentIds'
                   @> jsonb_build_array(deployment.id::text)
              )
            order by (profile.organization_id is not null) desc, profile.profile_code
            """, (result, row) -> new ModelCandidate(
                result.getObject("profile_id", UUID.class), result.getString("profile_code"),
                result.getObject("deployment_id", UUID.class), result.getString("health_status"),
                json(result.getObject("selection_policy_json")), Map.of()), organizationId);
    }

    @Override
    public JsonNode requirementGrid(UUID organizationId) {
        return jdbc.query("""
            select configuration_json
            from ui_configuration
            where configuration_code = 'REQUIREMENT_GRID' and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc
            limit 1
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("Requirement grid configuration not found");
                }
                return json(result.getObject("configuration_json"));
            }, organizationId);
    }

    @Override
    public List<Map<String, Object>> ontologies(UUID organizationId) {
        return normalizedRows(jdbc.queryForList("""
            select id, code, name, description, scope, status, created_at, updated_at
            from ontology
            where organization_id = ? or organization_id is null
            order by (organization_id is not null) desc, name
            """, organizationId));
    }

    @Override
    public List<Map<String, Object>> ontologyVersions(UUID organizationId, UUID ontologyId) {
        return normalizedRows(jdbc.queryForList("""
            select id, ontology_id, version_number, status, content_hash,
                   approved_by, approved_at, created_at
            from ontology_version
            where ontology_id = ?
              and (organization_id = ? or organization_id is null)
            order by version_number desc
            """, ontologyId, organizationId));
    }

    @Override
    public List<Map<String, Object>> ontologyConcepts(UUID organizationId, UUID versionId) {
        return normalizedRows(jdbc.queryForList("""
            select id, ontology_version_id, parent_concept_id, concept_code, name,
                   description, concept_type, metadata_json, active, sort_order, created_at
            from ontology_concept
            where ontology_version_id = ?
              and (organization_id = ? or organization_id is null)
            order by sort_order, name
            """, versionId, organizationId));
    }

    @Override
    public List<Map<String, Object>> terminologyCatalogs(UUID organizationId) {
        return normalizedRows(jdbc.queryForList("""
            select id, name, scope, language, sector, document_type, status,
                   created_at, updated_at
            from terminology_catalog
            where organization_id = ? or organization_id is null
            order by (organization_id is not null) desc, name
            """, organizationId));
    }

    @Override
    public UUID createCandidateTerm(UUID organizationId, UUID catalogId, String term,
                                    String termType, String semanticRole, double weight,
                                    JsonNode metadata) {
        Integer catalogCount = jdbc.queryForObject("""
            select count(*) from terminology_catalog
            where id = ? and organization_id = ?
            """, Integer.class, catalogId, organizationId);
        if (catalogCount == null || catalogCount == 0) {
            throw new IllegalArgumentException(
                "Candidate terms can only be added to the organization's own catalog");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into terminology_entry (
                id, organization_id, catalog_id, term, normalized_term, term_type,
                semantic_role, weight, metadata_json, approval_status, active,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), 'CANDIDATE', false, ?, ?)
            """, id, organizationId, catalogId, term, normalize(term), termType,
            semanticRole, weight, stringify(metadata), Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    @Override
    public void decideCandidateTerm(UUID organizationId, UUID entryId, boolean approved) {
        int updated = jdbc.update("""
            update terminology_entry
            set approval_status = ?, active = ?, updated_at = ?
            where id = ? and organization_id = ? and approval_status = 'CANDIDATE'
            """, approved ? "APPROVED" : "REJECTED", approved, Timestamp.from(Instant.now()),
            entryId, organizationId);
        if (updated == 0) {
            throw new IllegalArgumentException("Candidate terminology entry not found: " + entryId);
        }
    }

    private UUID activePolicy(UUID organizationId, String type) {
        return requiredUuid("""
            select version_row.id
            from policy_version version_row
            join policy_definition definition
              on definition.id = version_row.policy_definition_id
            where version_row.status = 'ACTIVE' and definition.policy_type = ?
              and (definition.organization_id = ? or definition.organization_id is null)
            order by (definition.organization_id is not null) desc,
                     version_row.version_number desc
            limit 1
            """, type, organizationId);
    }

    private UUID requiredUuid(String sql, Object... arguments) {
        List<UUID> values = jdbc.query(sql,
            (result, row) -> result.getObject(1, UUID.class), arguments);
        if (values.isEmpty()) {
            throw new IllegalStateException("Required active analysis configuration is missing");
        }
        return values.getFirst();
    }

    private UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }

    private JsonNode json(Object value) {
        try {
            return mapper.readTree(String.valueOf(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored analysis JSON is invalid", exception);
        }
    }

    private String stringify(JsonNode value) {
        try {
            return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Analysis JSON cannot be serialized", exception);
        }
    }

    private String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
            .toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> normalized = new LinkedHashMap<>();
            row.forEach((key, value) -> normalized.put(toCamelCase(key),
                value != null && value.getClass().getName().equals("org.postgresql.util.PGobject")
                    ? json(value) : value));
            return java.util.Collections.unmodifiableMap(normalized);
        }).toList();
    }

    private String toCamelCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : value.toLowerCase().toCharArray()) {
            if (character == '_') {
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return result.toString();
    }
}
