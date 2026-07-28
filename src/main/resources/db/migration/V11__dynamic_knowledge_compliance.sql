-- Sprint 5: tenant-scoped dynamic knowledge graph, evidence and compliance engine.
-- Business vocabulary is stored as ontology/catalog data. VARCHAR lifecycle columns below
-- are platform workflow state only; no company/product field or compliance label is encoded.

ALTER TABLE tender_project
    ADD CONSTRAINT uq_tender_project_id_organization UNIQUE (id, organization_id);
ALTER TABLE document
    ADD CONSTRAINT uq_document_id_organization UNIQUE (id, organization_id);
ALTER TABLE document_version
    ADD CONSTRAINT uq_document_version_id_organization UNIQUE (id, organization_id);
ALTER TABLE clause
    ADD CONSTRAINT uq_clause_id_organization UNIQUE (id, organization_id);
ALTER TABLE requirement
    ADD CONSTRAINT uq_requirement_id_organization UNIQUE (id, organization_id);

CREATE TABLE evidence_fragment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    clause_id UUID,
    document_page_id UUID,
    page_number INTEGER,
    fragment_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_start_offset INTEGER,
    source_end_offset INTEGER,
    content_hash VARCHAR(64) NOT NULL,
    language VARCHAR(20),
    parser_quality NUMERIC(10,6),
    ocr_quality NUMERIC(10,6),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    UNIQUE (organization_id, document_version_id, content_hash),
    FOREIGN KEY (document_id, organization_id)
        REFERENCES document(id, organization_id),
    FOREIGN KEY (document_version_id, organization_id)
        REFERENCES document_version(id, organization_id),
    FOREIGN KEY (clause_id, organization_id)
        REFERENCES clause(id, organization_id),
    CHECK (source_start_offset IS NULL OR source_start_offset >= 0),
    CHECK (source_end_offset IS NULL OR source_end_offset >= source_start_offset),
    CHECK (parser_quality IS NULL OR parser_quality BETWEEN 0 AND 1),
    CHECK (ocr_quality IS NULL OR ocr_quality BETWEEN 0 AND 1)
);

CREATE TABLE knowledge_entity (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entity_code VARCHAR(240) NOT NULL,
    entity_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    name VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(80) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_type VARCHAR(160) NOT NULL,
    source_reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    UNIQUE (organization_id, entity_code)
);

