package com.nanobase.specai.tender.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_member")
public class ProjectMember {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "user_id", nullable = false, updatable = false, length = 255)
    private String userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 40)
    private ProjectRole projectRole;
    @Column(name = "can_view_documents", nullable = false)
    private boolean canViewDocuments;
    @Column(name = "can_upload_documents", nullable = false)
    private boolean canUploadDocuments;
    @Column(name = "can_manage_members", nullable = false)
    private boolean canManageMembers;
    @Column(name = "can_archive_project", nullable = false)
    private boolean canArchiveProject;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectMember() {
    }

    public static ProjectMember owner(UUID organizationId, UUID projectId, String userId, Instant now) {
        return new ProjectMember(UUID.randomUUID(), organizationId, projectId, userId,
            ProjectRole.OWNER, true, true, true, true, now);
    }

    public ProjectMember(UUID id, UUID organizationId, UUID projectId, String userId,
                         ProjectRole projectRole, boolean canViewDocuments,
                         boolean canUploadDocuments, boolean canManageMembers,
                         boolean canArchiveProject, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.projectId = projectId;
        this.userId = userId;
        this.projectRole = projectRole;
        this.canViewDocuments = canViewDocuments;
        this.canUploadDocuments = canUploadDocuments;
        this.canManageMembers = canManageMembers;
        this.canArchiveProject = canArchiveProject;
        this.createdAt = createdAt;
    }

    public void change(ProjectRole role, boolean view, boolean upload, boolean manage, boolean archive) {
        if (projectRole == ProjectRole.OWNER && role != ProjectRole.OWNER) {
            throw new IllegalArgumentException("Project owner role cannot be changed");
        }
        this.projectRole = role;
        this.canViewDocuments = view;
        this.canUploadDocuments = upload;
        this.canManageMembers = manage;
        this.canArchiveProject = archive;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID projectId() { return projectId; }
    public String userId() { return userId; }
    public ProjectRole projectRole() { return projectRole; }
    public boolean canViewDocuments() { return canViewDocuments; }
    public boolean canUploadDocuments() { return canUploadDocuments; }
    public boolean canManageMembers() { return canManageMembers; }
    public boolean canArchiveProject() { return canArchiveProject; }
    public Instant createdAt() { return createdAt; }
}
