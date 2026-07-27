package com.nanobase.specai.tender.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    Optional<ProjectMember> findByProjectIdAndUserIdAndOrganizationId(
        UUID projectId, String userId, UUID organizationId);
    Optional<ProjectMember> findByIdAndProjectIdAndOrganizationId(
        UUID memberId, UUID projectId, UUID organizationId);
    List<ProjectMember> findAllByProjectIdAndOrganizationIdOrderByCreatedAt(
        UUID projectId, UUID organizationId);
}
