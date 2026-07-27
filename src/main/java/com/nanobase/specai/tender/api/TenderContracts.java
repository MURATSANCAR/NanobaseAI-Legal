package com.nanobase.specai.tender.api;

import com.nanobase.specai.tender.domain.Priority;
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
        @NotBlank @Size(max = 200) String contractingAuthority,
        @Size(max = 100) String registrationNumber,
        @FutureOrPresent LocalDate deadline,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Priority priority,
        @Size(max = 4000) String description
    ) {
    }

    public record UpdateTenderRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String contractingAuthority,
        @Size(max = 100) String registrationNumber,
        @FutureOrPresent LocalDate deadline,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Priority priority,
        @Size(max = 4000) String description
    ) {
    }

    public record TenderResponse(
        UUID id,
        String code,
        String name,
        String contractingAuthority,
        String registrationNumber,
        LocalDate deadline,
        String currency,
        Priority priority,
        TenderStatus status,
        String description,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
        public static TenderResponse from(TenderProject project) {
            return new TenderResponse(project.id(), project.projectCode(), project.name(),
                project.institutionName(), project.tenderRegistrationNumber(), project.bidDeadline(),
                project.currency(), project.priority(), project.status(), project.description(),
                project.createdAt(), project.updatedAt(), project.version());
        }
    }
}
