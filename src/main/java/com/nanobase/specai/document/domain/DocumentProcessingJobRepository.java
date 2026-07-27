package com.nanobase.specai.document.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentProcessingJobRepository
    extends JpaRepository<DocumentProcessingJob, UUID> {
    Optional<DocumentProcessingJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<DocumentProcessingJob> findAllByDocumentIdAndOrganizationIdOrderByCreatedAtDesc(
        UUID documentId, UUID organizationId);
    Page<DocumentProcessingJob> findAllByDocumentIdAndOrganizationId(
        UUID documentId, UUID organizationId, Pageable pageable);

    @Lock(jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("""
        select job from DocumentProcessingJob job
        where job.id = :id and job.organizationId = :organizationId
        """)
    Optional<DocumentProcessingJob> findForUpdate(
        @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
