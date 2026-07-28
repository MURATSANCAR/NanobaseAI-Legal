package com.nanobase.specai.knowledge.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.shared.security.CurrentTenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ui-configurations")
public class DynamicUiConfigurationController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;

    public DynamicUiConfigurationController(JdbcTemplate jdbc, ObjectMapper mapper,
                                            CurrentTenant currentTenant) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/entity-types/{conceptId}")
    JsonNode entityType(@PathVariable UUID conceptId) {
        UUID organizationId = currentTenant.require().tenantId();
        JsonNode base = configuration(organizationId, "ENTITY_PROFILE");
        return jdbc.query("""
            select concept_code, name, metadata_json::text
            from ontology_concept
            where id = ? and active = true
              and (organization_id = ? or organization_id is null)
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Active entity type concept not found");
                }
                ObjectNode response = mapper.createObjectNode();
                response.put("conceptId", conceptId.toString());
                response.put("conceptCode", result.getString(1));
                response.put("name", result.getString(2));
                response.set("conceptMetadata", tree(result.getString(3)));
                response.set("profile", base);
                response.set("attributeDefinitions", mapper.valueToTree(
                    attributeDefinitions(organizationId, conceptId)));
                return response;
            }, conceptId, organizationId);
    }

    @GetMapping("/compliance-matrix")
    JsonNode complianceMatrix() {
        return configuration(currentTenant.require().tenantId(), "COMPLIANCE_MATRIX");
    }

    @GetMapping("/compliance-decisions")
    List<Map<String, Object>> decisions() {
        UUID organizationId = currentTenant.require().tenantId();
        return jdbc.queryForList("""
            select id, concept_code as code, name, description,
                   metadata_json::text as metadata, sort_order as "sortOrder"
            from ontology_concept
            where active = true and concept_type = 'DECISION'
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order, concept_code
            """, organizationId);
    }

    @GetMapping("/entity-types")
    List<Map<String, Object>> entityTypes() {
        UUID organizationId = currentTenant.require().tenantId();
        return concepts(organizationId, "ENTITY_TYPE");
    }

    @GetMapping("/change-types")
    List<Map<String, Object>> changeTypes() {
        UUID organizationId = currentTenant.require().tenantId();
        return concepts(organizationId, "CHANGE_TYPE");
    }

    private List<Map<String, Object>> attributeDefinitions(UUID organizationId,
                                                           UUID entityTypeConceptId) {
        return jdbc.queryForList("""
            select target.id, target.concept_code as code, target.name,
                   target.metadata_json::text as metadata,
                   relation.metadata_json::text as relation_metadata
            from ontology_relation relation
            join ontology_concept target on target.id = relation.target_concept_id
            join ontology_relation_type type on type.code = relation.relation_type
            where relation.source_concept_id = ?
              and target.active = true and type.active = true
              and (relation.organization_id = ? or relation.organization_id is null)
            order by target.sort_order, target.concept_code
            """, entityTypeConceptId, organizationId);
    }

    private List<Map<String, Object>> concepts(UUID organizationId, String type) {
        return jdbc.queryForList("""
            select id, concept_code as code, name, description,
                   metadata_json::text as metadata, sort_order as "sortOrder"
            from ontology_concept
            where active = true and concept_type = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order, concept_code
            """, type, organizationId);
    }

    private JsonNode configuration(UUID organizationId, String code) {
        return jdbc.query("""
            select configuration_json::text from ui_configuration
            where configuration_code = ? and active = true
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc limit 1
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException("UI configuration not found: " + code);
                }
                return tree(result.getString(1));
            }, code, organizationId);
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored UI configuration is invalid", exception);
        }
    }
}
