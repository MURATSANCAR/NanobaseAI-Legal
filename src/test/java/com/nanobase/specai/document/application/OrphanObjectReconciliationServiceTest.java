package com.nanobase.specai.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nanobase.specai.document.application.ObjectStorage.StoredObject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrphanObjectReconciliationServiceTest {
    @Test
    void onlyOldTemporaryTenantObjectsAreEligible() {
        UUID organizationId = UUID.randomUUID();
        StoredObject old = new StoredObject(
            "specai-temp/" + organizationId + "/" + UUID.randomUUID() + "/spec.pdf",
            100, Instant.parse("2026-07-27T00:00:00Z"));
        StoredObject recent = new StoredObject(old.objectKey(), 100,
            Instant.parse("2026-07-28T00:00:00Z"));
        Instant cutoff = Instant.parse("2026-07-27T12:00:00Z");

        assertThat(OrphanObjectReconciliationService.eligible(old, cutoff)).isTrue();
        assertThat(OrphanObjectReconciliationService.eligible(recent, cutoff)).isFalse();
        assertThat(OrphanObjectReconciliationService.organizationId(old.objectKey()))
            .contains(organizationId);
    }
}
