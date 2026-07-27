package com.nanobase.specai.document.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    Optional<DocumentVersion> findByDocumentIdAndTenantIdAndVersionNumber(
        UUID documentId, UUID tenantId, int versionNumber);
}
