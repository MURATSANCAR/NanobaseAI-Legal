-- Sprint 6: dynamic, versioned risk, ambiguity, conflict and change-impact foundation.
-- Business taxonomies and thresholds are configuration/ontology data, not SQL constraints.

CREATE TABLE risk_taxonomy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    name VARCHAR(240) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_taxonomy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    risk_taxonomy_id UUID NOT NULL REFERENCES risk_taxonomy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    ontology_version_id UUID NOT NULL REFERENCES ontology_version(id),
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (risk_taxonomy_id, version_number)
);
ALTER TABLE risk_taxonomy ADD CONSTRAINT fk_risk_taxonomy_active_version
    FOREIGN KEY (active_version_id) REFERENCES risk_taxonomy_version(id);

CREATE TABLE severity_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE severity_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    severity_policy_id UUID NOT NULL REFERENCES severity_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (severity_policy_id, version_number)
);
ALTER TABLE severity_policy ADD CONSTRAINT fk_severity_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES severity_policy_version(id);

CREATE TABLE document_authority_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    name VARCHAR(240) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_authority_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    document_authority_policy_id UUID NOT NULL
        REFERENCES document_authority_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_authority_policy_id, version_number)
);
ALTER TABLE document_authority_policy
    ADD CONSTRAINT fk_document_authority_active_version
    FOREIGN KEY (active_version_id) REFERENCES document_authority_policy_version(id);

CREATE TABLE risk_analysis_profile (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    analysis_profile_id UUID REFERENCES analysis_profile(id),
    risk_taxonomy_version_id UUID NOT NULL REFERENCES risk_taxonomy_version(id),
    ontology_version_id UUID NOT NULL REFERENCES ontology_version(id),
    terminology_snapshot_id UUID REFERENCES terminology_snapshot(id),
    risk_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    conflict_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    ambiguity_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    impact_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    confidence_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    severity_policy_version_id UUID NOT NULL REFERENCES severity_policy_version(id),
    prompt_package_version_id UUID NOT NULL REFERENCES prompt_package_version(id),
    model_routing_policy_id UUID NOT NULL REFERENCES policy_version(id),
    document_authority_policy_id UUID NOT NULL
        REFERENCES document_authority_policy_version(id),
    snapshot_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_analysis_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    status VARCHAR(50) NOT NULL,
    risk_analysis_profile_id UUID NOT NULL REFERENCES risk_analysis_profile(id),
    knowledge_snapshot_id UUID REFERENCES knowledge_snapshot(id),
    requirement_set_version BIGINT NOT NULL DEFAULT 0,
    compliance_snapshot_id UUID,
    total_candidate_count INTEGER NOT NULL DEFAULT 0,
    processed_candidate_count INTEGER NOT NULL DEFAULT 0,
    risk_count INTEGER NOT NULL DEFAULT 0,
    ambiguity_count INTEGER NOT NULL DEFAULT 0,
    conflict_count INTEGER NOT NULL DEFAULT 0,
    manual_review_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE risk_analysis_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    risk_analysis_job_id UUID NOT NULL REFERENCES risk_analysis_job(id) ON DELETE CASCADE,
    event_type VARCHAR(160) NOT NULL,
    progress INTEGER NOT NULL,
    message VARCHAR(1000) NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE risk_analysis_task (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    risk_analysis_job_id UUID NOT NULL REFERENCES risk_analysis_job(id) ON DELETE CASCADE,
    target_entity_type VARCHAR(160) NOT NULL,
    target_entity_id UUID NOT NULL,
    task_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    status VARCHAR(50) NOT NULL,
    model_run_id UUID REFERENCES model_run(id),
    result_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(160),
    error_message VARCHAR(1000),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE risk_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    risk_code VARCHAR(160) NOT NULL,
    risk_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    source_entity_type VARCHAR(160) NOT NULL,
    source_entity_id UUID NOT NULL,
    risk_analysis_profile_id UUID NOT NULL REFERENCES risk_analysis_profile(id),
    probability_score NUMERIC(10,6),
    impact_score NUMERIC(10,6),
    exposure_score NUMERIC(10,6),
    severity_concept_id UUID REFERENCES ontology_concept(id),
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    owner_user_id VARCHAR(255),
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, project_id, risk_code)
);

