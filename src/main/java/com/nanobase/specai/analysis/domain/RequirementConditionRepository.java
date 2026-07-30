package com.nanobase.specai.analysis.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementConditionRepository extends JpaRepository<RequirementCondition, UUID> {
    java.util.List<RequirementCondition> findByOrganizationIdAndRequirementIdOrderBySequenceNoAsc(
        UUID organizationId, UUID requirementId);
}
