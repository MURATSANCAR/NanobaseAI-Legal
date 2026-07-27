package com.nanobase.specai.document.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, UUID> {
    Page<DocumentPage> findAllByDocumentVersionIdAndOrganizationId(
        UUID versionId, UUID organizationId, Pageable pageable);
    Optional<DocumentPage>
        findByDocumentVersionIdAndOrganizationIdAndPageNumber(
            UUID versionId, UUID organizationId, int pageNumber);
    void deleteAllByDocumentVersionIdAndOrganizationId(UUID versionId, UUID organizationId);
}
