package com.nanobase.specai.document.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTableRepository extends JpaRepository<DocumentTable, UUID> {
    Page<DocumentTable> findAllByDocumentVersionIdAndOrganizationId(
        UUID versionId, UUID organizationId, Pageable pageable);
    void deleteAllByDocumentVersionIdAndOrganizationId(UUID versionId, UUID organizationId);
}
