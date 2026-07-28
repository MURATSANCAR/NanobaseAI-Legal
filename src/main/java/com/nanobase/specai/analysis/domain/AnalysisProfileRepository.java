package com.nanobase.specai.analysis.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisProfileRepository extends JpaRepository<AnalysisProfile, UUID> {
    Optional<AnalysisProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<AnalysisProfile> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
