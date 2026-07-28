package com.nanobase.specai.knowledge.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transport-neutral dynamic knowledge graph values. Business classifications are UUID
 * ontology references or string catalog keys; they are intentionally not Java enums.
 */
public final class KnowledgeModels {
    private KnowledgeModels() {
    }

    public record DynamicValue(
        String type,
        String textValue,
        BigDecimal numericValue,
        BigDecimal numericValueEnd,
        Boolean booleanValue,
        Instant dateValue,
        JsonNode jsonValue,
        UUID unitConceptId,
        Map<String, Object> unsupportedMetadata
    ) {
        public boolean unsupported() {
            return unsupportedMetadata != null && !unsupportedMetadata.isEmpty();
        }
    }

    public record EntityView(
        UUID id,
        UUID organizationId,
        String entityCode,
        UUID entityTypeConceptId,
        String entityTypeCode,
        String name,
        String description,
        String status,
        Instant validFrom,
        Instant validUntil,
        JsonNode attributes,
        String sourceType,
        UUID sourceReferenceId,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
    }

    public record AttributeView(
        UUID id,
        UUID entityId,
        UUID attributeConceptId,
        String attributeConceptCode,
        DynamicValue value,
        Instant validFrom,
        Instant validUntil,
        double confidence,
        UUID sourceFragmentId,
        String reviewStatus,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
    }

    public record RelationView(
        UUID id,
        UUID sourceEntityId,
        UUID targetEntityId,
        UUID relationConceptId,
        String relationConceptCode,
        JsonNode attributes,
        double confidence,
        Instant validFrom,
        Instant validUntil,
        UUID sourceFragmentId,
        String reviewStatus,
        long version
    ) {
    }

    public record CapabilityView(
        UUID id,
        UUID ownerEntityId,
        UUID capabilityConceptId,
        String capabilityConceptCode,
        String name,
        String description,
        JsonNode attributes,
        Instant validFrom,
        Instant validUntil,
        String status,
        double confidence,
        String reviewStatus,
        List<Map<String, Object>> evidence,
        long version
    ) {
    }

    public record EntityResolutionContext(
        UUID organizationId,
        String name,
        Map<String, String> identifiers,
        UUID entityTypeConceptId,
        UUID manufacturerEntityId,
        String model,
        String version,
        JsonNode policy
    ) {
    }

    public record EntityCandidate(
        UUID entityId,
        String name,
        Map<String, String> identifiers,
        UUID entityTypeConceptId,
        UUID manufacturerEntityId,
        String model,
        String version,
        double historicalAcceptance
    ) {
    }

    public record EntityResolutionResult(
        String status,
        UUID matchedEntityId,
        double score,
        Map<String, Double> signals,
        List<UUID> ambiguousCandidateIds
    ) {
    }
}
