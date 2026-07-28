package com.nanobase.specai.workflow.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nanobase.specai.shared.security.CurrentTenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ui-configurations")
public class WorkflowUiConfigurationController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;

    public WorkflowUiConfigurationController(JdbcTemplate jdbc, ObjectMapper mapper,
                                             CurrentTenant currentTenant) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
    }

    @GetMapping("/workflow-concepts/{conceptType}")
    List<Map<String, Object>> concepts(@PathVariable String conceptType) {
        UUID tenant = currentTenant.require().tenantId();
        return jdbc.query("""
            select id, concept_code, name, description, metadata_json::text, sort_order
              from ontology_concept
             where concept_type = ? and active = true
               and (organization_id = ? or organization_id is null)
             order by (organization_id is not null) desc, sort_order, concept_code
            """, (result, row) -> Map.of(
                "id", result.getObject("id", UUID.class),
                "code", result.getString("concept_code"),
                "name", result.getString("name"),
                "description", result.getString("description") == null
                    ? "" : result.getString("description"),
                "metadata", parse(result.getString(5)),
                "sortOrder", result.getInt("sort_order")), conceptType, tenant);
    }

    @GetMapping("/task-grid")
    ObjectNode taskGrid(@RequestParam(defaultValue = "SPRINT_7_OPERATIONS")
                        String dashboardCode) {
        JsonNode dashboard = dashboard(dashboardCode);
        ObjectNode response = mapper.createObjectNode();
        response.put("dashboardCode", dashboardCode);
        ArrayNode columns = response.putArray("columns");
        for (JsonNode widget : dashboard.path("widgets")) {
            JsonNode configured = widget.path("dataSource").path("columns");
            if (configured.isArray()) {
                configured.forEach(column -> {
                    ObjectNode value = columns.addObject();
                    value.put("key", column.asText());
                    value.put("label", column.asText());
                    value.put("visible", true);
                });
                break;
            }
        }
        return response;
    }

    @GetMapping("/report-designer")
    Map<String, Object> reportDesigner() {
        List<Map<String, Object>> dataPolicies = jdbc.queryForList("""
            select v.id, p.policy_code as code, v.version_number as "versionNumber"
              from report_data_policy p
              join report_data_policy_version v on v.id = p.active_version_id
             order by p.policy_code
            """);
        List<Map<String, Object>> templates = jdbc.queryForList("""
            select v.id, t.template_code as code, v.version_number as "versionNumber",
                   c.concept_code as "formatConceptCode", t.format_concept_id as "formatConceptId",
                   t.language
              from report_template t
              join report_template_version v on v.id = t.active_version_id
              join ontology_concept c on c.id = t.format_concept_id
             order by t.template_code
            """);
        return Map.of("dataPolicies", dataPolicies, "templates", templates);
    }

    @GetMapping("/decision-policies")
    List<Map<String, Object>> decisionPolicies() {
        return jdbc.queryForList("""
            select v.id, p.policy_code as code, v.version_number as "versionNumber"
              from decision_policy p
              join decision_policy_version v on v.id = p.active_version_id
             order by p.policy_code
            """);
    }

    @GetMapping("/dashboard/{code}")
    JsonNode dashboard(@PathVariable String code) {
        UUID tenant = currentTenant.require().tenantId();
        return jdbc.query("""
            select d.id, d.name, v.id as version_id, v.version_number,
                   v.layout_configuration_json::text
              from dashboard_definition d
              join dashboard_version v on v.id = d.active_version_id
             where d.dashboard_code = ?
               and (d.organization_id = ? or d.organization_id is null)
             order by (d.organization_id is not null) desc limit 1
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Dashboard configuration not found");
                }
                UUID versionId = result.getObject("version_id", UUID.class);
                ObjectNode dashboard = mapper.createObjectNode();
                dashboard.put("id", result.getObject("id", UUID.class).toString());
                dashboard.put("code", code);
                dashboard.put("name", result.getString("name"));
                dashboard.put("versionId", versionId.toString());
                dashboard.put("versionNumber", result.getInt("version_number"));
                dashboard.set("layout", parse(result.getString(5)));
                dashboard.set("widgets", mapper.valueToTree(jdbc.query("""
                    select w.widget_code, c.concept_code,
                           w.data_source_configuration_json::text,
                           w.display_configuration_json::text,
                           w.visibility_condition_json::text, w.position_json::text
                      from dashboard_widget w
                      join ontology_concept c on c.id = w.widget_type_concept_id
                     where w.dashboard_version_id = ? order by w.created_at
                    """, (widgets, row) -> Map.of(
                        "code", widgets.getString("widget_code"),
                        "typeConceptCode", widgets.getString("concept_code"),
                        "dataSource", parse(widgets.getString(3)),
                        "display", parse(widgets.getString(4)),
                        "visibilityCondition", parse(widgets.getString(5)),
                        "position", parse(widgets.getString(6))), versionId)));
                return dashboard;
            }, code, tenant);
    }

    private JsonNode parse(String value) {
        try {
            return mapper.readTree(value == null ? "{}" : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored UI configuration is invalid", exception);
        }
    }
}
