package com.nanobase.specai.operations.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private final JdbcTemplate jdbc;

    public FeatureFlagService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public boolean enabled(UUID organizationId, UUID projectId, String featureCode) {
        Boolean value = jdbc.queryForObject("""
            select coalesce(
                (
                    select assignment.enabled
                      from feature_assignment assignment
                      join feature_definition definition
                        on definition.id = assignment.feature_definition_id
                     where assignment.organization_id = ?
                       and definition.feature_code = ?
                       and (assignment.valid_from is null or assignment.valid_from <= now())
                       and (assignment.valid_until is null or assignment.valid_until > now())
                       and (assignment.project_id = ? or assignment.project_id is null)
                     order by (assignment.project_id is not null) desc,
                              assignment.updated_at desc
                     limit 1
                ),
                (
                    select default_state from feature_definition
                     where feature_code = ?
                ),
                false
            )
            """, Boolean.class, organizationId, featureCode, projectId, featureCode);
        return Boolean.TRUE.equals(value);
    }
}
