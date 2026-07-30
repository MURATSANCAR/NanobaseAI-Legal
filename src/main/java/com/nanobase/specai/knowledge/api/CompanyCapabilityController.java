package com.nanobase.specai.knowledge.api;

import com.nanobase.specai.shared.security.CurrentTenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/company-capabilities")
public class CompanyCapabilityController {
    private final JdbcTemplate jdbc;
    private final CurrentTenant currentTenant;

    public CompanyCapabilityController(JdbcTemplate jdbc, CurrentTenant currentTenant) {
        this.jdbc = jdbc;
        this.currentTenant = currentTenant;
    }

    @GetMapping
    List<Map<String, Object>> list(
        @RequestParam(required = false) String capabilityType,
        @RequestParam(required = false) String verificationStatus,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        var principal = currentTenant.require();
        int limit = Math.min(Math.max(size, 1), 200);
        int offset = Math.max(page, 0) * limit;
        if (capabilityType != null && verificationStatus != null) {
            return jdbc.queryForList("""
                select id, owner_entity_id, capability_type, name, normalized_name, value_type,
                       text_value, numeric_value, unit, boolean_value, date_value, scope_text,
                       status, verification_status, valid_from, valid_until, confidence,
                       source_document_id, verified_by, verified_at, created_at, updated_at
                  from capability
                 where organization_id = ?
                   and capability_type = ?
                   and verification_status = ?
                 order by updated_at desc
                 limit ? offset ?
                """, principal.tenantId(), capabilityType, verificationStatus, limit, offset);
        }
        if (capabilityType != null) {
            return jdbc.queryForList("""
                select id, owner_entity_id, capability_type, name, normalized_name, value_type,
                       text_value, numeric_value, unit, boolean_value, date_value, scope_text,
                       status, verification_status, valid_from, valid_until, confidence,
                       source_document_id, verified_by, verified_at, created_at, updated_at
                  from capability
                 where organization_id = ?
                   and capability_type = ?
                 order by updated_at desc
                 limit ? offset ?
                """, principal.tenantId(), capabilityType, limit, offset);
        }
        return jdbc.queryForList("""
            select id, owner_entity_id, capability_type, name, normalized_name, value_type,
                   text_value, numeric_value, unit, boolean_value, date_value, scope_text,
                   status, verification_status, valid_from, valid_until, confidence,
                   source_document_id, verified_by, verified_at, created_at, updated_at
              from capability
             where organization_id = ?
             order by updated_at desc
             limit ? offset ?
            """, principal.tenantId(), limit, offset);
    }
}
