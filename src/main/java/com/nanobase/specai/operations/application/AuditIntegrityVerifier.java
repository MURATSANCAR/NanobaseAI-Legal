package com.nanobase.specai.operations.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AuditIntegrityVerifier {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AtomicInteger failures = new AtomicInteger();

    public AuditIntegrityVerifier(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                  MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        meterRegistry.gauge("audit_integrity_failure_total", failures);
    }

    @Scheduled(fixedDelayString = "${specai.audit.integrity-interval-ms:3600000}")
    public void verify() {
        List<UUID> organizations = jdbc.queryForList("select id from organization", UUID.class);
        int invalid = 0;
        for (UUID organizationId : organizations) {
            Boolean valid = transactions.execute(ignored -> {
                jdbc.queryForObject(
                    "select set_config('app.current_organization_id', ?, true)",
                    String.class, organizationId.toString());
                return jdbc.queryForObject(
                    "select valid from verify_audit_chain(?)",
                    Boolean.class, organizationId);
            });
            if (!Boolean.TRUE.equals(valid)) {
                invalid++;
            }
        }
        failures.set(invalid);
    }
}
