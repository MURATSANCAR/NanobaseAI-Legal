package com.nanobase.specai.analysis.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementExtractionJobRepository
    extends JpaRepository<RequirementExtractionJob, UUID> {
    Optional<RequirementExtractionJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<RequirementExtractionJob>
        findAllByDocumentIdAndOrganizationIdOrderByCreatedAtDesc(UUID documentId,
                                                                  UUID organizationId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select job from RequirementExtractionJob job
        where job.id = :id and job.organizationId = :organizationId
        """)
    Optional<RequirementExtractionJob> findForUpdate(@Param("id") UUID id,
                                                      @Param("organizationId") UUID organizationId);
}
