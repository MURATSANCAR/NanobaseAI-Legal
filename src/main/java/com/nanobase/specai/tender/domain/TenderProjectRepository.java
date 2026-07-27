package com.nanobase.specai.tender.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenderProjectRepository extends JpaRepository<TenderProject, UUID> {
    Optional<TenderProject> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<TenderProject> findAllByTenantId(UUID tenantId, Pageable pageable);
}
