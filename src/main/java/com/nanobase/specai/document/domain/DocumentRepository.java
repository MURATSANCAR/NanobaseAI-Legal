package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByProjectIdAndOrganizationIdOrderByCreatedAtDesc(
        UUID projectId, UUID organizationId);
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);
    long countByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status);
}
