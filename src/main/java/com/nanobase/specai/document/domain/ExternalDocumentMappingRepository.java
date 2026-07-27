package com.nanobase.specai.document.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalDocumentMappingRepository
    extends JpaRepository<ExternalDocumentMapping, UUID> {
    Optional<ExternalDocumentMapping> findByDocumentVersionIdAndProviderAndOrganizationId(
        UUID documentVersionId, ExternalDocumentMapping.Provider provider, UUID organizationId);
}
