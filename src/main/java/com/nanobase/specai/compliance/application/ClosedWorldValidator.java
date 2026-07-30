package com.nanobase.specai.compliance.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closed-world may only be applied when a system-confirmed scope declaration exists.
 * Models cannot invent closedWorldApplied=true.
 */
@Service
public class ClosedWorldValidator {
    private final JdbcTemplate jdbc;

    public ClosedWorldValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public boolean hasActiveDeclaration(UUID organizationId, UUID projectId,
                                        String capabilityType) {
        Integer count = jdbc.queryForObject("""
            select count(*) from evidence_scope_declaration
             where organization_id = ?
               and active = true
               and (project_id is null or project_id = ?)
               and (applies_to_capability_type is null
                    or applies_to_capability_type = ?)
               and source in ('USER_CONFIRMED', 'DOCUMENT_DECLARATION',
                              'BUSINESS_RULE', 'EXPERT_CONFIRMED')
            """, Integer.class, organizationId, projectId, capabilityType);
        return count != null && count > 0;
    }

    public boolean acceptClosedWorldClaim(boolean modelClaimedClosedWorld,
                                          boolean systemHasDeclaration) {
        return modelClaimedClosedWorld && systemHasDeclaration;
    }
}
