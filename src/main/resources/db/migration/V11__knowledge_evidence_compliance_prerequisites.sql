-- Sprint 5 prerequisite completion for Sprint 6.
-- Knowledge, evidence and compliance taxonomies remain ontology/policy data.

CREATE TABLE terminology_snapshot (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    catalog_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    entries_hash VARCHAR(64) NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE requirement_extraction_job
    ADD CONSTRAINT fk_extraction_job_terminology_snapshot
    FOREIGN KEY (terminology_snapshot_id) REFERENCES terminology_snapshot(id);

CREATE TABLE knowledge_entity (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id),
    entity_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    canonical_name VARCHAR(500) NOT NULL,
    description TEXT,
    external_reference VARCHAR(500),
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    source_document_id UUID REFERENCES document(id),
    source_document_version_id UUID REFERENCES document_version(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE entity_attribute (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entity_id UUID NOT NULL REFERENCES knowledge_entity(id) ON DELETE CASCADE,
    attribute_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    value_json JSONB NOT NULL,
    unit_concept_id UUID REFERENCES ontology_concept(id),
    confidence NUMERIC(10,6) NOT NULL,
    source_clause_id UUID REFERENCES clause(id),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE knowledge_relation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_entity_id UUID NOT NULL REFERENCES knowledge_entity(id),
    target_entity_id UUID NOT NULL REFERENCES knowledge_entity(id),
    relation_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    relation_attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (source_entity_id <> target_entity_id)
);

CREATE TABLE capability (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id),
    entity_id UUID REFERENCES knowledge_entity(id),
    capability_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE evidence_fragment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    document_id UUID NOT NULL REFERENCES document(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    clause_id UUID REFERENCES clause(id),
    evidence_concept_id UUID REFERENCES ontology_concept(id),
    fragment_text TEXT NOT NULL,
    page_number INTEGER NOT NULL,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_hash VARCHAR(64) NOT NULL,
    redaction_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE evidence_claim (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    evidence_fragment_id UUID NOT NULL REFERENCES evidence_fragment(id),
    subject_entity_id UUID REFERENCES knowledge_entity(id),
    predicate_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    object_entity_id UUID REFERENCES knowledge_entity(id),
    value_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE evidence_validity_assessment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    evidence_claim_id UUID NOT NULL REFERENCES evidence_claim(id),
    validity_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    explanation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    assessed_by VARCHAR(255) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE knowledge_snapshot (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    ontology_version_id UUID NOT NULL REFERENCES ontology_version(id),
    terminology_snapshot_id UUID NOT NULL REFERENCES terminology_snapshot(id),
    entity_set_version BIGINT NOT NULL,
    relation_set_version BIGINT NOT NULL,
    capability_set_version BIGINT NOT NULL,
    evidence_set_version BIGINT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE compliance_evaluation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    requirement_id UUID NOT NULL REFERENCES requirement(id),
    knowledge_snapshot_id UUID NOT NULL REFERENCES knowledge_snapshot(id),
    decision_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    explanation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    analysis_profile_id UUID NOT NULL REFERENCES analysis_profile(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE compliance_evidence_link (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    compliance_evaluation_id UUID NOT NULL REFERENCES compliance_evaluation(id) ON DELETE CASCADE,
    evidence_claim_id UUID NOT NULL REFERENCES evidence_claim(id),
    link_role_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    relevance_score NUMERIC(10,6) NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (compliance_evaluation_id, evidence_claim_id, link_role_concept_id)
);

CREATE INDEX ix_knowledge_entity_project
    ON knowledge_entity (organization_id, project_id, entity_concept_id);
CREATE INDEX ix_entity_attribute_entity
    ON entity_attribute (organization_id, entity_id, attribute_concept_id);
CREATE INDEX ix_knowledge_relation_graph
    ON knowledge_relation (organization_id, source_entity_id, target_entity_id);
CREATE INDEX ix_capability_project
    ON capability (organization_id, project_id, capability_concept_id);
CREATE INDEX ix_evidence_fragment_project
    ON evidence_fragment (organization_id, project_id, document_version_id);
CREATE INDEX ix_evidence_claim_fragment
    ON evidence_claim (organization_id, evidence_fragment_id, predicate_concept_id);
CREATE INDEX ix_evidence_validity_claim
    ON evidence_validity_assessment (organization_id, evidence_claim_id, assessed_at DESC);
CREATE INDEX ix_knowledge_snapshot_project
    ON knowledge_snapshot (organization_id, project_id, created_at DESC);
CREATE INDEX ix_compliance_requirement
    ON compliance_evaluation (organization_id, requirement_id, created_at DESC);

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'terminology_snapshot', 'knowledge_entity', 'entity_attribute',
        'knowledge_relation', 'capability', 'evidence_fragment', 'evidence_claim',
        'evidence_validity_assessment', 'knowledge_snapshot',
        'compliance_evaluation', 'compliance_evidence_link'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_%I ON %I USING (organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
            table_name,
            table_name
        );
    END LOOP;
END
$$;
