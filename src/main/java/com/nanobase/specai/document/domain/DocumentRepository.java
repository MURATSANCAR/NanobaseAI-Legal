package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByTenderProjectIdAndTenantIdOrderByCreatedAtDesc(UUID projectId, UUID tenantId);
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);
}
