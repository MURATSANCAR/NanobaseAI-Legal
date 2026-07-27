package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByProjectIdAndOrganizationIdOrderByCreatedAtDesc(
        UUID projectId, UUID organizationId);
    Optional<Document> findByIdAndOrganizationId(UUID id, UUID organizationId);
    long countByOrganizationIdAndStatus(UUID organizationId, DocumentStatus status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from Document document where document.id = :id and document.organizationId = :organizationId")
    Optional<Document> findForUpdate(@Param("id") UUID id,
                                     @Param("organizationId") UUID organizationId);
}
