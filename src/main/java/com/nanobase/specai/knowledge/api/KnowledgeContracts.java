package com.nanobase.specai.knowledge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.AttributeView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.CapabilityView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.DynamicValue;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.RelationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeContracts {
    private KnowledgeContracts() {
    }

    public record CreateEntityRequest(
        @NotBlank @Size(max = 240) String entityCode,
        @NotNull UUID entityTypeConceptId,
        @NotBlank @Size(max = 500) String name,
        String description,
        @NotBlank @Size(max = 80) String status,
        Instant validFrom,
        Instant validUntil,
        JsonNode attributes,
        @NotBlank @Size(max = 160) String sourceType,
        UUID sourceReferenceId
    ) {
    }

    public record UpdateEntityRequest(
        @NotNull UUID entityTypeConceptId,
        @NotBlank @Size(max = 500) String name,
        String description,
        @NotBlank @Size(max = 80) String status,
        Instant validFrom,
        Instant validUntil,
        JsonNode attributes,
        long version,
        @NotNull UUID changeTypeConceptId
    ) {
    }

    public record AttributeRequest(
        @NotNull UUID attributeConceptId,
        @NotNull @Valid DynamicValue value,
        Instant validFrom,
        Instant validUntil,
        @DecimalMin("0") @DecimalMax("1") double confidence,
        @NotNull UUID sourceFragmentId,
        @NotBlank @Size(max = 80) String reviewStatus
    ) {
    }

    public record RelationRequest(
        @NotNull UUID sourceEntityId,
        @NotNull UUID targetEntityId,
        @NotNull UUID relationConceptId,
        JsonNode attributes,
        @DecimalMin("0") @DecimalMax("1") double confidence,
        Instant validFrom,
        Instant validUntil,
        @NotNull UUID sourceFragmentId,
        @NotBlank @Size(max = 80) String reviewStatus
    ) {
    }

    public record CapabilityEvidenceRequest(
        @NotNull UUID evidenceFragmentId,
        @NotNull UUID evidenceRoleConceptId,
        @DecimalMin("0") @DecimalMax("1") double strength,
        Instant validFrom,
        Instant validUntil
    ) {
    }

    public record CapabilityRequest(
        @NotNull UUID capabilityConceptId,
        @NotBlank @Size(max = 500) String name,
        String description,
        JsonNode attributes,
        Instant validFrom,
        Instant validUntil,
        @NotBlank @Size(max = 80) String status,
        @DecimalMin("0") @DecimalMax("1") double confidence,
        @NotBlank @Size(max = 80) String reviewStatus,
        List<@Valid CapabilityEvidenceRequest> evidence
    ) {
    }

    public record MergeRequest(
        @NotEmpty List<UUID> sourceEntityIds,
        @NotNull UUID changeTypeConceptId
    ) {
    }

    public record SplitEntityRequest(
        @Valid @NotNull CreateEntityRequest entity,
        List<UUID> attributeIds,
        List<UUID> capabilityIds,
        List<UUID> outgoingRelationIds,
        List<UUID> incomingRelationIds
    ) {
    }

    public record SplitRequest(
        @NotEmpty List<@Valid SplitEntityRequest> entities,
        @NotNull UUID changeTypeConceptId
    ) {
    }

    public record EntityDetailResponse(
        EntityView entity,
        List<AttributeView> attributes,
        List<RelationView> relations,
        List<CapabilityView> capabilities,
        List<Map<String, Object>> evidence,
        List<Map<String, Object>> revisions
    ) {
    }

    public record EvidenceAssessmentRequest(
        @NotNull UUID policyVersionId,
        @NotNull UUID statusConceptId,
        @DecimalMin("0") @DecimalMax("1") double score,
        JsonNode factors,
        @NotBlank @Size(max = 120) String assessedByType
    ) {
    }
}
