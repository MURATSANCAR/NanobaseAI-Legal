package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClauseRepository extends JpaRepository<Clause, UUID> {
    List<Clause> findAllByDocumentVersionIdAndTenantIdOrderBySortOrder(UUID versionId, UUID tenantId);
    void deleteAllByDocumentVersionIdAndTenantId(UUID versionId, UUID tenantId);
}