CREATE TABLE entity_attribute (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entity_id UUID NOT NULL,
    attribute_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    value_type VARCHAR(160) NOT NULL,
    text_value TEXT,
    numeric_value NUMERIC,
    numeric_value_end NUMERIC,
    boolean_value BOOLEAN,
    date_value TIMESTAMPTZ,
    json_value JSONB,
    unit_concept_id UUID REFERENCES ontology_concept(id),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    confidence NUMERIC(10,6) NOT NULL,
    source_fragment_id UUID NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    FOREIGN KEY (source_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE knowledge_relation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_entity_id UUID NOT NULL,
    target_entity_id UUID NOT NULL,
    relation_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    source_fragment_id UUID NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (source_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    FOREIGN KEY (target_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    FOREIGN KEY (source_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    CHECK (source_entity_id <> target_entity_id),
    CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE capability (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    owner_entity_id UUID NOT NULL,
    capability_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    name VARCHAR(500) NOT NULL,
    description TEXT,
    capability_attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    status VARCHAR(80) NOT NULL,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (owner_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE capability_evidence (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    capability_id UUID NOT NULL,
    evidence_fragment_id UUID NOT NULL,
    evidence_role_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    strength NUMERIC(10,6) NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    UNIQUE (capability_id, evidence_fragment_id, evidence_role_concept_id),
    FOREIGN KEY (capability_id, organization_id)
        REFERENCES capability(id, organization_id),
    FOREIGN KEY (evidence_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    CHECK (strength BETWEEN 0 AND 1)
);

CREATE TABLE evidence_claim (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    evidence_fragment_id UUID NOT NULL,
    claim_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    subject_entity_id UUID,
    predicate_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    object_entity_id UUID,
    value_json JSONB,
    claim_text TEXT NOT NULL,
    grounding_status VARCHAR(80) NOT NULL,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (evidence_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    FOREIGN KEY (subject_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    FOREIGN KEY (object_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    CHECK (confidence BETWEEN 0 AND 1)
);

CREATE TABLE evidence_validity_assessment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    evidence_fragment_id UUID NOT NULL,
    policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    score NUMERIC(10,6) NOT NULL,
    factors_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    assessed_at TIMESTAMPTZ NOT NULL,
    assessed_by_type VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (evidence_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    CHECK (score BETWEEN 0 AND 1)
);

CREATE TABLE source_authority_profile (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    issuer_entity_id UUID,
    trust_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    configuration_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (issuer_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id)
);

CREATE TABLE candidate_concept (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    ontology_version_id UUID NOT NULL REFERENCES ontology_version(id),
    proposed_code VARCHAR(240) NOT NULL,
    proposed_name VARCHAR(500) NOT NULL,
    proposed_type VARCHAR(160) NOT NULL,
    source_fragment_id UUID NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_status VARCHAR(80) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    UNIQUE (id, organization_id),
    UNIQUE (organization_id, ontology_version_id, proposed_code, source_fragment_id),
    FOREIGN KEY (source_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id)
);

CREATE TABLE knowledge_extraction_profile (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL,
    analysis_profile_id UUID NOT NULL REFERENCES analysis_profile(id),
    ontology_version_id UUID NOT NULL REFERENCES ontology_version(id),
    terminology_snapshot_id UUID NOT NULL,
    policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    prompt_package_version_id UUID NOT NULL REFERENCES prompt_package_version(id),
    output_schema_version_id UUID NOT NULL REFERENCES output_schema_version(id),
    model_routing_policy_id UUID NOT NULL REFERENCES policy_version(id),
    confidence_policy_id UUID NOT NULL REFERENCES policy_version(id),
    document_role_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    snapshot_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (document_version_id, organization_id)
        REFERENCES document_version(id, organization_id)
);

CREATE TABLE knowledge_extraction_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_fragment_count INTEGER NOT NULL DEFAULT 0,
    processed_fragment_count INTEGER NOT NULL DEFAULT 0,
    extracted_entity_count INTEGER NOT NULL DEFAULT 0,
    manual_review_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(160),
    error_message VARCHAR(1000),
    correlation_id UUID NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (document_id, organization_id)
        REFERENCES document(id, organization_id),
    FOREIGN KEY (document_version_id, organization_id)
        REFERENCES document_version(id, organization_id),
    FOREIGN KEY (profile_id, organization_id)
        REFERENCES knowledge_extraction_profile(id, organization_id)
);

CREATE TABLE knowledge_extraction_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    extraction_job_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    progress INTEGER NOT NULL,
    message VARCHAR(1000) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (extraction_job_id, organization_id)
        REFERENCES knowledge_extraction_job(id, organization_id) ON DELETE CASCADE,
    CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE retrieval_policy_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE retrieval_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_definition_id UUID NOT NULL REFERENCES retrieval_policy_definition(id)
        ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (policy_definition_id, version_number)
);
ALTER TABLE retrieval_policy_definition
    ADD CONSTRAINT fk_retrieval_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES retrieval_policy_version(id);

CREATE TABLE comparison_strategy_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    strategy_code VARCHAR(160) NOT NULL,
    provider_code VARCHAR(240) NOT NULL,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, strategy_code)
);

CREATE TABLE compliance_condition (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    requirement_id UUID NOT NULL,
    parent_condition_id UUID,
    condition_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    logical_operator_concept_id UUID REFERENCES ontology_concept(id),
    condition_expression_json JSONB NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (requirement_id, organization_id)
        REFERENCES requirement(id, organization_id),
    FOREIGN KEY (parent_condition_id, organization_id)
        REFERENCES compliance_condition(id, organization_id)
);

CREATE TABLE knowledge_snapshot (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    entity_version_cutoff TIMESTAMPTZ NOT NULL,
    evidence_version_cutoff TIMESTAMPTZ NOT NULL,
    ontology_version_id UUID NOT NULL REFERENCES ontology_version(id),
    terminology_snapshot_id UUID NOT NULL,
    policy_versions_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id)
);

CREATE TABLE compliance_analysis_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    analysis_profile_id UUID NOT NULL REFERENCES analysis_profile(id),
    requirement_set_version BIGINT NOT NULL,
    knowledge_snapshot_id UUID NOT NULL,
    retrieval_policy_version_id UUID NOT NULL REFERENCES retrieval_policy_version(id),
    matching_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    comparison_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    confidence_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    prompt_package_version_id UUID NOT NULL REFERENCES prompt_package_version(id),
    model_routing_policy_id UUID NOT NULL REFERENCES policy_version(id),
    total_requirement_count INTEGER NOT NULL DEFAULT 0,
    processed_requirement_count INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    manual_review_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (knowledge_snapshot_id, organization_id)
        REFERENCES knowledge_snapshot(id, organization_id)
);

CREATE TABLE compliance_analysis_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    compliance_job_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    progress INTEGER NOT NULL,
    message VARCHAR(1000) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (compliance_job_id, organization_id)
        REFERENCES compliance_analysis_job(id, organization_id) ON DELETE CASCADE,
    CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE requirement_matching_task (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    compliance_job_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    reranked_candidate_count INTEGER NOT NULL DEFAULT 0,
    selected_evidence_count INTEGER NOT NULL DEFAULT 0,
    evaluation_count INTEGER NOT NULL DEFAULT 0,
    model_run_id UUID,
    error_code VARCHAR(160),
    error_message VARCHAR(1000),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    UNIQUE (compliance_job_id, requirement_id),
    FOREIGN KEY (compliance_job_id, organization_id)
        REFERENCES compliance_analysis_job(id, organization_id) ON DELETE CASCADE,
    FOREIGN KEY (requirement_id, organization_id)
        REFERENCES requirement(id, organization_id)
);

CREATE TABLE compliance_evaluation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    target_scope_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    analysis_job_id UUID NOT NULL,
    suggested_decision_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    final_decision_concept_id UUID REFERENCES ontology_concept(id),
    comparison_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    combined_confidence NUMERIC(10,6) NOT NULL,
    grounding_status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    review_status VARCHAR(80) NOT NULL,
    analysis_profile_id UUID NOT NULL REFERENCES analysis_profile(id),
    retrieval_policy_version_id UUID NOT NULL REFERENCES retrieval_policy_version(id),
    matching_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    comparison_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    confidence_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    prompt_package_version_id UUID NOT NULL REFERENCES prompt_package_version(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (id, organization_id),
    FOREIGN KEY (project_id, organization_id)
        REFERENCES tender_project(id, organization_id),
    FOREIGN KEY (requirement_id, organization_id)
        REFERENCES requirement(id, organization_id),
    FOREIGN KEY (analysis_job_id, organization_id)
        REFERENCES compliance_analysis_job(id, organization_id),
    CHECK (combined_confidence BETWEEN 0 AND 1)
);

CREATE TABLE compliance_evidence_link (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    compliance_evaluation_id UUID NOT NULL,
    evidence_fragment_id UUID NOT NULL,
    evidence_claim_id UUID,
    relation_role_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    relevance_score NUMERIC(10,6) NOT NULL,
    validity_score NUMERIC(10,6) NOT NULL,
    support_strength NUMERIC(10,6) NOT NULL DEFAULT 0,
    contradiction_strength NUMERIC(10,6) NOT NULL DEFAULT 0,
    selected_by_type VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (id, organization_id),
    FOREIGN KEY (compliance_evaluation_id, organization_id)
        REFERENCES compliance_evaluation(id, organization_id) ON DELETE CASCADE,
    FOREIGN KEY (evidence_fragment_id, organization_id)
        REFERENCES evidence_fragment(id, organization_id),
    FOREIGN KEY (evidence_claim_id, organization_id)
        REFERENCES evidence_claim(id, organization_id),
    CHECK (relevance_score BETWEEN 0 AND 1),
    CHECK (validity_score BETWEEN 0 AND 1),
    CHECK (support_strength BETWEEN 0 AND 1),
    CHECK (contradiction_strength BETWEEN 0 AND 1)
);

CREATE TABLE compliance_evaluation_revision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    compliance_evaluation_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    change_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (compliance_evaluation_id, organization_id)
        REFERENCES compliance_evaluation(id, organization_id) ON DELETE CASCADE,
    UNIQUE (compliance_evaluation_id, revision_number)
);

CREATE TABLE entity_revision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entity_id UUID NOT NULL,
    revision_number INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    change_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    UNIQUE (entity_id, revision_number)
);

CREATE TABLE entity_resolution_candidate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_entity_id UUID NOT NULL,
    candidate_entity_id UUID,
    resolution_status VARCHAR(80) NOT NULL,
    score NUMERIC(10,6) NOT NULL,
    signals_json JSONB NOT NULL,
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (source_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    FOREIGN KEY (candidate_entity_id, organization_id)
        REFERENCES knowledge_entity(id, organization_id),
    CHECK (score BETWEEN 0 AND 1)
);

ALTER TABLE expert_feedback
    ADD COLUMN feedback_type_concept_id UUID REFERENCES ontology_concept(id);

CREATE INDEX ix_knowledge_entity_type_name
    ON knowledge_entity (organization_id, entity_type_concept_id, lower(name))
    WHERE valid_until IS NULL;
CREATE INDEX ix_entity_attribute_lookup
    ON entity_attribute (organization_id, attribute_concept_id, numeric_value)
    WHERE valid_until IS NULL;
CREATE INDEX ix_relation_graph_source
    ON knowledge_relation (organization_id, source_entity_id, relation_concept_id)
    WHERE valid_until IS NULL;
CREATE INDEX ix_relation_graph_target
    ON knowledge_relation (organization_id, target_entity_id, relation_concept_id)
    WHERE valid_until IS NULL;
CREATE INDEX ix_capability_owner
    ON capability (organization_id, owner_entity_id, capability_concept_id)
    WHERE valid_until IS NULL;
CREATE INDEX ix_evidence_fragment_document
    ON evidence_fragment (organization_id, document_version_id, page_number);
CREATE INDEX ix_evidence_fragment_normalized
    ON evidence_fragment USING gin (to_tsvector('simple', normalized_text));
CREATE INDEX ix_evidence_claim_subject
    ON evidence_claim (organization_id, subject_entity_id, predicate_concept_id);
CREATE INDEX ix_evidence_validity_latest
    ON evidence_validity_assessment (organization_id, evidence_fragment_id, assessed_at DESC);
CREATE INDEX ix_compliance_job_project
    ON compliance_analysis_job (organization_id, project_id, created_at DESC);
CREATE INDEX ix_compliance_evaluation_requirement
    ON compliance_evaluation (organization_id, requirement_id, created_at DESC);
CREATE INDEX ix_compliance_evidence_evaluation
    ON compliance_evidence_link (compliance_evaluation_id, relevance_score DESC);
CREATE INDEX ix_knowledge_event_job
    ON knowledge_extraction_event (extraction_job_id, occurred_at);
CREATE INDEX ix_compliance_event_job
    ON compliance_analysis_event (compliance_job_id, occurred_at);

-- Extensible global catalog roots and safe baseline decision/evidence concepts.
INSERT INTO ontology_concept (
    id, organization_id, ontology_version_id, parent_concept_id, concept_code,
    name, description, concept_type, metadata_json, active, sort_order, created_at
) VALUES
    ('50000000-0000-0000-0000-000000000001', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'KNOWLEDGE_ENTITY_TYPE',
     'Knowledge entity type', 'Extensible root for entity types', 'ENTITY_TYPE_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 10, now()),
    ('50000000-0000-0000-0000-000000000002', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'KNOWLEDGE_ATTRIBUTE',
     'Knowledge attribute', 'Extensible root for attributes', 'ATTRIBUTE_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 20, now()),
    ('50000000-0000-0000-0000-000000000003', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'KNOWLEDGE_RELATION',
     'Knowledge relation', 'Extensible root for relation predicates', 'RELATION_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 30, now()),
    ('50000000-0000-0000-0000-000000000004', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'CAPABILITY',
     'Capability', 'Extensible root for capabilities', 'CAPABILITY_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 40, now()),
    ('50000000-0000-0000-0000-000000000005', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'COMPLIANCE_DECISION',
     'Compliance decision', 'Extensible root for decision labels', 'DECISION_ROOT',
     '{"extensible":true,"positiveMetadataKey":"positive"}'::jsonb, TRUE, 50, now()),
    ('50000000-0000-0000-0000-000000000006', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'EVIDENCE_VALIDITY_STATUS',
     'Evidence validity status', 'Extensible root for validity results', 'STATUS_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 60, now()),
    ('50000000-0000-0000-0000-000000000007', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'EVIDENCE_ROLE',
     'Evidence role', 'Extensible root for support and contradiction roles', 'ROLE_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 70, now()),
    ('50000000-0000-0000-0000-000000000008', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'DOCUMENT_ROLE',
     'Document role', 'Extensible root for knowledge extraction roles', 'DOCUMENT_ROLE_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 80, now()),
    ('50000000-0000-0000-0000-000000000009', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'CONDITION_TYPE',
     'Condition type', 'Extensible root for requirement conditions', 'CONDITION_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 90, now()),
    ('50000000-0000-0000-0000-00000000000a', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'LOGICAL_OPERATOR',
     'Logical operator', 'Extensible root for composite conditions', 'OPERATOR_ROOT',
     '{"extensible":true}'::jsonb, TRUE, 100, now()),
    ('50000000-0000-0000-0000-00000000000b', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000005',
     'COMPLIANT', 'Compliant', NULL, 'DECISION',
     '{"positive":true,"requiresEvidence":true,"outcome":"SATISFIED"}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-00000000000c', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000005',
     'PARTIALLY_COMPLIANT', 'Partially compliant', NULL, 'DECISION',
     '{"positive":true,"requiresEvidence":true,"requiresReview":true,"outcome":"PARTIAL"}'::jsonb, TRUE, 2, now()),
    ('50000000-0000-0000-0000-00000000000d', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000005',
     'NON_COMPLIANT', 'Non-compliant', NULL, 'DECISION',
     '{"positive":false,"outcome":"NOT_SATISFIED"}'::jsonb, TRUE, 3, now()),
    ('50000000-0000-0000-0000-00000000000e', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000005',
     'INSUFFICIENT_INFORMATION', 'Insufficient information', NULL, 'DECISION',
     '{"positive":false,"requiresReview":true,"outcome":"INDETERMINATE"}'::jsonb, TRUE, 4, now()),
    ('50000000-0000-0000-0000-00000000000f', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000006',
     'VALID', 'Valid', NULL, 'EVIDENCE_VALIDITY_STATUS',
     '{"usable":true}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-000000000010', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000006',
     'EXPIRED', 'Expired', NULL, 'EVIDENCE_VALIDITY_STATUS',
     '{"usable":false}'::jsonb, TRUE, 2, now()),
    ('50000000-0000-0000-0000-000000000011', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000006',
     'UNVERIFIED', 'Unverified', NULL, 'EVIDENCE_VALIDITY_STATUS',
     '{"usable":false}'::jsonb, TRUE, 3, now()),
    ('50000000-0000-0000-0000-000000000012', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000007',
     'SUPPORTS', 'Supports', NULL, 'EVIDENCE_ROLE',
     '{"polarity":1}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-000000000013', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000007',
     'CONTRADICTS', 'Contradicts', NULL, 'EVIDENCE_ROLE',
     '{"polarity":-1}'::jsonb, TRUE, 2, now()),
    ('50000000-0000-0000-0000-000000000014', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000008',
     'GENERIC_KNOWLEDGE_DOCUMENT', 'Generic knowledge document', NULL, 'DOCUMENT_ROLE',
     '{"fallback":true}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-000000000015', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000009',
     'ATOMIC_CONDITION', 'Atomic condition', NULL, 'CONDITION_TYPE',
     '{}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-000000000016', NULL,
     '40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-00000000000a',
     'ALL', 'All conditions', NULL, 'LOGICAL_OPERATOR',
     '{"provider":"ALL"}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-000000000017', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'GROUNDED',
     'Grounded', NULL, 'GROUNDING_STATUS',
     '{"grounded":true}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-000000000018', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'UNGROUNDED',
     'Ungrounded', NULL, 'GROUNDING_STATUS',
     '{"grounded":false}'::jsonb, TRUE, 2, now()),
    ('50000000-0000-0000-0000-000000000019', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'ENTITY_UPDATED',
     'Entity updated', NULL, 'CHANGE_TYPE',
     '{"changeProvider":"ENTITY_UPDATED"}'::jsonb, TRUE, 1, now()),
    ('50000000-0000-0000-0000-00000000001a', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'ENTITY_MERGED',
     'Entity merged', NULL, 'CHANGE_TYPE',
     '{"changeProvider":"ENTITY_MERGED"}'::jsonb, TRUE, 2, now()),
    ('50000000-0000-0000-0000-00000000001b', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'ENTITY_SPLIT',
     'Entity split', NULL, 'CHANGE_TYPE',
     '{"changeProvider":"ENTITY_SPLIT"}'::jsonb, TRUE, 3, now()),
    ('50000000-0000-0000-0000-00000000001c', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'EVALUATION_REVIEWED',
     'Evaluation reviewed', NULL, 'CHANGE_TYPE',
     '{"changeProvider":"EVALUATION_REVIEWED"}'::jsonb, TRUE, 4, now()),
    ('50000000-0000-0000-0000-00000000001d', NULL,
     '40000000-0000-0000-0000-000000000002', NULL, 'EVIDENCE_LINK_CHANGED',
     'Evidence link changed', NULL, 'CHANGE_TYPE',
     '{"changeProvider":"EVIDENCE_LINK_CHANGED"}'::jsonb, TRUE, 5, now());

INSERT INTO retrieval_policy_definition (
    id, organization_id, policy_code, scope, name, description,
    created_at, updated_at
) VALUES (
    '50000000-0000-0000-0000-000000000020', NULL, 'BASE_EVIDENCE_RETRIEVAL',
    'GLOBAL', 'Base evidence retrieval', 'Policy-driven staged candidate retrieval',
    now(), now()
);
INSERT INTO retrieval_policy_version (
    id, organization_id, policy_definition_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES (
    '50000000-0000-0000-0000-000000000021', NULL,
    '50000000-0000-0000-0000-000000000020', 1,
    '{
      "candidateLimits":{"metadata":100,"lexical":50,"graph":30,"reranking":15},
      "signals":{"ontology":0.25,"lexical":0.25,"attribute":0.20,
                 "evidenceValidity":0.20,"historicalFeedback":0.10},
      "minimumValidityScore":0.35,
      "graphDepth":2
    }'::jsonb, 'ACTIVE', 'platform', now(), now()
);
UPDATE retrieval_policy_definition
SET active_version_id = '50000000-0000-0000-0000-000000000021'
WHERE id = '50000000-0000-0000-0000-000000000020';

INSERT INTO policy_definition (
    id, organization_id, policy_code, policy_type, name, scope, created_at, updated_at
) VALUES
    ('50000000-0000-0000-0000-000000000030', NULL, 'BASE_KNOWLEDGE_EXTRACTION',
     'KNOWLEDGE_EXTRACTION', 'Base knowledge extraction', 'GLOBAL', now(), now()),
    ('50000000-0000-0000-0000-000000000031', NULL, 'BASE_EVIDENCE_VALIDITY',
     'EVIDENCE_VALIDITY', 'Base evidence validity', 'GLOBAL', now(), now()),
    ('50000000-0000-0000-0000-000000000032', NULL, 'BASE_SOURCE_AUTHORITY',
     'SOURCE_AUTHORITY', 'Base source authority', 'GLOBAL', now(), now()),
    ('50000000-0000-0000-0000-000000000033', NULL, 'BASE_COMPLIANCE_MATCHING',
     'COMPLIANCE_MATCHING', 'Base compliance matching', 'GLOBAL', now(), now()),
    ('50000000-0000-0000-0000-000000000034', NULL, 'BASE_COMPARISON',
     'COMPARISON', 'Base comparison selection', 'GLOBAL', now(), now()),
    ('50000000-0000-0000-0000-000000000035', NULL, 'BASE_COMPLIANCE_CONFIDENCE',
     'COMPLIANCE_CONFIDENCE', 'Base compliance confidence', 'GLOBAL', now(), now());

INSERT INTO policy_version (
    id, organization_id, policy_definition_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES
    ('50000000-0000-0000-0000-000000000040', NULL,
     '50000000-0000-0000-0000-000000000030', 1,
     '{"requireSource":true,"unknownConceptAction":"CANDIDATE","minimumConfidence":0.45}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000041', NULL,
     '50000000-0000-0000-0000-000000000031', 1,
     '{"factors":{"notExpired":0.35,"parserQuality":0.15,"ocrQuality":0.10,
                  "verified":0.20,"authority":0.20},"minimumUsableScore":0.55}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000042', NULL,
     '50000000-0000-0000-0000-000000000032', 1,
     '{"defaultScore":0.40,"sourceScores":{},"issuerOverrides":{}}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000043', NULL,
     '50000000-0000-0000-0000-000000000033', 1,
     '{"minimumRelevance":0.35,"contradictionReviewThreshold":0.25}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000044', NULL,
     '50000000-0000-0000-0000-000000000034', 1,
     '{"providers":[],"manualWhenUnsupported":true}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000045', NULL,
     '50000000-0000-0000-0000-000000000035', 1,
     '{"weights":{"relevance":0.20,"validity":0.20,"authority":0.15,
                  "grounding":0.15,"deterministic":0.15,"entityResolution":0.05,
                  "freshness":0.05,"historicalAcceptance":0.05},
       "penalties":{"contradiction":0.30,"missingEvidence":0.50},
       "reviewBelow":0.80}'::jsonb,
     'ACTIVE', 'platform', now(), now());

INSERT INTO comparison_strategy_definition (
    id, organization_id, strategy_code, provider_code, configuration_json,
    active, created_at, updated_at
) VALUES
    ('50000000-0000-0000-0000-000000000050', NULL, 'NUMERIC_THRESHOLD',
     'numeric-threshold', '{}', TRUE, now(), now()),
    ('50000000-0000-0000-0000-000000000051', NULL, 'NUMERIC_RANGE',
     'numeric-range', '{}', TRUE, now(), now()),
    ('50000000-0000-0000-0000-000000000052', NULL, 'DATE_VALIDITY',
     'date-validity', '{}', TRUE, now(), now()),
    ('50000000-0000-0000-0000-000000000053', NULL, 'BOOLEAN_EXISTENCE',
     'boolean-existence', '{}', TRUE, now(), now()),
    ('50000000-0000-0000-0000-000000000054', NULL, 'MANUAL_ONLY',
     'manual-only', '{}', TRUE, now(), now());

INSERT INTO output_schema_definition (
    id, organization_id, schema_code, task_type, scope, description,
    created_at, updated_at
) VALUES
    ('50000000-0000-0000-0000-000000000060', NULL, 'BASE_KNOWLEDGE_V1',
     'KNOWLEDGE_EXTRACTION', 'GLOBAL', 'Extensible evidence-grounded knowledge contract',
     now(), now()),
    ('50000000-0000-0000-0000-000000000061', NULL, 'BASE_COMPLIANCE_V1',
     'COMPLIANCE_EVALUATION', 'GLOBAL', 'Evidence-id constrained compliance contract',
     now(), now());

INSERT INTO output_schema_version (
    id, organization_id, schema_definition_id, version_number, json_schema, status,
    approved_by, approved_at, created_at
) VALUES
    ('50000000-0000-0000-0000-000000000062', NULL,
     '50000000-0000-0000-0000-000000000060', 1,
     '{
       "type":"object","additionalProperties":false,
       "required":["entities","relations","capabilities","warnings"],
       "properties":{
         "entities":{"type":"array","items":{"type":"object",
           "required":["temporaryId","entityTypeConcept","name","sourceFragments"],
           "properties":{
             "temporaryId":{"type":"string"},"entityTypeConcept":{"type":"string"},
             "name":{"type":"string"},"description":{"type":["string","null"]},
             "attributes":{"type":"array"},"sourceFragments":{"type":"array","minItems":1}
           }}},
         "relations":{"type":"array"},"capabilities":{"type":"array"},
         "warnings":{"type":"array"}
       }
     }'::jsonb, 'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000063', NULL,
     '50000000-0000-0000-0000-000000000061', 1,
     '{
       "type":"object","additionalProperties":false,
       "required":["recommendedDecisionConcept","summary","conditionEvaluations",
                   "missingInformation","warnings","confidence","requiresManualReview"],
       "properties":{
         "recommendedDecisionConcept":{"type":"string"},"summary":{"type":"string"},
         "conditionEvaluations":{"type":"array"},"missingInformation":{"type":"array"},
         "warnings":{"type":"array"},"confidence":{"type":"number","minimum":0,"maximum":1},
         "requiresManualReview":{"type":"boolean"}
       }
     }'::jsonb, 'ACTIVE', 'platform', now(), now());
UPDATE output_schema_definition
SET active_version_id = CASE id
    WHEN '50000000-0000-0000-0000-000000000060'
        THEN '50000000-0000-0000-0000-000000000062'::uuid
    ELSE '50000000-0000-0000-0000-000000000063'::uuid
END
WHERE id IN (
    '50000000-0000-0000-0000-000000000060',
    '50000000-0000-0000-0000-000000000061'
);

INSERT INTO prompt_component (
    id, organization_id, component_code, component_type, content_template, scope,
    metadata_json, created_at, updated_at
) VALUES
    ('50000000-0000-0000-0000-000000000070', NULL, 'KNOWLEDGE_EXTRACTION_TASK',
     'TASK',
     'Extract dynamic entities, attributes, relations and capabilities. Every value must cite a supplied evidence fragment. Unknown concepts must be returned as warnings, never invented.',
     'GLOBAL', '{}', now(), now()),
    ('50000000-0000-0000-0000-000000000071', NULL, 'COMPLIANCE_EVALUATION_TASK',
     'TASK',
     'Evaluate only unresolved semantic conditions using supplied evidence. Never emit an evidence ID absent from the request. Preserve contradictions and uncertainty.',
     'GLOBAL', '{}', now(), now());

INSERT INTO prompt_package (
    id, organization_id, package_code, task_type, scope, created_at, updated_at
) VALUES
    ('50000000-0000-0000-0000-000000000072', NULL, 'BASE_KNOWLEDGE_EXTRACTION',
     'KNOWLEDGE_EXTRACTION', 'GLOBAL', now(), now()),
    ('50000000-0000-0000-0000-000000000073', NULL, 'BASE_COMPLIANCE_EVALUATION',
     'COMPLIANCE_EVALUATION', 'GLOBAL', now(), now());

INSERT INTO prompt_package_version (
    id, organization_id, prompt_package_id, version_number,
    component_configuration_json, output_schema_id, status,
    approved_by, approved_at, created_at
) VALUES
    ('50000000-0000-0000-0000-000000000074', NULL,
     '50000000-0000-0000-0000-000000000072', 1,
     '{"components":["BASE_SAFETY","KNOWLEDGE_EXTRACTION_TASK"]}'::jsonb,
     '50000000-0000-0000-0000-000000000062', 'ACTIVE', 'platform', now(), now()),
    ('50000000-0000-0000-0000-000000000075', NULL,
     '50000000-0000-0000-0000-000000000073', 1,
     '{"components":["BASE_SAFETY","COMPLIANCE_EVALUATION_TASK"]}'::jsonb,
     '50000000-0000-0000-0000-000000000063', 'ACTIVE', 'platform', now(), now());
UPDATE prompt_package
SET active_version_id = CASE id
    WHEN '50000000-0000-0000-0000-000000000072'
        THEN '50000000-0000-0000-0000-000000000074'::uuid
    ELSE '50000000-0000-0000-0000-000000000075'::uuid
END
WHERE id IN (
    '50000000-0000-0000-0000-000000000072',
    '50000000-0000-0000-0000-000000000073'
);

INSERT INTO ui_configuration (
    id, organization_id, configuration_code, configuration_json, active,
    created_at, updated_at
) VALUES
    ('50000000-0000-0000-0000-000000000080', NULL, 'ENTITY_PROFILE',
     '{
       "tabs":[
         {"key":"overview","label":"Genel","sections":["identity","attributes"]},
         {"key":"capabilities","label":"Yetkinlikler","sections":["capabilities"]},
         {"key":"evidence","label":"Kanıt belgeleri","sections":["evidence"]},
         {"key":"relations","label":"İlişkiler","sections":["relations"]},
         {"key":"history","label":"Geçmiş","sections":["revisions"]}
       ],
       "valueRenderers":{"TEXT":"text","NUMBER":"number","RANGE":"range",
         "BOOLEAN":"boolean","DATE":"date","DATETIME":"datetime","DURATION":"duration",
         "QUANTITY":"quantity","REFERENCE":"entity-link","ENUM_CONCEPT":"concept","JSON":"json",
         "_unsupported":"metadata"}
     }'::jsonb, TRUE, now(), now()),
    ('50000000-0000-0000-0000-000000000081', NULL, 'COMPLIANCE_MATRIX',
     '{
       "columns":[
         {"key":"requirementCode","label":"Requirement code","type":"TEXT"},
         {"key":"requirementText","label":"Requirement","type":"TEXT"},
         {"key":"requirementConcept","label":"Requirement concept","type":"CONCEPT"},
         {"key":"targetEntity","label":"Hedef entity","type":"ENTITY"},
         {"key":"suggestedDecision","label":"Önerilen karar","type":"CONCEPT"},
         {"key":"finalDecision","label":"Final karar","type":"CONCEPT"},
         {"key":"evidenceCount","label":"Evidence","type":"NUMBER"},
         {"key":"contradictionCount","label":"Contradiction","type":"NUMBER"},
         {"key":"combinedConfidence","label":"Confidence","type":"PERCENT"},
         {"key":"reviewStatus","label":"Review","type":"STATUS"}
       ]
     }'::jsonb, TRUE, now(), now());

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'evidence_fragment', 'knowledge_entity', 'entity_attribute',
        'knowledge_relation', 'capability', 'capability_evidence', 'evidence_claim',
        'evidence_validity_assessment', 'source_authority_profile', 'candidate_concept',
        'knowledge_extraction_profile', 'knowledge_extraction_job',
        'knowledge_extraction_event', 'retrieval_policy_definition',
        'retrieval_policy_version', 'comparison_strategy_definition',
        'compliance_condition', 'knowledge_snapshot', 'compliance_analysis_job',
        'compliance_analysis_event', 'requirement_matching_task',
        'compliance_evaluation', 'compliance_evidence_link',
        'compliance_evaluation_revision', 'entity_revision',
        'entity_resolution_candidate'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        IF table_name IN (
            'retrieval_policy_definition', 'retrieval_policy_version',
            'comparison_strategy_definition'
        ) THEN
            EXECUTE format(
                'CREATE POLICY tenant_or_global_%I ON %I USING (organization_id IS NULL OR organization_id = app_current_organization_id()) WITH CHECK (organization_id IS NULL OR organization_id = app_current_organization_id())',
                table_name, table_name
            );
        ELSE
            EXECUTE format(
                'CREATE POLICY tenant_only_%I ON %I USING (organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
                table_name, table_name
            );
        END IF;
    END LOOP;
END
$$;
