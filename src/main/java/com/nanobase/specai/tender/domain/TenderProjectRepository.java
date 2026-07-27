package com.nanobase.specai.tender.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenderProjectRepository extends JpaRepository<TenderProject, UUID> {
    Optional<TenderProject> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Page<TenderProject> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    @Query("""
        select project from TenderProject project
        where project.organizationId = :organizationId
          and exists (
            select member.id from ProjectMember member
            where member.projectId = project.id
              and member.organizationId = :organizationId
              and member.userId = :userId
          )
        """)
    Page<TenderProject> findAccessible(@Param("organizationId") UUID organizationId,
                                       @Param("userId") String userId,
                                       Pageable pageable);
}
