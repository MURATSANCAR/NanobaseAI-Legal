package com.nanobase.specai.risk.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.risk.application.RiskCatalogPort;
import com.nanobase.specai.risk.application.RiskModels.RiskProfileInputs;
import com.nanobase.specai.risk.application.RiskModels.VersionedPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRiskCatalog implements RiskCatalogPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcRiskCatalog(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public RiskProfileInputs resolveProfileInputs(UUID organizationId, UUID projectId) {
        UUID analysisProfile = optionalUuid("""
            select id from analysis_profile where organization_id = ? and project_id = ?
            order by created_at desc limit 1
            """, organizationId, projectId).orElse(null);
        UUID ontology = requiredUuid("""
            select version_row.id from ontology_version version_row
            join ontology root on root.id = version_row.ontology_id
            where version_row.status = 'ACTIVE'
              and (root.organization_id = ? or root.organization_id is null)
            order by (root.organization_id is not null) desc, version_row.version_number desc
            limit 1
            """, organizationId);
        UUID taxonomy = requiredUuid("""
            select version_row.id from risk_taxonomy_version version_row
            join risk_taxonomy taxonomy on taxonomy.id = version_row.risk_taxonomy_id
            where version_row.status = 'ACTIVE'
              and (taxonomy.organization_id = ? or taxonomy.organization_id is null)
            order by (taxonomy.organization_id is not null) desc, version_row.version_number desc
            limit 1
            """, organizationId);
        UUID terminologySnapshot = optionalUuid("""
            select id from terminology_snapshot where organization_id = ? and project_id = ?
            order by created_at desc limit 1
            """, organizationId, projectId).orElse(null);
        Map<String, Object> prompt = jdbc.queryForMap("""
            select version_row.id, version_row.output_schema_id
            from prompt_package_version version_row
            join prompt_package package_row on package_row.id = version_row.prompt_package_id
            where version_row.status = 'ACTIVE'
              and (package_row.organization_id = ? or package_row.organization_id is null)
            order by (package_row.organization_id is not null) desc,
                     version_row.version_number desc limit 1
            """, organizationId);
        return new RiskProfileInputs(analysisProfile, taxonomy, ontology, terminologySnapshot,
            activePolicy(organizationId, "RISK_SIGNAL"),
            activePolicy(organizationId, "CONFLICT"),
            activePolicy(organizationId, "AMBIGUITY"),
            activePolicy(organizationId, "IMPACT"),
            activePolicy(organizationId, "CONFIDENCE"),
            activeSeverityPolicy(organizationId),
            uuid(prompt.get("id")),
            activePolicy(organizationId, "MODEL_ROUTING"),
            activeAuthorityPolicy(organizationId));
    }

    @Override
    public RiskProfileInputs profile(UUID organizationId, UUID profileId) {
        return jdbc.query("""
            select analysis_profile_id, risk_taxonomy_version_id, ontology_version_id,
                   terminology_snapshot_id, risk_policy_version_id,
                   conflict_policy_version_id, ambiguity_policy_version_id,
                   impact_policy_version_id, confidence_policy_version_id,
                   severity_policy_version_id, prompt_package_version_id,
                   model_routing_policy_id, document_authority_policy_id
            from risk_analysis_profile where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Risk analysis profile not found");
                }
                return new RiskProfileInputs(
                    result.getObject("analysis_profile_id", UUID.class),
                    result.getObject("risk_taxonomy_version_id", UUID.class),
                    result.getObject("ontology_version_id", UUID.class),
                    result.getObject("terminology_snapshot_id", UUID.class),
                    result.getObject("risk_policy_version_id", UUID.class),
                    result.getObject("conflict_policy_version_id", UUID.class),
                    result.getObject("ambiguity_policy_version_id", UUID.class),
                    result.getObject("impact_policy_version_id", UUID.class),
                    result.getObject("confidence_policy_version_id", UUID.class),
                    result.getObject("severity_policy_version_id", UUID.class),
                    result.getObject("prompt_package_version_id", UUID.class),
                    result.getObject("model_routing_policy_id", UUID.class),
                    result.getObject("document_authority_policy_id", UUID.class));
            }, profileId, organizationId);
    }

    @Override
    public VersionedPolicy policy(UUID organizationId, UUID versionId) {
        return jdbc.query("""
            select version_row.id, definition.policy_code, version_row.configuration_json
            from policy_version version_row
            join policy_definition definition on definition.id = version_row.policy_definition_id
            where version_row.id = ?
              and (version_row.organization_id = ? or version_row.organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Policy version not found");
                }
                return new VersionedPolicy(result.getObject("id", UUID.class),
                    result.getString("policy_code"), json(result.getObject("configuration_json")));
            }, versionId, organizationId);
    }

    @Override
    public JsonNode severityPolicy(UUID organizationId, UUID versionId) {
        return configuration("""
            select configuration_json from severity_policy_version
            where id = ? and (organization_id = ? or organization_id is null)
            """, versionId, organizationId);
    }

    @Override
    public JsonNode authorityPolicy(UUID organizationId, UUID versionId) {
        return configuration("""
            select configuration_json from document_authority_policy_version
            where id = ? and (organization_id = ? or organization_id is null)
            """, versionId, organizationId);
    }

    @Override
    public Optional<UUID> conceptId(UUID organizationId, UUID ontologyVersionId, String code) {
        return optionalUuid("""
            select id from ontology_concept
            where ontology_version_id = ? and concept_code = ? and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc limit 1
            """, ontologyVersionId, code, organizationId);
    }

    @Override
    public UUID activeClarificationStrategy(UUID organizationId) {
        return requiredUuid("""
            select version_row.id from clarification_strategy_version version_row
            join clarification_strategy strategy
              on strategy.id = version_row.clarification_strategy_id
            where version_row.status = 'ACTIVE'
              and (strategy.organization_id = ? or strategy.organization_id is null)
            order by (strategy.organization_id is not null) desc,
                     version_row.version_number desc limit 1
            """, organizationId);
    }

    @Override
    public JsonNode riskGrid(UUID organizationId) {
        return configuration("""
            select configuration_json from ui_configuration
            where configuration_code = 'RISK_GRID' and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc limit 1
            """, organizationId);
    }

    private UUID activePolicy(UUID organizationId, String type) {
        return requiredUuid("""
            select version_row.id from policy_version version_row
            join policy_definition definition on definition.id = version_row.policy_definition_id
            where version_row.status = 'ACTIVE' and definition.policy_type = ?
              and (definition.organization_id = ? or definition.organization_id is null)
            order by (definition.organization_id is not null) desc,
                     version_row.version_number desc limit 1
            """, type, organizationId);
    }

    private UUID activeSeverityPolicy(UUID organizationId) {
        return requiredUuid("""
            select version_row.id from severity_policy_version version_row
            join severity_policy policy on policy.id = version_row.severity_policy_id
            where version_row.status = 'ACTIVE'
              and (policy.organization_id = ? or policy.organization_id is null)
            order by (policy.organization_id is not null) desc,
                     version_row.version_number desc limit 1
            """, organizationId);
    }

    private UUID activeAuthorityPolicy(UUID organizationId) {
        return requiredUuid("""
            select version_row.id from document_authority_policy_version version_row
            join document_authority_policy policy
              on policy.id = version_row.document_authority_policy_id
            where version_row.status = 'ACTIVE'
              and (policy.organization_id = ? or policy.organization_id is null)
            order by (policy.organization_id is not null) desc,
                     version_row.version_number desc limit 1
            """, organizationId);
    }

    private JsonNode configuration(String sql, Object... arguments) {
        return jdbc.query(sql, result -> {
            if (!result.next()) {
                throw new IllegalArgumentException("Configuration not found");
            }
            return json(result.getObject(1));
        }, arguments);
    }

    private UUID requiredUuid(String sql, Object... arguments) {
        return optionalUuid(sql, arguments)
            .orElseThrow(() -> new IllegalStateException("Required risk configuration is missing"));
    }

    private Optional<UUID> optionalUuid(String sql, Object... arguments) {
        return jdbc.query(sql, result -> result.next()
            ? Optional.of(result.getObject(1, UUID.class)) : Optional.empty(), arguments);
    }

    private JsonNode json(Object value) {
        try {
            return mapper.readTree(String.valueOf(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid JSON configuration", exception);
        }
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }
}
