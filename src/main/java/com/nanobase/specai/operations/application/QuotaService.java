package com.nanobase.specai.operations.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {
    private final JdbcTemplate jdbc;

    public QuotaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public void requireAdditionalStorage(UUID organizationId, UUID projectId, long bytes) {
        require("STORAGE_BYTES", organizationId, projectId, storageUsage(organizationId, projectId),
            bytes);
        require("DOCUMENT_COUNT", organizationId, projectId,
            documentCount(organizationId, projectId), 1);
    }

    private void require(String quotaCode, UUID organizationId, UUID projectId,
                         long currentUsage, long requestedIncrement) {
        Long limit = jdbc.queryForObject("""
            select coalesce(
                (
                    select assignment.limit_value
                      from quota_assignment assignment
                      join quota_definition definition
                        on definition.id = assignment.quota_definition_id
                     where assignment.organization_id = ?
                       and definition.quota_code = ?
                       and (assignment.valid_from is null or assignment.valid_from <= now())
                       and (assignment.valid_until is null or assignment.valid_until > now())
                       and (assignment.project_id = ? or assignment.project_id is null)
                     order by (assignment.project_id is not null) desc,
                              assignment.updated_at desc
                     limit 1
                ),
                (select default_limit from quota_definition where quota_code = ?)
            )
            """, Long.class, organizationId, quotaCode, projectId, quotaCode);
        long resolvedLimit = limit == null ? 0 : limit;
        long requestedUsage;
        try {
            requestedUsage = Math.addExact(currentUsage, requestedIncrement);
        } catch (ArithmeticException overflow) {
            throw new ResourceQuotaExceededException(quotaCode, resolvedLimit, Long.MAX_VALUE);
        }
        if (requestedUsage > resolvedLimit) {
            throw new ResourceQuotaExceededException(quotaCode, resolvedLimit, requestedUsage);
        }
    }

    private long storageUsage(UUID organizationId, UUID projectId) {
        Long value = jdbc.queryForObject("""
            select coalesce(sum(version.file_size), 0)
              from document_version version
              join document document on document.id = version.document_id
             where version.organization_id = ? and document.project_id = ?
            """, Long.class, organizationId, projectId);
        return value == null ? 0 : value;
    }

    private long documentCount(UUID organizationId, UUID projectId) {
        Long value = jdbc.queryForObject("""
            select count(*) from document
             where organization_id = ? and project_id = ?
            """, Long.class, organizationId, projectId);
        return value == null ? 0 : value;
    }
}
