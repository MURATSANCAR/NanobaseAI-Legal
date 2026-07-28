package com.nanobase.specai.analysis.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRevisionRepository
    extends JpaRepository<RequirementRevision, UUID> {
    List<RequirementRevision>
        findAllByRequirementIdAndOrganizationIdOrderByRevisionNumber(
            UUID requirementId, UUID organizationId);
    long countByRequirementIdAndOrganizationId(UUID requirementId, UUID organizationId);
}
