package com.nanobase.specai.shared.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantDatabaseContextTest {
    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void requiresTransactionAndUsesVerifiedOrganizationSetting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantDatabaseContext context = new TenantDatabaseContext(jdbc);
        UUID organizationId = UUID.randomUUID();
        assertThatThrownBy(() -> context.apply(organizationId))
            .isInstanceOf(IllegalStateException.class);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        context.apply(organizationId);

        verify(jdbc).queryForObject(
            eq("select set_config('app.current_organization_id', ?, true)"),
            eq(String.class), eq(organizationId.toString()));
    }
}
