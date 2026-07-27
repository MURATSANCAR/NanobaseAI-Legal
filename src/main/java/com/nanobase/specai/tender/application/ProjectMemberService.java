package com.nanobase.specai.tender.application;

import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.api.TenderContracts.AddProjectMemberRequest;
import com.nanobase.specai.tender.api.TenderContracts.ProjectMemberResponse;
import com.nanobase.specai.tender.api.TenderContracts.UpdateProjectMemberRequest;
import com.nanobase.specai.tender.domain.ProjectMember;
import com.nanobase.specai.tender.domain.ProjectMemberRepository;
import com.nanobase.specai.tender.domain.ProjectRole;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectMemberService {
    private final ProjectMemberRepository members;
    private final ProjectAccessService access;
    private final CurrentTenant currentTenant;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    public ProjectMemberService(ProjectMemberRepository members, ProjectAccessService access,
                                CurrentTenant currentTenant, AuditService audit) {
        this.members = members;
        this.access = access;
        this.currentTenant = currentTenant;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(UUID projectId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireView(projectId, principal);
        return members.findAllByProjectIdAndOrganizationIdOrderByCreatedAt(
            projectId, principal.tenantId()).stream().map(ProjectMemberResponse::from).toList();
    }

    @Transactional
    public ProjectMemberResponse add(UUID projectId, AddProjectMemberRequest request) {
        TenantPrincipal principal = currentTenant.require();
        access.requireManageMembers(projectId, principal);
        ProjectMember member = new ProjectMember(UUID.randomUUID(), principal.tenantId(), projectId,
            request.userId(), request.projectRole(), request.canViewDocuments(),
            request.canUploadDocuments(), request.canManageMembers(),
            request.canArchiveProject(), clock.instant());
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalArgumentException("User is already a member of this project");
        }
        audit.record("PROJECT_MEMBER_ADDED", "TenderProject", projectId, null,
            ProjectMemberResponse.from(member));
        return ProjectMemberResponse.from(member);
    }

    @Transactional
    public ProjectMemberResponse update(UUID projectId, UUID memberId,
                                        UpdateProjectMemberRequest request) {
        TenantPrincipal principal = currentTenant.require();
        access.requireManageMembers(projectId, principal);
        ProjectMember member = member(memberId, projectId, principal);
        ProjectMemberResponse before = ProjectMemberResponse.from(member);
        member.change(request.projectRole(), request.canViewDocuments(),
            request.canUploadDocuments(), request.canManageMembers(),
            request.canArchiveProject());
        ProjectMemberResponse after = ProjectMemberResponse.from(member);
        audit.record("PROJECT_MEMBER_UPDATED", "TenderProject", projectId, before, after);
        return after;
    }

    @Transactional
    public void remove(UUID projectId, UUID memberId) {
        TenantPrincipal principal = currentTenant.require();
        access.requireManageMembers(projectId, principal);
        ProjectMember member = member(memberId, projectId, principal);
        if (member.projectRole() == ProjectRole.OWNER) {
            throw new IllegalArgumentException("Project owner cannot be removed");
        }
        members.delete(member);
        audit.record("PROJECT_MEMBER_REMOVED", "TenderProject", projectId,
            ProjectMemberResponse.from(member), Map.of());
    }

    private ProjectMember member(UUID memberId, UUID projectId, TenantPrincipal principal) {
        return members.findByIdAndProjectIdAndOrganizationId(
            memberId, projectId, principal.tenantId())
            .orElseThrow(ProjectAccessDeniedException::new);
    }
}
