package com.nanobase.specai.document.application;

import org.springframework.beans.factory.annotation.Autowired;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.document.application.ObjectStorage.StoredObject;
import com.nanobase.specai.document.domain.DocumentVersionRepository;
import com.nanobase.specai.shared.observability.PlatformMetrics;
import com.nanobase.specai.shared.security.TenantDatabaseContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrphanObjectReconciliationService {
    static final String TEMPORARY_PREFIX = "specai-temp/";

    private final ObjectStorage storage;
    private final DocumentVersionRepository versions;
    private final AuditService audit;
    private final TenantDatabaseContext tenantContext;
    private final PlatformMetrics metrics;
    private final TransactionTemplate transactions;
    private final Duration gracePeriod;
    private final Clock clock;

    @Autowired
    public OrphanObjectReconciliationService(
        ObjectStorage storage,
        DocumentVersionRepository versions,
        AuditService audit,
        TenantDatabaseContext tenantContext,
        PlatformMetrics metrics,
        PlatformTransactionManager transactionManager,
        @Value("${specai.storage.orphan-grace-period:PT6H}") Duration gracePeriod) {
        this(storage, versions, audit, tenantContext, metrics,
            new TransactionTemplate(transactionManager), gracePeriod, Clock.systemUTC());
    }

    OrphanObjectReconciliationService(
        ObjectStorage storage, DocumentVersionRepository versions, AuditService audit,
        TenantDatabaseContext tenantContext, PlatformMetrics metrics,
        TransactionTemplate transactions, Duration gracePeriod, Clock clock) {
        this.storage = storage;
        this.versions = versions;
        this.audit = audit;
        this.tenantContext = tenantContext;
        this.metrics = metrics;
        this.transactions = transactions;
        this.gracePeriod = gracePeriod;
        this.clock = clock;
    }

    @Scheduled(cron = "${specai.storage.orphan-reconciliation-cron:0 17 * * * *}")
    public void reconcile() {
        Instant cutoff = clock.instant().minus(gracePeriod);
        storage.list(TEMPORARY_PREFIX).stream()
            .filter(object -> eligible(object, cutoff))
            .forEach(this::reconcileObject);
    }

    private void reconcileObject(StoredObject object) {
        Optional<UUID> organizationId = organizationId(object.objectKey());
        if (organizationId.isEmpty()) {
            return;
        }
        metrics.orphanDetected();
        transactions.executeWithoutResult(ignored -> {
            UUID tenant = organizationId.orElseThrow();
            tenantContext.apply(tenant);
            if (versions.existsByObjectStorageKeyAndOrganizationId(
                object.objectKey(), tenant)) {
                return;
            }
            try {
                storage.delete(object.objectKey());
                metrics.orphanDeleted();
                audit.recordSystem(tenant, "orphan-reconciler",
                    "ORPHAN_OBJECT_DELETED", "ObjectStorage", UUID.randomUUID(), null,
                    Map.of("objectKey", object.objectKey(), "size", object.size()));
            } catch (RuntimeException exception) {
                audit.recordSystem(tenant, "orphan-reconciler",
                    "ORPHAN_OBJECT_DELETE_FAILED", "ObjectStorage", UUID.randomUUID(), null,
                    Map.of("objectKey", object.objectKey(),
                        "errorCode", "OBJECT_DELETE_FAILED"));
            }
        });
    }

    static Optional<UUID> organizationId(String objectKey) {
        String[] segments = objectKey.split("/", 4);
        if (segments.length < 3 || !"specai-temp".equals(segments[0])) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(segments[1]));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static boolean eligible(StoredObject object, Instant cutoff) {
        return object != null && object.objectKey().startsWith(TEMPORARY_PREFIX)
            && object.lastModified().isBefore(cutoff);
    }
}
