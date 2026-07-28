package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.knowledge.application.PolicyEvidenceValidityEngine.ValidityInput;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Materializes policy-versioned evidence validity assessments for parsed fragments.
 * Reprocessing is idempotent for the same fragment and policy version.
 */
@Service
public class KnowledgeEvidencePolicyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PolicySourceAuthorityEvaluator authorityEvaluator;
    private final PolicyEvidenceValidityEngine validityEngine;

    public KnowledgeEvidencePolicyService(
        JdbcTemplate jdbc,
        ObjectMapper mapper,
        PolicySourceAuthorityEvaluator authorityEvaluator,
        PolicyEvidenceValidityEngine validityEngine
    ) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.authorityEvaluator = authorityEvaluator;
        this.validityEngine = validityEngine;
    }

    public int assess(UUID organizationId, UUID documentVersionId) {
        Policy authorityPolicy = policy(organizationId, "SOURCE_AUTHORITY");
        Policy validityPolicy = policy(organizationId, "EVIDENCE_VALIDITY");
        List<Fragment> fragments = jdbc.query("""
            select fragment.id, document.document_type, fragment.valid_until,
                   coalesce(fragment.parser_quality, 0),
                   coalesce(fragment.ocr_quality, 0)
            from evidence_fragment fragment
            join document on document.id = fragment.document_id
             and document.organization_id = fragment.organization_id
            where fragment.organization_id = ?
              and fragment.document_version_id = ?
              and not exists (
                select 1 from evidence_validity_assessment assessment
                where assessment.organization_id = fragment.organization_id
                  and assessment.evidence_fragment_id = fragment.id
                  and assessment.policy_version_id = ?
              )
            """, (result, row) -> new Fragment(
                result.getObject(1, UUID.class),
                result.getString(2),
                instant(result.getTimestamp(3)),
                result.getDouble(4),
                result.getDouble(5)
            ), organizationId, documentVersionId, validityPolicy.id());
        int assessed = 0;
        for (Fragment fragment : fragments) {
            JsonNode profile = sourceProfile(organizationId, fragment.sourceTypeCode());
            double authority = authorityEvaluator.evaluate(fragment.sourceTypeCode(), null,
                authorityPolicy.configuration(), profile);
            var result = validityEngine.evaluate(
                new ValidityInput(Instant.now(), fragment.validUntil(),
                    fragment.parserQuality(), fragment.ocrQuality(), false, authority),
                validityPolicy.configuration());
            UUID statusConceptId = statusConcept(
                organizationId, result.statusSelector());
            jdbc.update("""
                insert into evidence_validity_assessment (
                    id, organization_id, evidence_fragment_id, policy_version_id,
                    status_concept_id, score, factors_json, assessed_at,
                    assessed_by_type, created_at
                ) values (?, ?, ?, ?, ?, ?, ?::jsonb, now(), 'POLICY_ENGINE', now())
                """, UUID.randomUUID(), organizationId, fragment.id(),
                validityPolicy.id(), statusConceptId, result.score(),
                json(result.factors()));
            assessed++;
        }
        return assessed;
    }

    private Policy policy(UUID organizationId, String type) {
        return jdbc.query("""
            select version.id, version.configuration_json::text
            from policy_definition definition
            join policy_version version
              on version.policy_definition_id = definition.id
            where definition.policy_type = ? and version.status = 'ACTIVE'
              and (version.organization_id = ? or version.organization_id is null)
            order by (version.organization_id is not null) desc,
                     version.version_number desc
            limit 1
            """, result -> {
                if (!result.next()) {
                    throw new IllegalStateException(
                        "Active evidence policy is not configured: " + type);
                }
                return new Policy(result.getObject(1, UUID.class),
                    tree(result.getString(2)));
            }, type, organizationId);
    }

    private JsonNode sourceProfile(UUID organizationId, String sourceTypeCode) {
        List<String> profiles = jdbc.query("""
            select profile.configuration_json::text
            from source_authority_profile profile
            join ontology_concept concept
              on concept.id = profile.source_type_concept_id
            where profile.organization_id = ? and profile.active = true
              and concept.concept_code = ? and concept.active = true
            order by profile.updated_at desc
            limit 1
            """, (result, row) -> result.getString(1),
            organizationId, sourceTypeCode);
        return profiles.isEmpty() ? null : tree(profiles.getFirst());
    }

    private UUID statusConcept(UUID organizationId, String selector) {
        List<UUID> ids = jdbc.query("""
            select id from ontology_concept
            where concept_type = 'EVIDENCE_VALIDITY_STATUS' and active = true
              and metadata_json ->> 'validitySelector' = ?
              and (organization_id = ? or organization_id is null)
            order by (organization_id is not null) desc, sort_order
            limit 1
            """, (result, row) -> result.getObject(1, UUID.class),
            selector, organizationId);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                "Evidence validity selector is not mapped to an ontology concept");
        }
        return ids.getFirst();
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evidence policy JSON is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                "Evidence assessment is not serializable", exception);
        }
    }

    private record Policy(UUID id, JsonNode configuration) {
    }

    private record Fragment(UUID id, String sourceTypeCode, Instant validUntil,
                            double parserQuality, double ocrQuality) {
    }
}
