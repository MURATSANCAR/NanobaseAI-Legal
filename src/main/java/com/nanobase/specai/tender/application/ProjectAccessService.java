package com.nanobase.specai.tender.application;

import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.tender.domain.ProjectMember;
import com.nanobase.specai.tender.domain.ProjectMemberRepository;
import com.nanobase.specai.tender.domain.TenderProject;
import com.nanobase.specai.tender.domain.TenderProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectAccessService {
    private final TenderProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectAccessService(TenderProjectRepository projects, ProjectMemberRepository members) {
        this.projects = projects;
        this.members = members;
    }

    public TenderProject requireView(UUID projectId, TenantPrincipal principal) {
        TenderProject project = scopedProject(projectId, principal);
        if (!tenantWide(principal) && membership(projectId, principal).isEmpty()) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    public TenderProject requireDocumentView(UUID projectId, TenantPrincipal principal) {
        TenderProject project = scopedProject(projectId, principal);
        if (!tenantWide(principal) && membership(projectId, principal)
            .filter(ProjectMember::canViewDocuments).isEmpty()) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    public TenderProject requireUpload(UUID projectId, TenantPrincipal principal) {
        TenderProject project = scopedProject(projectId, principal);
        if (!tenantWide(principal) && membership(projectId, principal)
            .filter(ProjectMember::canUploadDocuments).isEmpty()) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    public TenderProject requireManageMembers(UUID projectId, TenantPrincipal principal) {
        TenderProject project = scopedProject(projectId, principal);
        if (!tenantWide(principal) && membership(projectId, principal)
            .filter(ProjectMember::canManageMembers).isEmpty()) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    public TenderProject requireArchive(UUID projectId, TenantPrincipal principal) {
        TenderProject project = scopedProject(projectId, principal);
        if (!tenantWide(principal) && membership(projectId, principal)
            .filter(ProjectMember::canArchiveProject).isEmpty()) {
            throw new ProjectAccessDeniedException();
        }
        return project;
    }

    public boolean tenantWide(TenantPrincipal principal) {
        return principal.roles().contains("SYSTEM_ADMIN")
            || principal.roles().contains("TENANT_ADMIN");
    }

    private TenderProject scopedProject(UUID projectId, TenantPrincipal principal) {
        return projects.findByIdAndOrganizationId(projectId, principal.tenantId())
            .orElseThrow(() -> new TenderNotFoundException(projectId));
    }

    private java.util.Optional<ProjectMember> membership(
        UUID projectId, TenantPrincipal principal) {
        return members.findByProjectIdAndUserIdAndOrganizationId(
            projectId, principal.subject(), principal.tenantId());
    }
}
