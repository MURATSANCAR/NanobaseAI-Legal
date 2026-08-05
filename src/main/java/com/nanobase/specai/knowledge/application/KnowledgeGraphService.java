package com.nanobase.specai.knowledge.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobase.specai.audit.application.AuditService;
import com.nanobase.specai.integration.outbox.OutboxService;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.AttributeRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.CapabilityRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.CreateEntityRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.EntityDetailResponse;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.MergeRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.RelationRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.SplitRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.SplitEntityRequest;
import com.nanobase.specai.knowledge.api.KnowledgeContracts.UpdateEntityRequest;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.AttributeView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.CapabilityView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.DynamicValue;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.EntityView;
import com.nanobase.specai.knowledge.domain.KnowledgeModels.RelationView;
import com.nanobase.specai.shared.security.CurrentTenant;
import com.nanobase.specai.shared.security.TenantPrincipal;
import com.nanobase.specai.shared.web.RequestContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGraphService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentTenant currentTenant;
    private final DynamicValueValidator values;
    private final AuditService audit;
    private final OutboxService outbox;

    public KnowledgeGraphService(JdbcTemplate jdbc, ObjectMapper mapper,
                                 CurrentTenant currentTenant, DynamicValueValidator values,
                                 AuditService audit, OutboxService outbox) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentTenant = currentTenant;
        this.values = values;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<EntityView> list(UUID entityTypeConceptId, String query) {
        UUID organizationId = currentTenant.require().tenantId();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        return jdbc.query("""
            select entity.*, concept.concept_code as entity_type_code
            from knowledge_entity entity
            join ontology_concept concept on concept.id = entity.entity_type_concept_id
            where entity.organization_id = ?
              and entity.valid_until is null
              and (?::uuid is null or entity.entity_type_concept_id = ?::uuid)
              and (? = '' or lower(entity.name) like ('%' || ? || '%')
                   or lower(entity.entity_code) like ('%' || ? || '%'))
            order by lower(entity.name), entity.id
            """, this::entity, organizationId, entityTypeConceptId, entityTypeConceptId,
            normalizedQuery, normalizedQuery, normalizedQuery);
    }

    @Transactional(readOnly = true)
    public EntityDetailResponse get(UUID id) {
        UUID organizationId = currentTenant.require().tenantId();
        EntityView entity = requireEntity(organizationId, id);
        List<AttributeView> attributes = attributes(organizationId, id);
        List<RelationView> relations = relations(organizationId, id);
        List<CapabilityView> capabilities = capabilities(organizationId, id);
        // No DISTINCT: EXISTS filters do not multiply rows, and PostgreSQL rejects
        // SELECT DISTINCT ... ORDER BY fragment.created_at when created_at is not selected.
        List<Map<String, Object>> evidence = normalizedRows(jdbc.queryForList("""
            select fragment.id, fragment.document_id, fragment.document_version_id,
                   fragment.clause_id, fragment.page_number, fragment.content_hash,
                   fragment.language, fragment.parser_quality, fragment.ocr_quality,
                   fragment.valid_from, fragment.valid_until, fragment.created_at,
                   validity.score as validity_score,
                   validity_concept.concept_code as validity_status
            from evidence_fragment fragment
            left join lateral (
                select assessment.score, assessment.status_concept_id
                from evidence_validity_assessment assessment
                where assessment.organization_id = fragment.organization_id
                  and assessment.evidence_fragment_id = fragment.id
                order by assessment.assessed_at desc limit 1
            ) validity on true
            left join ontology_concept validity_concept
              on validity_concept.id = validity.status_concept_id
            where fragment.organization_id = ?
              and (
                exists (select 1 from entity_attribute attribute
                    where attribute.organization_id = ? and attribute.entity_id = ?
                      and attribute.source_fragment_id = fragment.id)
                or exists (select 1 from knowledge_relation relation
                    where relation.organization_id = ? and
                      (relation.source_entity_id = ? or relation.target_entity_id = ?)
                      and relation.source_fragment_id = fragment.id)
                or exists (select 1 from evidence_claim claim
                    where claim.organization_id = ? and
                      (claim.subject_entity_id = ? or claim.object_entity_id = ?)
                      and claim.evidence_fragment_id = fragment.id)
              )
            order by fragment.valid_until nulls first, fragment.created_at desc
            """, organizationId, organizationId, id, organizationId, id, id,
            organizationId, id, id));
        return new EntityDetailResponse(entity, attributes, relations, capabilities,
            evidence, revisions(organizationId, id));
    }

    @Transactional
    public EntityView create(CreateEntityRequest request) {
        TenantPrincipal principal = currentTenant.require();
        requireConcept(principal.tenantId(), request.entityTypeConceptId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into knowledge_entity (
                id, organization_id, entity_code, entity_type_concept_id, name,
                description, status, valid_from, valid_until, attributes_json,
                source_type, source_reference_id, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            """, id, principal.tenantId(), request.entityCode(),
            request.entityTypeConceptId(), request.name(), request.description(),
            request.status(), timestamp(request.validFrom()), timestamp(request.validUntil()),
            json(request.attributes()), request.sourceType(), request.sourceReferenceId(),
            Timestamp.from(now), Timestamp.from(now));
        EntityView created = requireEntity(principal.tenantId(), id);
        audit.record("KNOWLEDGE_ENTITY_CREATED", "KnowledgeEntity", id, null, created);
        outbox.publish(principal.tenantId(), "KnowledgeEntity", id,
            "KnowledgeEntityCreated", "knowledge.entity.created.v1",
            Map.of("entityId", id, "entityTypeConceptId", request.entityTypeConceptId()),
            RequestContext.current().correlationId());
        return created;
    }

    @Transactional
    public EntityView update(UUID id, UpdateEntityRequest request) {
        TenantPrincipal principal = currentTenant.require();
        EntityView before = requireEntity(principal.tenantId(), id);
        requireConcept(principal.tenantId(), request.entityTypeConceptId());
        requireConcept(principal.tenantId(), request.changeTypeConceptId());
        revision(principal, id, before, request.changeTypeConceptId());
        int changed = jdbc.update("""
            update knowledge_entity
            set entity_type_concept_id = ?, name = ?, description = ?, status = ?,
                valid_from = ?, valid_until = ?, attributes_json = ?::jsonb,
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ? and version = ?
            """, request.entityTypeConceptId(), request.name(), request.description(),
            request.status(), timestamp(request.validFrom()), timestamp(request.validUntil()),
            json(request.attributes()), id, principal.tenantId(), request.version());
        optimistic(changed);
        EntityView after = requireEntity(principal.tenantId(), id);
        audit.record("KNOWLEDGE_ENTITY_UPDATED", "KnowledgeEntity", id, before, after);
        outbox.publish(principal.tenantId(), "KnowledgeEntity", id,
            "KnowledgeEntityUpdated", "knowledge.entity.updated.v1",
            Map.of("entityId", id), RequestContext.current().correlationId());
        return after;
    }

    @Transactional
    public AttributeView addAttribute(UUID entityId, AttributeRequest request) {
        TenantPrincipal principal = currentTenant.require();
        requireEntity(principal.tenantId(), entityId);
        requireConcept(principal.tenantId(), request.attributeConceptId());
        requireEvidence(principal.tenantId(), request.sourceFragmentId());
        DynamicValue value = values.normalize(request.value(), mapper.createObjectNode());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into entity_attribute (
                id, organization_id, entity_id, attribute_concept_id, value_type,
                text_value, numeric_value, numeric_value_end, boolean_value, date_value,
                json_value, unit_concept_id, valid_from, valid_until, confidence,
                source_fragment_id, review_status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, principal.tenantId(), entityId, request.attributeConceptId(),
            value.type(), value.textValue(), value.numericValue(), value.numericValueEnd(),
            value.booleanValue(), timestamp(value.dateValue()), json(value.jsonValue()),
            value.unitConceptId(), timestamp(request.validFrom()),
            timestamp(request.validUntil()), request.confidence(), request.sourceFragmentId(),
            request.reviewStatus(), Timestamp.from(now), Timestamp.from(now));
        AttributeView created = requireAttribute(principal.tenantId(), id);
        audit.record("ENTITY_ATTRIBUTE_CREATED", "EntityAttribute", id, null, created);
        return created;
    }

    @Transactional
    public AttributeView updateAttribute(UUID id, AttributeRequest request) {
        TenantPrincipal principal = currentTenant.require();
        AttributeView before = requireAttribute(principal.tenantId(), id);
        requireConcept(principal.tenantId(), request.attributeConceptId());
        requireEvidence(principal.tenantId(), request.sourceFragmentId());
        DynamicValue value = values.normalize(request.value(), mapper.createObjectNode());
        jdbc.update("""
            update entity_attribute set attribute_concept_id = ?, value_type = ?,
                text_value = ?, numeric_value = ?, numeric_value_end = ?,
                boolean_value = ?, date_value = ?, json_value = ?::jsonb,
                unit_concept_id = ?, valid_from = ?, valid_until = ?, confidence = ?,
                source_fragment_id = ?, review_status = ?, updated_at = now(),
                version = version + 1
            where id = ? and organization_id = ?
            """, request.attributeConceptId(), value.type(), value.textValue(),
            value.numericValue(), value.numericValueEnd(), value.booleanValue(),
            timestamp(value.dateValue()), json(value.jsonValue()), value.unitConceptId(),
            timestamp(request.validFrom()), timestamp(request.validUntil()),
            request.confidence(), request.sourceFragmentId(), request.reviewStatus(),
            id, principal.tenantId());
        AttributeView after = requireAttribute(principal.tenantId(), id);
        audit.record("ENTITY_ATTRIBUTE_UPDATED", "EntityAttribute", id, before, after);
        return after;
    }

    @Transactional
    public void endAttribute(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        AttributeView before = requireAttribute(principal.tenantId(), id);
        jdbc.update("""
            update entity_attribute set valid_until = now(), updated_at = now(),
                version = version + 1 where id = ? and organization_id = ?
            """, id, principal.tenantId());
        audit.record("ENTITY_ATTRIBUTE_ENDED", "EntityAttribute", id, before,
            requireAttribute(principal.tenantId(), id));
    }

    @Transactional
    public RelationView createRelation(RelationRequest request) {
        TenantPrincipal principal = currentTenant.require();
        if (request.sourceEntityId().equals(request.targetEntityId())) {
            throw new IllegalArgumentException("Self relation is not allowed");
        }
        requireEntity(principal.tenantId(), request.sourceEntityId());
        requireEntity(principal.tenantId(), request.targetEntityId());
        requireConcept(principal.tenantId(), request.relationConceptId());
        requireEvidence(principal.tenantId(), request.sourceFragmentId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into knowledge_relation (
                id, organization_id, source_entity_id, target_entity_id,
                relation_concept_id, attributes_json, confidence, valid_from,
                valid_until, source_fragment_id, review_status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            """, id, principal.tenantId(), request.sourceEntityId(),
            request.targetEntityId(), request.relationConceptId(),
            json(request.attributes()), request.confidence(),
            timestamp(request.validFrom()), timestamp(request.validUntil()),
            request.sourceFragmentId(), request.reviewStatus(),
            Timestamp.from(now), Timestamp.from(now));
        RelationView created = requireRelation(principal.tenantId(), id);
        audit.record("KNOWLEDGE_RELATION_CREATED", "KnowledgeRelation", id, null, created);
        return created;
    }

    @Transactional
    public RelationView updateRelation(UUID id, RelationRequest request) {
        TenantPrincipal principal = currentTenant.require();
        RelationView before = requireRelation(principal.tenantId(), id);
        requireEntity(principal.tenantId(), request.sourceEntityId());
        requireEntity(principal.tenantId(), request.targetEntityId());
        requireConcept(principal.tenantId(), request.relationConceptId());
        requireEvidence(principal.tenantId(), request.sourceFragmentId());
        jdbc.update("""
            update knowledge_relation set source_entity_id = ?, target_entity_id = ?,
                relation_concept_id = ?, attributes_json = ?::jsonb, confidence = ?,
                valid_from = ?, valid_until = ?, source_fragment_id = ?,
                review_status = ?, updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, request.sourceEntityId(), request.targetEntityId(),
            request.relationConceptId(), json(request.attributes()), request.confidence(),
            timestamp(request.validFrom()), timestamp(request.validUntil()),
            request.sourceFragmentId(), request.reviewStatus(), id, principal.tenantId());
        RelationView after = requireRelation(principal.tenantId(), id);
        audit.record("KNOWLEDGE_RELATION_UPDATED", "KnowledgeRelation", id, before, after);
        return after;
    }

    @Transactional
    public void endRelation(UUID id) {
        TenantPrincipal principal = currentTenant.require();
        RelationView before = requireRelation(principal.tenantId(), id);
        jdbc.update("""
            update knowledge_relation set valid_until = now(), updated_at = now(),
                version = version + 1 where id = ? and organization_id = ?
            """, id, principal.tenantId());
        audit.record("KNOWLEDGE_RELATION_ENDED", "KnowledgeRelation", id, before,
            requireRelation(principal.tenantId(), id));
    }

    @Transactional
    public CapabilityView createCapability(UUID entityId, CapabilityRequest request) {
        TenantPrincipal principal = currentTenant.require();
        requireEntity(principal.tenantId(), entityId);
        requireConcept(principal.tenantId(), request.capabilityConceptId());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
            insert into capability (
                id, organization_id, owner_entity_id, capability_concept_id,
                name, description, capability_attributes_json, valid_from, valid_until,
                status, confidence, review_status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            """, id, principal.tenantId(), entityId, request.capabilityConceptId(),
            request.name(), request.description(), json(request.attributes()),
            timestamp(request.validFrom()), timestamp(request.validUntil()),
            request.status(), request.confidence(), request.reviewStatus(),
            Timestamp.from(now), Timestamp.from(now));
        if (request.evidence() != null) {
            request.evidence().forEach(link -> {
                requireEvidence(principal.tenantId(), link.evidenceFragmentId());
                requireConcept(principal.tenantId(), link.evidenceRoleConceptId());
                jdbc.update("""
                    insert into capability_evidence (
                        id, organization_id, capability_id, evidence_fragment_id,
                        evidence_role_concept_id, strength, valid_from, valid_until, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, now())
                    """, UUID.randomUUID(), principal.tenantId(), id,
                    link.evidenceFragmentId(), link.evidenceRoleConceptId(),
                    link.strength(), timestamp(link.validFrom()), timestamp(link.validUntil()));
            });
        }
        CapabilityView created = requireCapability(principal.tenantId(), id);
        audit.record("CAPABILITY_CREATED", "Capability", id, null, created);
        outbox.publish(principal.tenantId(), "Capability", id, "KnowledgeCapabilityCreated",
            "knowledge.capability.created.v1", Map.of("capabilityId", id, "entityId", entityId),
            RequestContext.current().correlationId());
        return created;
    }

    @Transactional
    public CapabilityView updateCapability(UUID id, CapabilityRequest request) {
        TenantPrincipal principal = currentTenant.require();
        CapabilityView before = requireCapability(principal.tenantId(), id);
        requireConcept(principal.tenantId(), request.capabilityConceptId());
        jdbc.update("""
            update capability set capability_concept_id = ?, name = ?, description = ?,
                capability_attributes_json = ?::jsonb, valid_from = ?, valid_until = ?,
                status = ?, confidence = ?, review_status = ?, updated_at = now(),
                version = version + 1
            where id = ? and organization_id = ?
            """, request.capabilityConceptId(), request.name(), request.description(),
            json(request.attributes()), timestamp(request.validFrom()),
            timestamp(request.validUntil()), request.status(), request.confidence(),
            request.reviewStatus(), id, principal.tenantId());
        CapabilityView after = requireCapability(principal.tenantId(), id);
        audit.record("CAPABILITY_UPDATED", "Capability", id, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<CapabilityView> capabilities(UUID entityId) {
        UUID organizationId = currentTenant.require().tenantId();
        requireEntity(organizationId, entityId);
        return capabilities(organizationId, entityId);
    }

    @Transactional
    public EntityDetailResponse merge(UUID targetId, MergeRequest request) {
        TenantPrincipal principal = currentTenant.require();
        EntityDetailResponse before = detail(principal.tenantId(), targetId);
        requireConcept(principal.tenantId(), request.changeTypeConceptId());
        if (request.sourceEntityIds().contains(targetId)) {
            throw new IllegalArgumentException("Target entity cannot be a merge source");
        }
        revision(principal, targetId, before, request.changeTypeConceptId());
        for (UUID sourceId : request.sourceEntityIds()) {
            EntityDetailResponse source = detail(principal.tenantId(), sourceId);
            revision(principal, sourceId, source, request.changeTypeConceptId());
            jdbc.update("""
                update knowledge_relation set valid_until = now(), updated_at = now(),
                    version = version + 1
                where organization_id = ? and valid_until is null
                  and ((source_entity_id = ? and target_entity_id = ?)
                    or (source_entity_id = ? and target_entity_id = ?))
                """, principal.tenantId(), sourceId, targetId, targetId, sourceId);
            jdbc.update("""
                update entity_attribute set entity_id = ?, updated_at = now(),
                    version = version + 1 where entity_id = ? and organization_id = ?
                """, targetId, sourceId, principal.tenantId());
            jdbc.update("""
                update capability set owner_entity_id = ?, updated_at = now(),
                    version = version + 1 where owner_entity_id = ? and organization_id = ?
                """, targetId, sourceId, principal.tenantId());
            jdbc.update("""
                update evidence_claim set subject_entity_id = ?, updated_at = now(),
                    version = version + 1
                where subject_entity_id = ? and organization_id = ?
                """, targetId, sourceId, principal.tenantId());
            jdbc.update("""
                update evidence_claim set object_entity_id = ?, updated_at = now(),
                    version = version + 1
                where object_entity_id = ? and organization_id = ?
                """, targetId, sourceId, principal.tenantId());
            jdbc.update("""
                update knowledge_relation set source_entity_id = ?, updated_at = now(),
                    version = version + 1
                where source_entity_id = ? and organization_id = ? and valid_until is null
                """, targetId, sourceId, principal.tenantId());
            jdbc.update("""
                update knowledge_relation set target_entity_id = ?, updated_at = now(),
                    version = version + 1
                where target_entity_id = ? and organization_id = ? and valid_until is null
                """, targetId, sourceId, principal.tenantId());
            jdbc.update("""
                update knowledge_entity set status = 'MERGED', valid_until = now(),
                    updated_at = now(), version = version + 1
                where id = ? and organization_id = ?
                """, sourceId, principal.tenantId());
        }
        EntityDetailResponse after = detail(principal.tenantId(), targetId);
        audit.record("KNOWLEDGE_ENTITY_MERGED", "KnowledgeEntity", targetId, before, after);
        outbox.publish(principal.tenantId(), "KnowledgeEntity", targetId,
            "KnowledgeEntityMerged", "knowledge.entity.merged.v1",
            Map.of("targetEntityId", targetId, "sourceEntityIds", request.sourceEntityIds()),
            RequestContext.current().correlationId());
        return after;
    }

    @Transactional
    public List<EntityDetailResponse> split(UUID sourceId, SplitRequest request) {
        TenantPrincipal principal = currentTenant.require();
        EntityDetailResponse before = detail(principal.tenantId(), sourceId);
        requireConcept(principal.tenantId(), request.changeTypeConceptId());
        revision(principal, sourceId, before, request.changeTypeConceptId());
        List<EntityDetailResponse> created = new ArrayList<>();
        for (SplitEntityRequest child : request.entities()) {
            EntityView childEntity = create(child.entity());
            UUID childId = childEntity.id();
            moveIds("entity_attribute", "entity_id", child.attributeIds(),
                sourceId, childId, principal.tenantId());
            moveIds("capability", "owner_entity_id", child.capabilityIds(),
                sourceId, childId, principal.tenantId());
            moveIds("knowledge_relation", "source_entity_id", child.outgoingRelationIds(),
                sourceId, childId, principal.tenantId());
            moveIds("knowledge_relation", "target_entity_id", child.incomingRelationIds(),
                sourceId, childId, principal.tenantId());
            revision(principal, childId, childEntity, request.changeTypeConceptId());
            created.add(detail(principal.tenantId(), childId));
        }
        jdbc.update("""
            update knowledge_entity set status = 'SPLIT', valid_until = now(),
                updated_at = now(), version = version + 1
            where id = ? and organization_id = ?
            """, sourceId, principal.tenantId());
        audit.record("KNOWLEDGE_ENTITY_SPLIT", "KnowledgeEntity", sourceId, before, created);
        return List.copyOf(created);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> revisions(UUID entityId) {
        UUID organizationId = currentTenant.require().tenantId();
        requireEntity(organizationId, entityId);
        return revisions(organizationId, entityId);
    }

    private EntityDetailResponse detail(UUID organizationId, UUID id) {
        EntityView entity = requireEntity(organizationId, id);
        return new EntityDetailResponse(entity, attributes(organizationId, id),
            relations(organizationId, id), capabilities(organizationId, id), List.of(),
            revisions(organizationId, id));
    }

    private void moveIds(String table, String ownerColumn, List<UUID> ids,
                         UUID sourceId, UUID targetId, UUID organizationId) {
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            int changed = jdbc.update("""
                update %s set %s = ?, updated_at = now(), version = version + 1
                where id = ? and organization_id = ? and %s = ?
                """.formatted(table, ownerColumn, ownerColumn),
                targetId, id, organizationId, sourceId);
            if (changed != 1) {
                throw new IllegalArgumentException("Split member does not belong to source entity");
            }
        }
    }

    private List<AttributeView> attributes(UUID organizationId, UUID entityId) {
        return jdbc.query("""
            select attribute.*, concept.concept_code as attribute_concept_code
            from entity_attribute attribute
            join ontology_concept concept on concept.id = attribute.attribute_concept_id
            where attribute.organization_id = ? and attribute.entity_id = ?
            order by attribute.valid_until nulls first, attribute.created_at, attribute.id
            """, this::attribute, organizationId, entityId);
    }

    private List<RelationView> relations(UUID organizationId, UUID entityId) {
        return jdbc.query("""
            select relation.*, concept.concept_code as relation_concept_code
            from knowledge_relation relation
            join ontology_concept concept on concept.id = relation.relation_concept_id
            where relation.organization_id = ?
              and (relation.source_entity_id = ? or relation.target_entity_id = ?)
            order by relation.valid_until nulls first, relation.created_at, relation.id
            """, this::relation, organizationId, entityId, entityId);
    }

    private List<CapabilityView> capabilities(UUID organizationId, UUID entityId) {
        return jdbc.query("""
            select capability.*, concept.concept_code as capability_concept_code
            from capability
            join ontology_concept concept on concept.id = capability.capability_concept_id
            where capability.organization_id = ? and capability.owner_entity_id = ?
            order by capability.valid_until nulls first, capability.created_at, capability.id
            """, (result, row) -> capability(result,
                normalizedRows(jdbc.queryForList("""
                    select evidence.id, evidence.evidence_fragment_id,
                           evidence.evidence_role_concept_id, concept.concept_code as role,
                           evidence.strength, evidence.valid_from, evidence.valid_until
                    from capability_evidence evidence
                    join ontology_concept concept
                      on concept.id = evidence.evidence_role_concept_id
                    where evidence.organization_id = ? and evidence.capability_id = ?
                    order by evidence.created_at
                    """, organizationId, result.getObject("id", UUID.class)))),
            organizationId, entityId);
    }

    private EntityView requireEntity(UUID organizationId, UUID id) {
        try {
            return jdbc.queryForObject("""
                select entity.*, concept.concept_code as entity_type_code
                from knowledge_entity entity
                join ontology_concept concept on concept.id = entity.entity_type_concept_id
                where entity.id = ? and entity.organization_id = ?
                """, this::entity, id, organizationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("Knowledge entity not found");
        }
    }

    private AttributeView requireAttribute(UUID organizationId, UUID id) {
        try {
            return jdbc.queryForObject("""
                select attribute.*, concept.concept_code as attribute_concept_code
                from entity_attribute attribute
                join ontology_concept concept on concept.id = attribute.attribute_concept_id
                where attribute.id = ? and attribute.organization_id = ?
                """, this::attribute, id, organizationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("Entity attribute not found");
        }
    }

    private RelationView requireRelation(UUID organizationId, UUID id) {
        try {
            return jdbc.queryForObject("""
                select relation.*, concept.concept_code as relation_concept_code
                from knowledge_relation relation
                join ontology_concept concept on concept.id = relation.relation_concept_id
                where relation.id = ? and relation.organization_id = ?
                """, this::relation, id, organizationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("Knowledge relation not found");
        }
    }

    private CapabilityView requireCapability(UUID organizationId, UUID id) {
        try {
            return jdbc.queryForObject("""
                select capability.*, concept.concept_code as capability_concept_code
                from capability
                join ontology_concept concept on concept.id = capability.capability_concept_id
                where capability.id = ? and capability.organization_id = ?
                """, (result, row) -> capability(result, normalizedRows(jdbc.queryForList("""
                    select id, evidence_fragment_id, evidence_role_concept_id, strength,
                           valid_from, valid_until
                    from capability_evidence
                    where organization_id = ? and capability_id = ?
                    order by created_at
                    """, organizationId, id))), id, organizationId);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalArgumentException("Capability not found");
        }
    }

    private void requireEvidence(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from evidence_fragment
            where id = ? and organization_id = ? and (valid_until is null or valid_until > now())
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active evidence fragment not found");
        }
    }

    private void requireConcept(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("""
            select count(*) from ontology_concept
            where id = ? and active = true
              and (organization_id = ? or organization_id is null)
            """, Integer.class, id, organizationId);
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Active ontology concept not found");
        }
    }

    private void revision(TenantPrincipal principal, UUID entityId, Object snapshot,
                          UUID changeTypeConceptId) {
        Integer number = jdbc.queryForObject("""
            select coalesce(max(revision_number), 0) + 1
            from entity_revision where organization_id = ? and entity_id = ?
            """, Integer.class, principal.tenantId(), entityId);
        jdbc.update("""
            insert into entity_revision (
                id, organization_id, entity_id, revision_number, snapshot_json,
                change_type_concept_id, created_by, created_at
            ) values (?, ?, ?, ?, ?::jsonb, ?, ?, now())
            """, UUID.randomUUID(), principal.tenantId(), entityId, number,
            jsonValue(snapshot), changeTypeConceptId, principal.subject());
    }

    private List<Map<String, Object>> revisions(UUID organizationId, UUID entityId) {
        return normalizedRows(jdbc.queryForList("""
            select revision.id, revision.revision_number,
                   revision.snapshot_json::text as snapshot_json,
                   revision.change_type_concept_id, concept.concept_code as change_type,
                   revision.created_by, revision.created_at
            from entity_revision revision
            join ontology_concept concept on concept.id = revision.change_type_concept_id
            where revision.organization_id = ? and revision.entity_id = ?
            order by revision.revision_number desc
            """, organizationId, entityId));
    }

    private EntityView entity(ResultSet result, int row) throws SQLException {
        return new EntityView(result.getObject("id", UUID.class),
            result.getObject("organization_id", UUID.class),
            result.getString("entity_code"),
            result.getObject("entity_type_concept_id", UUID.class),
            result.getString("entity_type_code"), result.getString("name"),
            result.getString("description"), result.getString("status"),
            instant(result, "valid_from"), instant(result, "valid_until"),
            tree(result.getString("attributes_json")), result.getString("source_type"),
            result.getObject("source_reference_id", UUID.class),
            instant(result, "created_at"), instant(result, "updated_at"),
            result.getLong("version"));
    }

    private AttributeView attribute(ResultSet result, int row) throws SQLException {
        DynamicValue value = new DynamicValue(result.getString("value_type"),
            result.getString("text_value"), result.getBigDecimal("numeric_value"),
            result.getBigDecimal("numeric_value_end"),
            (Boolean) result.getObject("boolean_value"), instant(result, "date_value"),
            tree(result.getString("json_value")),
            result.getObject("unit_concept_id", UUID.class), Map.of());
        return new AttributeView(result.getObject("id", UUID.class),
            result.getObject("entity_id", UUID.class),
            result.getObject("attribute_concept_id", UUID.class),
            result.getString("attribute_concept_code"), value,
            instant(result, "valid_from"), instant(result, "valid_until"),
            result.getDouble("confidence"),
            result.getObject("source_fragment_id", UUID.class),
            result.getString("review_status"), instant(result, "created_at"),
            instant(result, "updated_at"), result.getLong("version"));
    }

    private RelationView relation(ResultSet result, int row) throws SQLException {
        return new RelationView(result.getObject("id", UUID.class),
            result.getObject("source_entity_id", UUID.class),
            result.getObject("target_entity_id", UUID.class),
            result.getObject("relation_concept_id", UUID.class),
            result.getString("relation_concept_code"),
            tree(result.getString("attributes_json")), result.getDouble("confidence"),
            instant(result, "valid_from"), instant(result, "valid_until"),
            result.getObject("source_fragment_id", UUID.class),
            result.getString("review_status"), result.getLong("version"));
    }

    private CapabilityView capability(ResultSet result,
                                      List<Map<String, Object>> evidence) throws SQLException {
        return new CapabilityView(result.getObject("id", UUID.class),
            result.getObject("owner_entity_id", UUID.class),
            result.getObject("capability_concept_id", UUID.class),
            result.getString("capability_concept_code"), result.getString("name"),
            result.getString("description"),
            tree(result.getString("capability_attributes_json")),
            instant(result, "valid_from"), instant(result, "valid_until"),
            result.getString("status"), result.getDouble("confidence"),
            result.getString("review_status"), evidence, result.getLong("version"));
    }

    private List<Map<String, Object>> normalizedRows(List<Map<String, Object>> rows) {
        return rows.stream()
            .map(row -> (Map<String, Object>) new LinkedHashMap<String, Object>(row))
            .toList();
    }

    private void optimistic(int changed) {
        if (changed != 1) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                "Knowledge entity was modified by another request");
        }
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String json(JsonNode node) {
        return node == null ? "{}" : node.toString();
    }

    private String jsonValue(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Knowledge snapshot is not serializable",
                exception);
        }
    }

    private JsonNode tree(String value) {
        if (value == null) {
            return mapper.nullNode();
        }
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored JSON is invalid", exception);
        }
    }
}
