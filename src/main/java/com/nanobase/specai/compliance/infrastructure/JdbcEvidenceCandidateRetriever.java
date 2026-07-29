package com.nanobase.specai.compliance.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.compliance.application.ComplianceModels.CandidateEvidence;
import com.nanobase.specai.compliance.application.ComplianceModels.PolicyVersion;
import com.nanobase.specai.compliance.application.EvidenceCandidateRetriever;
import com.nanobase.specai.compliance.application.LexicalEvidenceQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEvidenceCandidateRetriever implements EvidenceCandidateRetriever {
    private static final Logger log = LoggerFactory.getLogger(JdbcEvidenceCandidateRetriever.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcEvidenceCandidateRetriever(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public List<CandidateEvidence> retrieve(UUID organizationId, UUID requirementId,
                                            UUID targetEntityId, Instant entityCutoff,
                                            Instant evidenceCutoff,
                                            PolicyVersion policy) {
        Requirement requirement = requirement(organizationId, requirementId);
        LexicalEvidenceQuery lexical = LexicalEvidenceQuery.from(requirement.normalizedText());
        JsonNode limits = policy.configuration().path("candidateLimits");
        int metadataLimit = Math.max(1, limits.path("metadata").asInt(100));
        double defaultAuthority = policy.configuration()
            .path("defaultAuthorityScore").asDouble(0.4);
        String attributeConcept = requirement.attributes()
            .path("attributeConceptId").asText("");
        UUID attributeConceptId = parseUuid(attributeConcept);
        UUID primaryConceptId = requirement.primaryConceptId();
        String tokenList = lexical.tokenList();
        String tsQuery = lexical.isEmpty() ? "" : lexical.tsQuery();
        int minimumHits = lexical.minimumHits();

        List<CandidateEvidence> candidates = jdbc.query("""
            select fragment.id as fragment_id,
                   claim.id as claim_id,
                   coalesce(attribute.entity_id, claim.subject_entity_id,
                            capability.owner_entity_id) as entity_id,
                   attribute.attribute_concept_id,
                   attribute.unit_concept_id,
                   coalesce(attribute.numeric_value,
                            (claim.value_json ->> 'numericValue')::numeric) as numeric_value,
                   coalesce(attribute.numeric_value_end,
                            (claim.value_json ->> 'numericValueEnd')::numeric)
                       as numeric_value_end,
                   coalesce(attribute.boolean_value,
                            (claim.value_json ->> 'booleanValue')::boolean) as boolean_value,
                   coalesce(attribute.date_value,
                            (claim.value_json ->> 'dateValue')::timestamptz) as date_value,
                   case when ?::uuid is not null and
                                  (attribute.attribute_concept_id = ?::uuid
                                   or claim.predicate_concept_id = ?::uuid)
                        then 1.0 else 0.0 end as ontology_score,
                   case when ? = '' then 0.0
                        else ts_rank_cd(to_tsvector('simple', fragment.normalized_text),
                                       to_tsquery('simple', ?))
                   end as lexical_score,
                   case when ?::uuid is not null
                             and attribute.attribute_concept_id = ?::uuid
                        then 1.0 else 0.0 end as attribute_score,
                   coalesce(validity.score, 0.0) as validity_score,
                   coalesce(authority.score, ?::numeric) as authority_score,
                   coalesce(history.acceptance, 0.0) as historical_score,
                   case when ?::uuid is null or coalesce(attribute.entity_id,
                        claim.subject_entity_id, capability.owner_entity_id) = ?::uuid
                        then 0 else 1 end as relation_distance,
                   version.uploaded_at as document_date,
                   fragment.valid_until,
                   fragment.document_id,
                   fragment.document_version_id,
                   fragment.page_number,
                   case when ? = '' then 0
                        else (
                            select count(*)::int
                            from unnest(string_to_array(?, ' ')) as q(token)
                            where q.token <> ''
                              and to_tsvector('simple', fragment.normalized_text)
                                  @@ to_tsquery('simple', q.token || ':*')
                        )
                   end as lexical_hits
            from evidence_fragment fragment
            join document source_document
              on source_document.id = fragment.document_id
             and source_document.organization_id = fragment.organization_id
            join document_version version
              on version.id = fragment.document_version_id
             and version.organization_id = fragment.organization_id
            left join lateral (
                select candidate.*
                from entity_attribute candidate
                where candidate.organization_id = fragment.organization_id
                  and candidate.source_fragment_id = fragment.id
                  and candidate.valid_until is null
                order by candidate.confidence desc limit 1
            ) attribute on true
            left join lateral (
                select candidate.*
                from evidence_claim candidate
                where candidate.organization_id = fragment.organization_id
                  and candidate.evidence_fragment_id = fragment.id
                order by candidate.confidence desc limit 1
            ) claim on true
            left join lateral (
                select candidate.*
                from capability_evidence link
                join capability candidate
                  on candidate.id = link.capability_id
                 and candidate.organization_id = link.organization_id
                where link.organization_id = fragment.organization_id
                  and link.evidence_fragment_id = fragment.id
                  and candidate.valid_until is null
                order by link.strength desc limit 1
            ) capability on true
            left join lateral (
                select assessment.score, concept.metadata_json
                from evidence_validity_assessment assessment
                join ontology_concept concept on concept.id = assessment.status_concept_id
                where assessment.organization_id = fragment.organization_id
                  and assessment.evidence_fragment_id = fragment.id
                  and coalesce((concept.metadata_json ->> 'usable')::boolean, false)
                order by assessment.assessed_at desc limit 1
            ) validity on true
            left join lateral (
                select coalesce(
                    (profile.configuration_json ->> 'score')::numeric,
                    (policy.configuration_json -> 'sourceScores'
                        ->> source_document.document_type)::numeric,
                    (policy.configuration_json ->> 'defaultScore')::numeric
                ) as score
                from policy_version policy
                join policy_definition definition
                  on definition.id = policy.policy_definition_id
                left join ontology_concept source_type
                  on source_type.concept_code = source_document.document_type
                 and source_type.active = true
                 and (source_type.organization_id = fragment.organization_id
                      or source_type.organization_id is null)
                left join source_authority_profile profile
                  on profile.organization_id = fragment.organization_id
                 and profile.source_type_concept_id = source_type.id
                 and profile.active = true
                where definition.policy_type = 'SOURCE_AUTHORITY'
                  and policy.status = 'ACTIVE'
                  and (policy.organization_id = fragment.organization_id
                       or policy.organization_id is null)
                order by (profile.id is not null) desc,
                         (policy.organization_id is not null) desc,
                         policy.version_number desc
                limit 1
            ) authority on true
            left join lateral (
                select avg(case when feedback.corrected_snapshot_json
                                  ->> 'accepted' = 'true' then 1.0 else 0.0 end) acceptance
                from expert_feedback feedback
                where feedback.organization_id = fragment.organization_id
                  and feedback.entity_id in (attribute.entity_id, claim.subject_entity_id,
                                             capability.owner_entity_id)
                  and feedback.approved_for_learning = true
            ) history on true
            where fragment.organization_id = ?
              and fragment.created_at <= ?
              and (fragment.valid_until is null or fragment.valid_until > now())
              and (
                attribute.id is not null or claim.id is not null
                or capability.id is not null
                or (
                    ? <> ''
                    and (
                        select count(*)::int
                        from unnest(string_to_array(?, ' ')) as q(token)
                        where q.token <> ''
                          and to_tsvector('simple', fragment.normalized_text)
                              @@ to_tsquery('simple', q.token || ':*')
                    ) >= ?
                )
              )
              and (
                coalesce(attribute.entity_id, claim.subject_entity_id,
                         capability.owner_entity_id) is null
                or exists (
                    select 1 from knowledge_entity scoped_entity
                    where scoped_entity.organization_id = fragment.organization_id
                      and scoped_entity.id = coalesce(attribute.entity_id,
                          claim.subject_entity_id, capability.owner_entity_id)
                      and scoped_entity.created_at <= ?
                )
              )
              and (
                ?::uuid is null
                or coalesce(attribute.entity_id, claim.subject_entity_id,
                            capability.owner_entity_id) = ?::uuid
                or exists (
                    select 1 from knowledge_relation relation
                    where relation.organization_id = fragment.organization_id
                      and relation.valid_until is null
                      and ((relation.source_entity_id = ?::uuid
                            and relation.target_entity_id = coalesce(attribute.entity_id,
                                claim.subject_entity_id, capability.owner_entity_id))
                        or (relation.target_entity_id = ?::uuid
                            and relation.source_entity_id = coalesce(attribute.entity_id,
                                claim.subject_entity_id, capability.owner_entity_id)))
                )
              )
            order by ontology_score desc, attribute_score desc, lexical_score desc,
                     validity_score desc, fragment.created_at desc
            limit ?
            """, this::candidate,
            primaryConceptId, primaryConceptId, primaryConceptId,
            tsQuery, tsQuery,
            attributeConceptId, attributeConceptId, defaultAuthority,
            targetEntityId, targetEntityId,
            tokenList, tokenList,
            organizationId,
            Timestamp.from(evidenceCutoff),
            tokenList, tokenList, minimumHits,
            Timestamp.from(entityCutoff), targetEntityId, targetEntityId,
            targetEntityId, targetEntityId,
            metadataLimit);

        Double topScore = candidates.stream()
            .map(CandidateEvidence::lexicalScore)
            .max(Double::compareTo)
            .orElse(null);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("requirementId", requirementId);
        trace.put("organizationId", organizationId);
        trace.put("targetEntityId", targetEntityId);
        trace.put("queryTextLength", lexical.queryTextLength());
        trace.put("queryTokenCount", lexical.tokens().size());
        trace.put("minimumLexicalHits", minimumHits);
        trace.put("vectorCollection", null);
        trace.put("queryEmbeddingDimension", null);
        trace.put("keywordCandidateCount", candidates.size());
        trace.put("finalCandidateCount", candidates.size());
        trace.put("topScore", topScore);
        trace.put("documentScopeLocked", false);
        log.info("evidence_retrieval {}", writeTrace(trace));
        return candidates;
    }

    private String writeTrace(Map<String, Object> trace) {
        try {
            return mapper.writeValueAsString(trace);
        } catch (JsonProcessingException exception) {
            return trace.toString();
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Requirement requirement(UUID organizationId, UUID requirementId) {
        return jdbc.query("""
            select primary_concept_id, normalized_requirement_text, attributes_json::text
            from requirement where id = ? and organization_id = ?
            """, result -> {
                if (!result.next()) {
                    throw new IllegalArgumentException("Requirement not found");
                }
                return new Requirement(result.getObject(1, UUID.class),
                    result.getString(2), tree(result.getString(3)));
            }, requirementId, organizationId);
    }

    private CandidateEvidence candidate(ResultSet result, int row) throws SQLException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", result.getObject("document_id", UUID.class));
        metadata.put("documentVersionId",
            result.getObject("document_version_id", UUID.class));
        metadata.put("pageNumber", result.getObject("page_number"));
        metadata.put("validUntil", instant(result, "valid_until"));
        metadata.put("lexicalHits", result.getObject("lexical_hits"));
        return new CandidateEvidence(
            result.getObject("fragment_id", UUID.class),
            result.getObject("claim_id", UUID.class),
            result.getObject("entity_id", UUID.class),
            result.getObject("attribute_concept_id", UUID.class),
            result.getObject("unit_concept_id", UUID.class),
            result.getBigDecimal("numeric_value"),
            result.getBigDecimal("numeric_value_end"),
            (Boolean) result.getObject("boolean_value"),
            instant(result, "date_value"),
            result.getDouble("ontology_score"),
            result.getDouble("lexical_score"),
            result.getDouble("attribute_score"),
            result.getDouble("validity_score"),
            result.getDouble("authority_score"),
            result.getDouble("historical_score"),
            result.getInt("relation_distance"),
            instant(result, "document_date"),
            metadata
        );
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private JsonNode tree(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Requirement attributes are invalid", exception);
        }
    }

    private record Requirement(UUID primaryConceptId, String normalizedText,
                               JsonNode attributes) {
    }
}
