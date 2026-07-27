package com.nanobase.specai.shared.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TenantDatabaseContext {
    private final JdbcTemplate jdbc;

    public TenantDatabaseContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void apply(UUID organizationId) {
        if (organizationId == null) {
            throw new MissingTenantException("Database tenant context is required");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                "Tenant database context must be set inside a transaction");
        }
        jdbc.queryForObject("select set_config('app.current_organization_id', ?, true)",
            String.class, organizationId.toString());
    }
}