CREATE TABLE risk_source (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    risk_id UUID NOT NULL REFERENCES risk_record(id) ON DELETE CASCADE,
    source_type VARCHAR(160) NOT NULL,
    source_id UUID NOT NULL,
    document_id UUID REFERENCES document(id),
    document_version_id UUID REFERENCES document_version(id),
    clause_id UUID REFERENCES clause(id),
    requirement_id UUID REFERENCES requirement(id),
    compliance_evaluation_id UUID REFERENCES compliance_evaluation(id),
    evidence_fragment_id UUID REFERENCES evidence_fragment(id),
    page_number INTEGER,
    source_text TEXT NOT NULL,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_role_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_factor (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    risk_id UUID NOT NULL REFERENCES risk_record(id) ON DELETE CASCADE,
    factor_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    factor_value JSONB NOT NULL,
    effect_score NUMERIC(10,6) NOT NULL,
    explanation TEXT NOT NULL,
    source_reference_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_revision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    risk_id UUID NOT NULL REFERENCES risk_record(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    change_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (risk_id, revision_number)
);

CREATE TABLE ambiguity_finding (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    entity_type VARCHAR(160) NOT NULL,
    entity_id UUID NOT NULL,
    ambiguity_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    analysis_profile_id UUID NOT NULL REFERENCES risk_analysis_profile(id),
    description TEXT NOT NULL,
    confidence NUMERIC(10,6) NOT NULL,
    severity_concept_id UUID REFERENCES ontology_concept(id),
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE ambiguity_source (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    ambiguity_finding_id UUID NOT NULL REFERENCES ambiguity_finding(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES document(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    clause_id UUID REFERENCES clause(id),
    requirement_id UUID REFERENCES requirement(id),
    source_text TEXT NOT NULL,
    page_number INTEGER,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ambiguity_interpretation (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    ambiguity_finding_id UUID NOT NULL REFERENCES ambiguity_finding(id) ON DELETE CASCADE,
    interpretation_text TEXT NOT NULL,
    interpretation_attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    supporting_source_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE conflict_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    conflict_code VARCHAR(160) NOT NULL,
    conflict_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    left_entity_type VARCHAR(160) NOT NULL,
    left_entity_id UUID NOT NULL,
    right_entity_type VARCHAR(160) NOT NULL,
    right_entity_id UUID NOT NULL,
    analysis_profile_id UUID NOT NULL REFERENCES risk_analysis_profile(id),
    comparison_strategy_code VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    suggested_resolution_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    severity_concept_id UUID REFERENCES ontology_concept(id),
    review_status VARCHAR(80) NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (left_entity_id <> right_entity_id),
    UNIQUE (organization_id, project_id, conflict_code)
);

CREATE TABLE conflict_source (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    conflict_id UUID NOT NULL REFERENCES conflict_record(id) ON DELETE CASCADE,
    side_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    document_id UUID NOT NULL REFERENCES document(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    clause_id UUID REFERENCES clause(id),
    requirement_id UUID REFERENCES requirement(id),
    evidence_fragment_id UUID REFERENCES evidence_fragment(id),
    source_text TEXT NOT NULL,
    page_number INTEGER,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    authority_score NUMERIC(10,6),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE conflict_factor (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    conflict_id UUID NOT NULL REFERENCES conflict_record(id) ON DELETE CASCADE,
    factor_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    effect_score NUMERIC(10,6) NOT NULL,
    description TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE conflict_revision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    conflict_id UUID NOT NULL REFERENCES conflict_record(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    snapshot_json JSONB NOT NULL,
    change_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (conflict_id, revision_number)
);

CREATE TABLE requirement_dependency (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_requirement_id UUID NOT NULL REFERENCES requirement(id),
    target_requirement_id UUID NOT NULL REFERENCES requirement(id),
    dependency_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    dependency_attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (source_requirement_id <> target_requirement_id),
    UNIQUE (source_requirement_id, target_requirement_id, dependency_concept_id)
);

CREATE TABLE document_change_set (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    base_document_version_id UUID NOT NULL REFERENCES document_version(id),
    target_document_version_id UUID NOT NULL REFERENCES document_version(id),
    change_profile_id UUID NOT NULL REFERENCES policy_version(id),
    status VARCHAR(50) NOT NULL,
    summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (base_document_version_id <> target_document_version_id)
);

CREATE TABLE document_change_item (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    change_set_id UUID NOT NULL REFERENCES document_change_set(id) ON DELETE CASCADE,
    change_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    base_clause_id UUID REFERENCES clause(id),
    target_clause_id UUID REFERENCES clause(id),
    base_requirement_id UUID REFERENCES requirement(id),
    target_requirement_id UUID REFERENCES requirement(id),
    similarity_score NUMERIC(10,6) NOT NULL,
    change_attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE impact_analysis_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    change_set_id UUID NOT NULL REFERENCES document_change_set(id),
    status VARCHAR(50) NOT NULL,
    impact_policy_version_id UUID NOT NULL REFERENCES policy_version(id),
    total_item_count INTEGER NOT NULL DEFAULT 0,
    processed_item_count INTEGER NOT NULL DEFAULT 0,
    affected_requirement_count INTEGER NOT NULL DEFAULT 0,
    affected_evaluation_count INTEGER NOT NULL DEFAULT 0,
    affected_risk_count INTEGER NOT NULL DEFAULT 0,
    affected_report_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE impact_analysis_result (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    impact_analysis_job_id UUID NOT NULL REFERENCES impact_analysis_job(id) ON DELETE CASCADE,
    entity_type VARCHAR(160) NOT NULL,
    entity_id UUID NOT NULL,
    impact_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    reason_codes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence NUMERIC(10,6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE impact_analysis_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    impact_analysis_job_id UUID NOT NULL REFERENCES impact_analysis_job(id) ON DELETE CASCADE,
    event_type VARCHAR(160) NOT NULL,
    progress INTEGER NOT NULL,
    message VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE analysis_staleness_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entity_type VARCHAR(160) NOT NULL,
    entity_id UUID NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    trigger_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    trigger_entity_id UUID NOT NULL,
    reason TEXT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE risk_propagation_candidate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_risk_id UUID NOT NULL REFERENCES risk_record(id),
    target_entity_type VARCHAR(160) NOT NULL,
    target_entity_id UUID NOT NULL,
    propagation_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    path_json JSONB NOT NULL,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE mitigation_catalog (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    name VARCHAR(240) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE mitigation_catalog_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    mitigation_catalog_id UUID NOT NULL REFERENCES mitigation_catalog(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (mitigation_catalog_id, version_number)
);
ALTER TABLE mitigation_catalog ADD CONSTRAINT fk_mitigation_catalog_active_version
    FOREIGN KEY (active_version_id) REFERENCES mitigation_catalog_version(id);

CREATE TABLE mitigation_pattern (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    catalog_version_id UUID NOT NULL REFERENCES mitigation_catalog_version(id) ON DELETE CASCADE,
    applicable_risk_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    applicable_context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    action_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    template_id UUID,
    priority_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE mitigation_candidate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    risk_id UUID NOT NULL REFERENCES risk_record(id) ON DELETE CASCADE,
    pattern_id UUID REFERENCES mitigation_pattern(id),
    action_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    description TEXT NOT NULL,
    confidence NUMERIC(10,6) NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE clarification_strategy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    strategy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, strategy_code)
);

CREATE TABLE clarification_strategy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    clarification_strategy_id UUID NOT NULL REFERENCES clarification_strategy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    prompt_package_version_id UUID NOT NULL REFERENCES prompt_package_version(id),
    output_schema_version_id UUID NOT NULL REFERENCES output_schema_version(id),
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (clarification_strategy_id, version_number)
);
ALTER TABLE clarification_strategy
    ADD CONSTRAINT fk_clarification_strategy_active_version
    FOREIGN KEY (active_version_id) REFERENCES clarification_strategy_version(id);

CREATE TABLE clarification_candidate (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    source_entity_type VARCHAR(160) NOT NULL,
    source_entity_id UUID NOT NULL,
    strategy_version_id UUID NOT NULL REFERENCES clarification_strategy_version(id),
    question TEXT NOT NULL,
    reason TEXT NOT NULL,
    source_ids_json JSONB NOT NULL,
    priority_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    requires_legal_review BOOLEAN NOT NULL,
    review_status VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_risk_job_project ON risk_analysis_job (organization_id, project_id, created_at DESC);
CREATE INDEX ix_risk_record_project ON risk_record (organization_id, project_id, created_at DESC);
CREATE INDEX ix_risk_source_risk ON risk_source (organization_id, risk_id);
CREATE INDEX ix_ambiguity_project ON ambiguity_finding (organization_id, project_id, created_at DESC);
CREATE INDEX ix_conflict_project ON conflict_record (organization_id, project_id, created_at DESC);
CREATE INDEX ix_conflict_pair ON conflict_record (organization_id, left_entity_id, right_entity_id);
CREATE INDEX ix_requirement_dependency_source ON requirement_dependency (organization_id, source_requirement_id);
CREATE INDEX ix_requirement_dependency_target ON requirement_dependency (organization_id, target_requirement_id);
CREATE INDEX ix_change_set_project ON document_change_set (organization_id, project_id, created_at DESC);
CREATE INDEX ix_change_item_set ON document_change_item (organization_id, change_set_id);
CREATE INDEX ix_impact_result_job ON impact_analysis_result (organization_id, impact_analysis_job_id);
CREATE INDEX ix_staleness_entity ON analysis_staleness_record
    (organization_id, entity_type, entity_id, detected_at DESC);
CREATE UNIQUE INDEX uq_open_staleness
    ON analysis_staleness_record (organization_id, entity_type, entity_id, status_concept_id)
    WHERE resolved_at IS NULL;

-- Generic global bootstrap. All labels, thresholds, mappings and authority behavior
-- can be replaced by organization/sector/project-scoped approved versions.
INSERT INTO ontology_concept (
    id, organization_id, ontology_version_id, concept_code, name, concept_type,
    metadata_json, active, sort_order, created_at
) VALUES
    ('60000000-0000-0000-0000-000000000001', NULL,
     '40000000-0000-0000-0000-000000000002', 'RISK_CANDIDATE', 'Risk candidate',
     'RISK', '{"bootstrap":true}', TRUE, 100, now()),
    ('60000000-0000-0000-0000-000000000002', NULL,
     '40000000-0000-0000-0000-000000000002', 'AMBIGUITY_CANDIDATE', 'Ambiguity candidate',
     'AMBIGUITY', '{"bootstrap":true}', TRUE, 101, now()),
    ('60000000-0000-0000-0000-000000000003', NULL,
     '40000000-0000-0000-0000-000000000002', 'CONFLICT_CANDIDATE', 'Conflict candidate',
     'CONFLICT', '{"bootstrap":true}', TRUE, 102, now()),
    ('60000000-0000-0000-0000-000000000004', NULL,
     '40000000-0000-0000-0000-000000000002', 'REVIEW_REQUIRED', 'Review required',
     'WORKFLOW_STATUS', '{"bootstrap":true}', TRUE, 103, now()),
    ('60000000-0000-0000-0000-000000000005', NULL,
     '40000000-0000-0000-0000-000000000002', 'SOURCE_SUPPORT', 'Supporting source',
     'SOURCE_ROLE', '{"bootstrap":true}', TRUE, 104, now()),
    ('60000000-0000-0000-0000-000000000006', NULL,
     '40000000-0000-0000-0000-000000000002', 'SOURCE_LEFT', 'Left source',
     'SOURCE_SIDE', '{"bootstrap":true}', TRUE, 105, now()),
    ('60000000-0000-0000-0000-000000000007', NULL,
     '40000000-0000-0000-0000-000000000002', 'SOURCE_RIGHT', 'Right source',
     'SOURCE_SIDE', '{"bootstrap":true}', TRUE, 106, now()),
    ('60000000-0000-0000-0000-000000000008', NULL,
     '40000000-0000-0000-0000-000000000002', 'REVISION_CREATED', 'Revision created',
     'CHANGE_TYPE', '{"bootstrap":true}', TRUE, 107, now()),
    ('60000000-0000-0000-0000-000000000009', NULL,
     '40000000-0000-0000-0000-000000000002', 'MODIFIED', 'Modified',
     'CHANGE_TYPE', '{"bootstrap":true}', TRUE, 108, now()),
    ('60000000-0000-0000-0000-000000000010', NULL,
     '40000000-0000-0000-0000-000000000002', 'ADDED', 'Added',
     'CHANGE_TYPE', '{"bootstrap":true}', TRUE, 109, now()),
    ('60000000-0000-0000-0000-000000000011', NULL,
     '40000000-0000-0000-0000-000000000002', 'REMOVED', 'Removed',
     'CHANGE_TYPE', '{"bootstrap":true}', TRUE, 110, now()),
    ('60000000-0000-0000-0000-000000000012', NULL,
     '40000000-0000-0000-0000-000000000002', 'REQUIRES_REANALYSIS', 'Requires reanalysis',
     'IMPACT', '{"bootstrap":true}', TRUE, 111, now()),
    ('60000000-0000-0000-0000-000000000013', NULL,
     '40000000-0000-0000-0000-000000000002', 'STALE', 'Stale',
     'STALENESS_STATUS', '{"bootstrap":true}', TRUE, 112, now()),
    ('60000000-0000-0000-0000-000000000014', NULL,
     '40000000-0000-0000-0000-000000000002', 'SOURCE_CHANGED', 'Source changed',
     'STALENESS_TRIGGER', '{"bootstrap":true}', TRUE, 113, now()),
    ('60000000-0000-0000-0000-000000000015', NULL,
     '40000000-0000-0000-0000-000000000002', 'PROPAGATED_RISK_CANDIDATE', 'Propagated risk candidate',
     'RISK', '{"bootstrap":true}', TRUE, 114, now()),
    ('60000000-0000-0000-0000-000000000016', NULL,
     '40000000-0000-0000-0000-000000000002', 'ANALYZE_REQUIREMENT', 'Analyze requirement',
     'TASK_TYPE', '{"bootstrap":true}', TRUE, 115, now()),
    ('60000000-0000-0000-0000-000000000017', NULL,
     '40000000-0000-0000-0000-000000000002', 'REQUEST_CLARIFICATION', 'Request clarification',
     'ACTION', '{"bootstrap":true}', TRUE, 116, now()),
    ('60000000-0000-0000-0000-000000000018', NULL,
     '40000000-0000-0000-0000-000000000002', 'UNCHANGED', 'Unchanged',
     'CHANGE_TYPE', '{"bootstrap":true}', TRUE, 117, now());

INSERT INTO risk_taxonomy (
    id, organization_id, name, scope, status, created_at, updated_at
) VALUES (
    '60000000-0000-0000-0000-000000000100', NULL,
    'Global extensible risk baseline', 'GLOBAL', 'ACTIVE', now(), now()
);
INSERT INTO risk_taxonomy_version (
    id, organization_id, risk_taxonomy_id, version_number, ontology_version_id,
    configuration_json, status, approved_by, approved_at, created_at
) VALUES (
    '60000000-0000-0000-0000-000000000101', NULL,
    '60000000-0000-0000-0000-000000000100', 1,
    '40000000-0000-0000-0000-000000000002',
    '{"fallbackConceptCode":"RISK_CANDIDATE","conceptMappings":[]}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE risk_taxonomy SET active_version_id = '60000000-0000-0000-0000-000000000101'
WHERE id = '60000000-0000-0000-0000-000000000100';

INSERT INTO severity_policy (
    id, organization_id, policy_code, scope, created_at, updated_at
) VALUES (
    '60000000-0000-0000-0000-000000000110', NULL,
    'GLOBAL_DYNAMIC_SEVERITY', 'GLOBAL', now(), now()
);
INSERT INTO severity_policy_version (
    id, organization_id, severity_policy_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES (
    '60000000-0000-0000-0000-000000000111', NULL,
    '60000000-0000-0000-0000-000000000110', 1,
    '{"levels":[{"conceptCode":"REVIEW_REQUIRED","minimum":0.0}]}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE severity_policy SET active_version_id = '60000000-0000-0000-0000-000000000111'
WHERE id = '60000000-0000-0000-0000-000000000110';

INSERT INTO document_authority_policy (
    id, organization_id, name, scope, created_at, updated_at
) VALUES (
    '60000000-0000-0000-0000-000000000120', NULL,
    'Manual authority baseline', 'GLOBAL', now(), now()
);
INSERT INTO document_authority_policy_version (
    id, organization_id, document_authority_policy_id, version_number,
    configuration_json, status, approved_by, approved_at, created_at
) VALUES (
    '60000000-0000-0000-0000-000000000121', NULL,
    '60000000-0000-0000-0000-000000000120', 1,
    '{"rules":[],"onUnknown":"MANUAL_REVIEW"}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE document_authority_policy
SET active_version_id = '60000000-0000-0000-0000-000000000121'
WHERE id = '60000000-0000-0000-0000-000000000120';

INSERT INTO policy_definition (
    id, organization_id, policy_code, policy_type, name, scope, created_at, updated_at
) VALUES
    ('60000000-0000-0000-0000-000000000130', NULL, 'GLOBAL_RISK_SIGNAL',
     'RISK_SIGNAL', 'Global risk signal policy', 'GLOBAL', now(), now()),
    ('60000000-0000-0000-0000-000000000131', NULL, 'GLOBAL_CONFLICT',
     'CONFLICT', 'Global conflict candidate policy', 'GLOBAL', now(), now()),
    ('60000000-0000-0000-0000-000000000132', NULL, 'GLOBAL_AMBIGUITY',
     'AMBIGUITY', 'Global ambiguity policy', 'GLOBAL', now(), now()),
    ('60000000-0000-0000-0000-000000000133', NULL, 'GLOBAL_IMPACT',
     'IMPACT', 'Global impact policy', 'GLOBAL', now(), now());
INSERT INTO policy_version (
    id, organization_id, policy_definition_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES
    ('60000000-0000-0000-0000-000000000140', NULL,
     '60000000-0000-0000-0000-000000000130', 1,
     '{"weights":{"groundingGap":0.30,"confidenceGap":0.20,"testabilityGap":0.20,"evidenceGap":0.30},
       "signalSources":{
         "groundingGap":{"source":"groundingCoverage","transform":"ONE_MINUS"},
         "confidenceGap":{"source":"requirementConfidence","transform":"ONE_MINUS"},
         "testabilityGap":{"source":"testabilityPresent","transform":"ONE_MINUS"},
         "evidenceGap":{"source":"validEvidencePresent","transform":"ONE_MINUS"}
       },
       "detailedAnalysisThreshold":0.35,"exposureMethod":"WEIGHTED_SUM",
       "probabilityWeights":{"groundingGap":0.50,"confidenceGap":0.50},
       "impactWeights":{"testabilityGap":0.40,"evidenceGap":0.60},
       "exposureWeights":{"probability":0.45,"impact":0.55},
       "fallbackConcept":{"conceptCode":"RISK_CANDIDATE",
         "reasonCodes":["POLICY_THRESHOLD_EXCEEDED"]},
       "reviewStatusConceptCode":"REVIEW_REQUIRED",
       "sourceRoleConceptCode":"SOURCE_SUPPORT"}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('60000000-0000-0000-0000-000000000141', NULL,
     '60000000-0000-0000-0000-000000000131', 1,
     '{"candidateLimit":100,"retrievalLimit":250,"minimumRetrievalScore":0.55,
       "stages":["ENTITY_SCOPE","ONTOLOGY_CONCEPT","ATTRIBUTE","VERSION","NUMERIC","RERANK"],
       "strategies":["STRUCTURED_VALUE"],
       "structuredRules":[
         {"path":"/value","strategyCode":"STRUCTURED_VALUE",
          "conflictConceptCode":"CONFLICT_CANDIDATE",
          "description":"Policy-selected structured values differ.","tolerance":0.0},
         {"path":"/duration/value","strategyCode":"STRUCTURED_VALUE",
          "conflictConceptCode":"CONFLICT_CANDIDATE",
          "description":"Policy-selected duration values differ.","tolerance":0.0}
       ],
       "reviewStatusConceptCode":"REVIEW_REQUIRED",
       "leftSideConceptCode":"SOURCE_LEFT","rightSideConceptCode":"SOURCE_RIGHT"}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('60000000-0000-0000-0000-000000000142', NULL,
     '60000000-0000-0000-0000-000000000132', 1,
     '{"features":{"missingMeasurement":0.30,"missingOperator":0.20,
       "missingTestCondition":0.25,"missingAcceptanceThreshold":0.25},
       "featureSources":{
         "missingMeasurement":{"jsonPointer":"/measurement"},
         "missingOperator":{"jsonPointer":"/operator"},
         "missingTestCondition":{"jsonPointer":"/testCondition"},
         "missingAcceptanceThreshold":{"jsonPointer":"/acceptanceThreshold"}
       },
       "findingThreshold":0.45,
       "fallbackConcept":{"conceptCode":"AMBIGUITY_CANDIDATE"}}'::jsonb,
     'ACTIVE', 'platform', now(), now()),
    ('60000000-0000-0000-0000-000000000143', NULL,
     '60000000-0000-0000-0000-000000000133', 1,
     '{"maximumDepth":4,"minimumConfidence":0.50,
       "impactConceptCode":"REQUIRES_REANALYSIS",
       "stalenessStatusCode":"STALE","stalenessTriggerCode":"SOURCE_CHANGED",
       "changeMatching":{"minimumSimilarity":0.62,
         "addedConceptCode":"ADDED","removedConceptCode":"REMOVED",
         "modifiedConceptCode":"MODIFIED","unchangedConceptCode":"UNCHANGED"}}'::jsonb,
     'ACTIVE', 'platform', now(), now());

-- Propagation is an impact-policy behavior; keeping it in the same version makes
-- re-analysis and propagation reproducible as one immutable snapshot.
UPDATE policy_version
SET configuration_json = configuration_json || '{
  "propagationConceptCode":"PROPAGATED_RISK_CANDIDATE"
}'::jsonb
WHERE id = '60000000-0000-0000-0000-000000000143';

INSERT INTO mitigation_catalog (
    id, organization_id, name, scope, created_at, updated_at
) VALUES (
    '60000000-0000-0000-0000-000000000160', NULL,
    'Global empty mitigation baseline', 'GLOBAL', now(), now()
);
INSERT INTO mitigation_catalog_version (
    id, organization_id, mitigation_catalog_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES (
    '60000000-0000-0000-0000-000000000161', NULL,
    '60000000-0000-0000-0000-000000000160', 1,
    '{"patterns":[],"activateGeneratedCandidates":false}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE mitigation_catalog
SET active_version_id = '60000000-0000-0000-0000-000000000161'
WHERE id = '60000000-0000-0000-0000-000000000160';

INSERT INTO clarification_strategy (
    id, organization_id, strategy_code, scope, created_at, updated_at
) VALUES (
    '60000000-0000-0000-0000-000000000170', NULL,
    'GLOBAL_REVIEWED_CLARIFICATION', 'GLOBAL', now(), now()
);
INSERT INTO clarification_strategy_version (
    id, organization_id, clarification_strategy_id, version_number,
    configuration_json, prompt_package_version_id, output_schema_version_id,
    status, approved_by, approved_at, created_at
) VALUES (
    '60000000-0000-0000-0000-000000000171', NULL,
    '60000000-0000-0000-0000-000000000170', 1,
    '{"delivery":"CANDIDATE_ONLY","requiresHumanApproval":true}'::jsonb,
    '40000000-0000-0000-0000-000000000033',
    '40000000-0000-0000-0000-000000000021',
    'ACTIVE', 'platform', now(), now()
);
UPDATE clarification_strategy
SET active_version_id = '60000000-0000-0000-0000-000000000171'
WHERE id = '60000000-0000-0000-0000-000000000170';

INSERT INTO ui_configuration (
    id, organization_id, configuration_code, configuration_json, active,
    created_at, updated_at
) VALUES (
    '60000000-0000-0000-0000-000000000150', NULL, 'RISK_GRID',
    '{"columns":[
       {"key":"riskCode","label":"Risk kodu","type":"TEXT","visible":true},
       {"key":"title","label":"Başlık","type":"TEXT","visible":true},
       {"key":"riskConcept","label":"Risk kavramı","type":"CONCEPT","visible":true},
       {"key":"severity","label":"Seviye","type":"CONCEPT","visible":true},
       {"key":"probabilityScore","label":"Olasılık","type":"PERCENT","visible":true},
       {"key":"impactScore","label":"Etki","type":"PERCENT","visible":true},
       {"key":"exposureScore","label":"Maruziyet","type":"PERCENT","visible":true},
       {"key":"confidence","label":"Güven","type":"PERCENT","visible":true},
       {"key":"ownerUserId","label":"Sorumlu","type":"USER","visible":true},
       {"key":"dueDate","label":"Son tarih","type":"DATE","visible":true},
       {"key":"reviewStatus","label":"İnceleme","type":"STATUS","visible":true},
       {"key":"stalenessStatus","label":"Güncellik","type":"STATUS","visible":true}
     ]}'::jsonb, TRUE, now(), now()
);

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'risk_taxonomy', 'risk_taxonomy_version', 'severity_policy',
        'severity_policy_version', 'document_authority_policy',
        'document_authority_policy_version', 'risk_analysis_profile',
        'risk_analysis_job', 'risk_analysis_event', 'risk_analysis_task',
        'risk_record', 'risk_source', 'risk_factor', 'risk_revision',
        'ambiguity_finding', 'ambiguity_source', 'ambiguity_interpretation',
        'conflict_record', 'conflict_source', 'conflict_factor', 'conflict_revision',
        'requirement_dependency', 'document_change_set', 'document_change_item',
        'impact_analysis_job', 'impact_analysis_result', 'impact_analysis_event',
        'analysis_staleness_record', 'risk_propagation_candidate',
        'mitigation_catalog', 'mitigation_catalog_version', 'mitigation_pattern',
        'mitigation_candidate', 'clarification_strategy',
        'clarification_strategy_version', 'clarification_candidate'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_or_global_%I ON %I USING (organization_id IS NULL OR organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
            table_name,
            table_name
        );
    END LOOP;
END
$$;
