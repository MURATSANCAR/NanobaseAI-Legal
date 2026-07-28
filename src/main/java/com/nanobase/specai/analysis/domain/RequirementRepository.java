package com.nanobase.specai.analysis.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, UUID> {
    Optional<Requirement> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Page<Requirement> findAllByProjectIdAndOrganizationId(UUID projectId, UUID organizationId,
                                                           Pageable pageable);
    List<Requirement> findAllByExtractionJobIdAndOrganizationId(UUID jobId,
                                                                 UUID organizationId);
    boolean existsByExtractionJobIdAndSourceClauseIdAndRequirementCode(
        UUID extractionJobId, UUID sourceClauseId, String requirementCode);
}
