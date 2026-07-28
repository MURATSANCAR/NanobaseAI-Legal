package com.nanobase.specai.compliance.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class ComplianceContracts {
    private ComplianceContracts() {
    }

    public record StartComplianceRequest(JsonNode targetScope) {
    }

    public record ReviewRequest(
        @NotNull UUID finalDecisionConceptId,
        @NotNull UUID changeTypeConceptId,
        UUID feedbackTypeConceptId,
        @NotBlank String reason
    ) {
    }

    public record EvidenceLinkRequest(
        @NotNull UUID evidenceFragmentId,
        UUID evidenceClaimId,
        @NotNull UUID relationRoleConceptId,
        @DecimalMin("0") @DecimalMax("1") double relevanceScore,
        @DecimalMin("0") @DecimalMax("1") double validityScore,
        @DecimalMin("0") @DecimalMax("1") double supportStrength,
        @DecimalMin("0") @DecimalMax("1") double contradictionStrength,
        @NotBlank String selectedByType
    ) {
    }
}
