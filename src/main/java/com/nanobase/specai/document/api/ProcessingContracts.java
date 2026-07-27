package com.nanobase.specai.document.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class ProcessingContracts {
    private ProcessingContracts() {}

    public record ClauseInput(
        @NotNull UUID id,
        UUID parentId,
        @NotBlank @Size(max = 100) String number,
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Size(max = 100_000) String sourceText,
        @Min(1) int pageNumber,
        @Min(0) int sortOrder
    ) {}

    public record ProcessingResult(
        @NotNull UUID tenantId,
        @NotNull UUID documentVersionId,
        @NotBlank String status,
        @Size(max = 2000) String message,
        @Size(max = 10_000) List<@Valid ClauseInput> clauses
    ) {
        public ProcessingResult {
            clauses = clauses == null ? List.of() : List.copyOf(clauses);
        }
    }
}
