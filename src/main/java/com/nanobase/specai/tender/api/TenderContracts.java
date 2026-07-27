package com.nanobase.specai.tender.api;

import com.nanobase.specai.tender.domain.Priority;
import com.nanobase.specai.tender.domain.ProjectMember;
import com.nanobase.specai.tender.domain.ProjectRole;
import com.nanobase.specai.tender.domain.TenderProject;
import com.nanobase.specai.tender.domain.TenderStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TenderContracts {
    private TenderContracts() {
    }

    public record CreateTenderRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String institutionName,
        @Size(max = 100) String tenderRegistrationNumber,
        @Size(max = 80) String tenderType,
        @Size(max = 80) String businessType,
        @Size(max = 120) String sector,
        @NotNull Priority priority,
        @FutureOrPresent LocalDate bidDeadline,
        @FutureOrPresent LocalDate clarificationDeadline,
        @Size(max = 4000) String description,
        @Pattern(regexp = "^[A-Z]{3}$") String currency
    ) {
    }

    public record UpdateTenderRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String institutionName,
        @Size(max = 100) String tenderRegistrationNumber,
        @Size(max = 80) String tenderType,
        @Size(max = 80) String businessType,
        @Size(max = 120) String sector,
        @NotNull Priority priority,
        @FutureOrPresent LocalDate bidDeadline,
        @FutureOrPresent LocalDate clarificationDeadline,
        @Size(max = 4000) String description,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        Long version
    ) {
    }

    public record TenderResponse(
        UUID id,
        String projectCode,
        String name,
        String institutionName,
        String tenderRegistrationNumber,
        String tenderType,
        String businessType,
        String sector,
        Priority priority,
        TenderStatus status,
        LocalDate bidDeadline,
        LocalDate clarificationDeadline,
        String description,
        String currency,
        String ownerUserId,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
        public static TenderResponse from(TenderProject project) {
            return new TenderResponse(project.id(), project.projectCode(), project.name(),
                project.institutionName(), project.tenderRegistrationNumber(), project.tenderType(),
                project.businessType(), project.sector(), project.priority(), project.status(),
                project.bidDeadline(), project.clarificationDeadline(), project.description(),
                project.currency(), project.ownerUserId(), project.createdAt(), project.updatedAt(),
                project.version());
        }
    }

    public record AddProjectMemberRequest(
        @NotBlank @Size(max = 255) String userId,
        @NotNull ProjectRole projectRole,
        boolean canViewDocuments,
        boolean canUploadDocuments,
        boolean canManageMembers,
        boolean canArchiveProject
    ) {
    }

    public record UpdateProjectMemberRequest(
        @NotNull ProjectRole projectRole,
        boolean canViewDocuments,
        boolean canUploadDocuments,
        boolean canManageMembers,
        boolean canArchiveProject
    ) {
    }

    public record ProjectMemberResponse(
        UUID id,
        String userId,
        ProjectRole projectRole,
        boolean canViewDocuments,
        boolean canUploadDocuments,
        boolean canManageMembers,
        boolean canArchiveProject,
        Instant createdAt
    ) {
        public static ProjectMemberResponse from(ProjectMember member) {
            return new ProjectMemberResponse(member.id(), member.userId(), member.projectRole(),
                member.canViewDocuments(), member.canUploadDocuments(),
                member.canManageMembers(), member.canArchiveProject(), member.createdAt());
        }
    }
}
