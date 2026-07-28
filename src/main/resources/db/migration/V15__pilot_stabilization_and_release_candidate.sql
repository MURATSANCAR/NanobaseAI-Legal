-- Sprint 9: pilot stabilization, explainable triage, reproducible experiments and
-- fail-closed release-candidate governance. Business taxonomies are data, never
-- Java/PostgreSQL enums. Platform invariants remain database/application rules.

CREATE TABLE sprint9_concept_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organization(id),
    catalog_code VARCHAR(120) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (organization_id, catalog_code)
);

CREATE TABLE sprint9_concept (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organization(id),
    catalog_id UUID NOT NULL REFERENCES sprint9_concept_catalog(id) ON DELETE CASCADE,
    concept_code VARCHAR(160) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (catalog_id, concept_code)
);

CREATE TABLE sprint9_policy_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    policy_type VARCHAR(120) NOT NULL,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sprint9_policy_version CHECK (version_number > 0),
    CONSTRAINT ck_sprint9_policy_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED')
    ),
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code, version_number)
);

CREATE TABLE configuration_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    snapshot_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    model_deployments_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_profiles_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    prompt_versions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    policy_versions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ontology_versions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    terminology_snapshots_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    output_schema_versions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    workflow_versions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    feature_flags_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    deployment_configuration_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, deployment_configuration_hash)
);

CREATE TABLE pilot_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id) ON DELETE CASCADE,
    pilot_phase_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    configuration_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pilot_session_window CHECK (
        completed_at IS NULL OR completed_at >= started_at
    )
);

CREATE TABLE pilot_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    pilot_session_id UUID NOT NULL REFERENCES pilot_session(id) ON DELETE CASCADE,
    event_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    entity_type VARCHAR(160) NOT NULL,
    entity_id UUID,
    correlation_id UUID NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pilot_metric_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organization(id),
    metric_code VARCHAR(160) NOT NULL,
    name VARCHAR(240) NOT NULL,
    unit VARCHAR(80) NOT NULL,
    allowed_dimensions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (organization_id, metric_code)
);

CREATE TABLE pilot_metric_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    pilot_session_id UUID NOT NULL REFERENCES pilot_session(id) ON DELETE CASCADE,
    metric_definition_id UUID NOT NULL REFERENCES pilot_metric_definition(id),
    metric_value NUMERIC(24,6) NOT NULL,
    dimension_values_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE feedback_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id) ON DELETE CASCADE,
    pilot_session_id UUID REFERENCES pilot_session(id),
    feedback_code VARCHAR(80) NOT NULL,
    feedback_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    classification_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    severity_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    entity_type VARCHAR(160) NOT NULL,
    entity_id UUID,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    expected_behavior TEXT,
    actual_behavior TEXT,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    assigned_team_concept_id UUID REFERENCES sprint9_concept(id),
    reported_by VARCHAR(255) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, feedback_code),
    CONSTRAINT ck_feedback_resolution CHECK (
        resolved_at IS NULL OR resolved_at >= reported_at
    )
);

CREATE TABLE feedback_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    feedback_case_id UUID NOT NULL REFERENCES feedback_case(id) ON DELETE CASCADE,
    evidence_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    reference_entity_type VARCHAR(160) NOT NULL,
    reference_entity_id UUID,
    sanitized_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE feedback_comment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    feedback_case_id UUID NOT NULL REFERENCES feedback_case(id) ON DELETE CASCADE,
    author_user_id VARCHAR(255) NOT NULL,
    comment_text TEXT NOT NULL,
    visibility_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE error_triage_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    feedback_case_id UUID NOT NULL REFERENCES feedback_case(id) ON DELETE CASCADE,
    root_cause_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    secondary_cause_concepts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    reproducibility_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    affected_scope_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    impact_score NUMERIC(8,4) NOT NULL,
    frequency_score NUMERIC(8,4) NOT NULL,
    priority_score NUMERIC(8,4) NOT NULL,
    release_blocker BOOLEAN NOT NULL DEFAULT FALSE,
    analysis_summary TEXT NOT NULL,
    analyzer_recommendation_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    triaged_by VARCHAR(255) NOT NULL,
    triaged_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (feedback_case_id)
);

CREATE TABLE sanitized_input_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    snapshot_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    sanitization_manifest_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, content_hash)
);

CREATE TABLE reproduction_package (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    feedback_case_id UUID NOT NULL REFERENCES feedback_case(id) ON DELETE CASCADE,
    sanitized_input_snapshot_id UUID NOT NULL REFERENCES sanitized_input_snapshot(id),
    configuration_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    expected_output_json JSONB NOT NULL,
    actual_output_json JSONB NOT NULL,
    execution_instructions_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, content_hash)
);

CREATE TABLE improvement_candidate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    candidate_code VARCHAR(80) NOT NULL,
    candidate_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    root_cause_record_id UUID NOT NULL REFERENCES error_triage_record(id),
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    target_component_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    baseline_configuration_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    candidate_configuration_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    expected_improvement_json JSONB NOT NULL,
    risk_assessment_json JSONB NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, candidate_code),
    CONSTRAINT ck_candidate_distinct_snapshots CHECK (
        baseline_configuration_snapshot_id <> candidate_configuration_snapshot_id
    )
);

ALTER TABLE shadow_execution
    ADD COLUMN improvement_candidate_id UUID REFERENCES improvement_candidate(id);
ALTER TABLE canary_assignment
    ADD COLUMN improvement_candidate_id UUID REFERENCES improvement_candidate(id),
    ADD COLUMN comparison_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN completed_at TIMESTAMPTZ;

CREATE TABLE experiment_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    improvement_candidate_id UUID REFERENCES improvement_candidate(id),
    experiment_code VARCHAR(80) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    experiment_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    baseline_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    candidate_snapshot_ids_json JSONB NOT NULL,
    dataset_ids_json JSONB NOT NULL,
    metric_configuration_json JSONB NOT NULL,
    quality_gate_version_id UUID NOT NULL REFERENCES quality_gate_version(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, experiment_code)
);

CREATE TABLE experiment_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    experiment_definition_id UUID NOT NULL REFERENCES experiment_definition(id) ON DELETE CASCADE,
    run_number INTEGER NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    runtime_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (experiment_definition_id, run_number),
    CONSTRAINT ck_experiment_run_number CHECK (run_number > 0),
    CONSTRAINT ck_experiment_run_window CHECK (
        completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at
    )
);

CREATE TABLE experiment_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    experiment_run_id UUID NOT NULL REFERENCES experiment_run(id) ON DELETE CASCADE,
    candidate_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    metrics_json JSONB NOT NULL,
    regression_summary_json JSONB NOT NULL,
    resource_usage_json JSONB NOT NULL,
    failure_summary_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (experiment_run_id, candidate_snapshot_id)
);

CREATE TABLE regression_suite (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    suite_code VARCHAR(80) NOT NULL,
    name VARCHAR(240) NOT NULL,
    scope VARCHAR(120) NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, suite_code)
);

CREATE TABLE regression_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_feedback_case_id UUID NOT NULL REFERENCES feedback_case(id),
    suite_id UUID NOT NULL REFERENCES regression_suite(id) ON DELETE CASCADE,
    case_code VARCHAR(100) NOT NULL,
    input_snapshot_id UUID NOT NULL REFERENCES sanitized_input_snapshot(id),
    expected_behavior_json JSONB NOT NULL,
    severity_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    case_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, case_code)
);

CREATE TABLE quality_debt_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    feedback_case_id UUID NOT NULL REFERENCES feedback_case(id),
    debt_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    severity_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    business_impact TEXT NOT NULL,
    technical_impact TEXT NOT NULL,
    workaround TEXT,
    target_release VARCHAR(80),
    owner_user_id VARCHAR(255) NOT NULL,
    acceptance_status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    accepted_by VARCHAR(255),
    accepted_at TIMESTAMPTZ,
    compensating_controls_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE review_disagreement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    entity_type VARCHAR(160) NOT NULL,
    entity_id UUID NOT NULL,
    reviewer_a_id VARCHAR(255) NOT NULL,
    reviewer_b_id VARCHAR(255) NOT NULL,
    decision_a_json JSONB NOT NULL,
    decision_b_json JSONB NOT NULL,
    disagreement_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    adjudicator_user_id VARCHAR(255),
    final_decision_json JSONB,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_disagreement_reviewers CHECK (reviewer_a_id <> reviewer_b_id),
    CONSTRAINT ck_disagreement_adjudication CHECK (
        (resolved_at IS NULL AND final_decision_json IS NULL)
        OR (resolved_at IS NOT NULL AND final_decision_json IS NOT NULL
            AND adjudicator_user_id IS NOT NULL
            AND adjudicator_user_id <> reviewer_a_id
            AND adjudicator_user_id <> reviewer_b_id)
    )
);

CREATE TABLE training_need (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    user_id VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL REFERENCES tender_project(id) ON DELETE CASCADE,
    training_topic_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    detected_from_feedback_id UUID REFERENCES feedback_case(id),
    priority_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    assigned_training_content_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE survey_definition_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organization(id),
    survey_code VARCHAR(120) NOT NULL,
    version_number INTEGER NOT NULL,
    questions_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE NULLS NOT DISTINCT (organization_id, survey_code, version_number)
);

CREATE TABLE user_satisfaction_survey (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    pilot_session_id UUID NOT NULL REFERENCES pilot_session(id) ON DELETE CASCADE,
    survey_definition_version_id UUID NOT NULL REFERENCES survey_definition_version(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    answers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE process_baseline (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    process_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    measurement_period DATERANGE NOT NULL,
    average_duration_minutes NUMERIC(12,2) NOT NULL,
    average_user_count NUMERIC(12,2) NOT NULL,
    average_rework_minutes NUMERIC(12,2) NOT NULL,
    average_error_count NUMERIC(12,2) NOT NULL,
    source_method_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_process_baseline_nonnegative CHECK (
        average_duration_minutes >= 0 AND average_user_count >= 0
        AND average_rework_minutes >= 0 AND average_error_count >= 0
    )
);

CREATE TABLE business_value_metric_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    pilot_session_id UUID NOT NULL REFERENCES pilot_session(id) ON DELETE CASCADE,
    metric_definition_id UUID NOT NULL REFERENCES pilot_metric_definition(id),
    baseline_value NUMERIC(24,6),
    observed_value NUMERIC(24,6) NOT NULL,
    dimension_values_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    measured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE release_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_code VARCHAR(80) NOT NULL,
    semantic_version VARCHAR(80) NOT NULL,
    release_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    source_commit VARCHAR(80) NOT NULL,
    build_number VARCHAR(120) NOT NULL,
    scope_locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    UNIQUE (organization_id, release_code),
    UNIQUE (organization_id, semantic_version, build_number)
);

CREATE TABLE release_scope (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    feature_definition_id UUID NOT NULL REFERENCES feature_definition(id),
    scope_status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (release_id, feature_definition_id)
);

CREATE TABLE release_artifact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    artifact_type_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    artifact_reference TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    signature_reference TEXT NOT NULL,
    sbom_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_release_artifact_sha CHECK (sha256 ~ '^[a-f0-9]{64}$')
);

CREATE TABLE release_configuration_manifest (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    manifest_version INTEGER NOT NULL DEFAULT 1,
    backend_image_digest VARCHAR(120) NOT NULL,
    frontend_image_digest VARCHAR(120) NOT NULL,
    worker_image_digests_json JSONB NOT NULL,
    model_artifacts_json JSONB NOT NULL,
    prompt_versions_json JSONB NOT NULL,
    policy_versions_json JSONB NOT NULL,
    ontology_versions_json JSONB NOT NULL,
    workflow_versions_json JSONB NOT NULL,
    database_migration_versions_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (release_id),
    CONSTRAINT ck_backend_digest CHECK (backend_image_digest ~ '^sha256:[a-f0-9]{64}$'),
    CONSTRAINT ck_frontend_digest CHECK (frontend_image_digest ~ '^sha256:[a-f0-9]{64}$')
);

CREATE TABLE release_gate_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gate_code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    scope VARCHAR(120) NOT NULL,
    required_by_default BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE release_gate_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    gate_definition_id UUID NOT NULL REFERENCES release_gate_definition(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    evidence_references_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary TEXT NOT NULL,
    waiver_reason TEXT,
    compensating_controls_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    waived_by VARCHAR(255),
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (release_id, gate_definition_id)
);

CREATE TABLE release_approval_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    approval_policy_version_id UUID NOT NULL REFERENCES sprint9_policy_version(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    approval_steps_json JSONB NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE compatibility_matrix (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    component_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    minimum_supported_version VARCHAR(120),
    maximum_supported_version VARCHAR(120),
    compatibility_status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (release_id, component_concept_id)
);

CREATE TABLE release_dry_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    environment VARCHAR(120) NOT NULL,
    steps_json JSONB NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    evidence_references_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    requested_by VARCHAR(255) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rollout_checkpoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    stage_code VARCHAR(120) NOT NULL,
    checkpoint_code VARCHAR(160) NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (release_id, stage_code, checkpoint_code)
);

CREATE TABLE go_live_decision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    decision_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    conditions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    open_risks_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    rollback_plan_reference TEXT NOT NULL,
    decided_by VARCHAR(255) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stabilization_window (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    release_id UUID NOT NULL REFERENCES release_record(id) ON DELETE CASCADE,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    monitoring_policy_version_id UUID NOT NULL REFERENCES sprint9_policy_version(id),
    support_policy_version_id UUID NOT NULL REFERENCES sprint9_policy_version(id),
    status_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_stabilization_window CHECK (end_at > start_at)
);

CREATE TABLE support_ticket_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    feedback_case_id UUID NOT NULL REFERENCES feedback_case(id) ON DELETE CASCADE,
    provider_concept_id UUID NOT NULL REFERENCES sprint9_concept(id),
    external_ticket_id VARCHAR(300) NOT NULL,
    external_status VARCHAR(160),
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, provider_concept_id, external_ticket_id)
);

CREATE TABLE capacity_plan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    deployment_profile_id VARCHAR(160) NOT NULL,
    measurement_period TSTZRANGE NOT NULL,
    workload_summary_json JSONB NOT NULL,
    resource_requirements_json JSONB NOT NULL,
    scaling_policy_json JSONB NOT NULL,
    headroom_percentage NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_capacity_headroom CHECK (
        headroom_percentage >= 0 AND headroom_percentage <= 100
    )
);

CREATE TABLE configuration_activation_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    configuration_snapshot_id UUID NOT NULL REFERENCES configuration_snapshot(id),
    previous_configuration_snapshot_id UUID REFERENCES configuration_snapshot(id),
    improvement_candidate_id UUID REFERENCES improvement_candidate(id),
    action VARCHAR(40) NOT NULL,
    reason TEXT NOT NULL,
    approved_by_json JSONB NOT NULL,
    activated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_configuration_action CHECK (action IN ('ACTIVATE', 'ROLLBACK'))
);

CREATE TABLE diagnostic_bundle_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organization(id),
    requested_by VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    manifest_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_diagnostic_status CHECK (
        status IN ('GENERATED', 'EXPIRED', 'FAILED')
    )
);

-- Telemetry is allowlisted. Text-bearing business content and credentials cannot
-- be smuggled into the otherwise flexible metadata object.
CREATE OR REPLACE FUNCTION validate_pilot_metadata() RETURNS trigger AS $$
DECLARE
    metadata_key TEXT;
    allowed_keys CONSTANT TEXT[] := ARRAY[
        'duration_ms', 'page_count', 'clause_count', 'requirement_count',
        'model_profile', 'schema_failure', 'grounding_result', 'retry_count',
        'manual_review', 'expert_decision_type', 'correction_type',
        'queue_duration_ms', 'parser_warning_code', 'ocr_quality_level'
    ];
BEGIN
    IF jsonb_typeof(NEW.metadata_json) <> 'object' THEN
        RAISE EXCEPTION 'pilot metadata must be a JSON object';
    END IF;
    IF octet_length(NEW.metadata_json::text) > 32768 THEN
        RAISE EXCEPTION 'pilot metadata exceeds 32 KiB';
    END IF;
    FOR metadata_key IN SELECT jsonb_object_keys(NEW.metadata_json)
    LOOP
        IF NOT (metadata_key = ANY (allowed_keys)) THEN
            RAISE EXCEPTION 'pilot metadata key is not allowlisted: %', metadata_key;
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pilot_event_metadata_guard
    BEFORE INSERT OR UPDATE ON pilot_event
    FOR EACH ROW EXECUTE FUNCTION validate_pilot_metadata();

CREATE OR REPLACE FUNCTION validate_pilot_dimensions() RETURNS trigger AS $$
DECLARE
    dimension_key TEXT;
    forbidden_keys CONSTANT TEXT[] := ARRAY[
        'document_text', 'evidence_text', 'prompt', 'model_input', 'model_output',
        'personal_data', 'trade_secret', 'signed_url', 'token', 'secret',
        'authorization', 'password', 'api_key'
    ];
BEGIN
    IF jsonb_typeof(NEW.dimension_values_json) <> 'object' THEN
        RAISE EXCEPTION 'pilot metric dimensions must be a JSON object';
    END IF;
    IF octet_length(NEW.dimension_values_json::text) > 8192 THEN
        RAISE EXCEPTION 'pilot metric dimensions exceed 8 KiB';
    END IF;
    FOR dimension_key IN SELECT lower(jsonb_object_keys(NEW.dimension_values_json))
    LOOP
        IF dimension_key = ANY (forbidden_keys) THEN
            RAISE EXCEPTION 'sensitive pilot metric dimension is forbidden: %', dimension_key;
        END IF;
    END LOOP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pilot_metric_dimension_guard
    BEFORE INSERT OR UPDATE ON pilot_metric_snapshot
    FOR EACH ROW EXECUTE FUNCTION validate_pilot_dimensions();

CREATE OR REPLACE FUNCTION reject_immutable_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is immutable', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER configuration_snapshot_immutable
    BEFORE UPDATE OR DELETE ON configuration_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER sanitized_input_snapshot_immutable
    BEFORE UPDATE OR DELETE ON sanitized_input_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER reproduction_package_immutable
    BEFORE UPDATE OR DELETE ON reproduction_package
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER release_manifest_immutable
    BEFORE UPDATE OR DELETE ON release_configuration_manifest
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();
CREATE TRIGGER experiment_result_immutable
    BEFORE UPDATE OR DELETE ON experiment_result
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_change();

CREATE INDEX ix_feedback_queue
    ON feedback_case (organization_id, status_concept_id, severity_concept_id, reported_at DESC);
CREATE INDEX ix_triage_blockers
    ON error_triage_record (organization_id, release_blocker, priority_score DESC);
CREATE INDEX ix_experiment_candidate
    ON experiment_definition (organization_id, improvement_candidate_id, updated_at DESC);
CREATE INDEX ix_release_gate_status
    ON release_gate_result (organization_id, release_id, status_concept_id);
CREATE INDEX ix_pilot_metric_time
    ON pilot_metric_snapshot (organization_id, pilot_session_id, measured_at DESC);
CREATE INDEX ix_stabilization_release
    ON stabilization_window (organization_id, release_id, start_at DESC);

INSERT INTO sprint9_concept_catalog (catalog_code, name, description) VALUES
    ('ROOT_CAUSE', 'Error root causes', 'Dynamic root-cause taxonomy'),
    ('FEEDBACK_TYPE', 'Feedback types', 'Pilot feedback taxonomy'),
    ('FEEDBACK_CLASSIFICATION', 'Feedback classification', 'Bug/expectation classification'),
    ('SEVERITY', 'Severity', 'Impact severity'),
    ('FEEDBACK_STATUS', 'Feedback status', 'Feedback lifecycle'),
    ('REPRODUCIBILITY', 'Reproducibility', 'Reproduction confidence'),
    ('ASSIGNED_TEAM', 'Assigned team', 'Dynamic ownership'),
    ('SNAPSHOT_TYPE', 'Snapshot type', 'Configuration snapshot purpose'),
    ('CANDIDATE_TYPE', 'Candidate type', 'Improvement mechanism'),
    ('TARGET_COMPONENT', 'Target component', 'Improvement target'),
    ('CANDIDATE_STATUS', 'Candidate status', 'Candidate lifecycle'),
    ('EXPERIMENT_TYPE', 'Experiment type', 'Provider-dispatched experiment behavior'),
    ('EXPERIMENT_STATUS', 'Experiment status', 'Experiment lifecycle'),
    ('REGRESSION_CASE_TYPE', 'Regression case type', 'Regression execution layer'),
    ('REGRESSION_STATUS', 'Regression suite status', 'Regression suite lifecycle'),
    ('QUALITY_DEBT_STATUS', 'Quality debt status', 'Explicit debt acceptance'),
    ('QUALITY_DEBT_TYPE', 'Quality debt type', 'Deferred quality work category'),
    ('DISAGREEMENT', 'Review disagreement', 'Expert disagreement category'),
    ('REVIEW_STATUS', 'Review status', 'Review/adjudication lifecycle'),
    ('PILOT_PHASE', 'Pilot phase', 'Pilot execution phase'),
    ('PILOT_STATUS', 'Pilot status', 'Pilot session lifecycle'),
    ('PILOT_EVENT_TYPE', 'Pilot event type', 'Safe telemetry event type'),
    ('RELEASE_TYPE', 'Release type', 'Release behavior category'),
    ('RELEASE_STATUS', 'Release status', 'Release lifecycle'),
    ('GATE_STATUS', 'Release gate status', 'Evidence result'),
    ('APPROVAL_STATUS', 'Approval status', 'Release approval state'),
    ('GO_LIVE_DECISION', 'Go-live decision', 'Human production decision'),
    ('SCOPE_STATUS', 'Release scope status', 'RC scope disposition'),
    ('STABILIZATION_STATUS', 'Stabilization status', 'Post-release window state'),
    ('COMPATIBILITY_STATUS', 'Compatibility status', 'Supported platform status'),
    ('EVIDENCE_TYPE', 'Feedback evidence type', 'Sanitized evidence reference'),
    ('VISIBILITY', 'Comment visibility', 'Comment access class'),
    ('ARTIFACT_TYPE', 'Release artifact type', 'Immutable release artifact'),
    ('COMPONENT', 'Compatibility component', 'Deployment dependency'),
    ('TRAINING_TOPIC', 'Training topic', 'Product training need'),
    ('PRIORITY', 'Priority', 'Operational priority'),
    ('PROCESS', 'Business process', 'Measured process'),
    ('MEASUREMENT_SOURCE', 'Measurement source', 'Baseline source method'),
    ('SUPPORT_PROVIDER', 'Support provider', 'Provider-neutral ticket adapter');

WITH catalog AS (
    SELECT id FROM sprint9_concept_catalog WHERE catalog_code = 'ROOT_CAUSE'
), values_to_insert(code, ordinal) AS (
    SELECT * FROM unnest(ARRAY[
        'SOURCE_DATA','DOCUMENT_QUALITY','SECURITY_SCAN','PARSER','OCR',
        'CLAUSE_SEGMENTATION','CONTEXT_SELECTION','ONTOLOGY','TERMINOLOGY','POLICY',
        'PROMPT','MODEL','MODEL_ROUTING','OUTPUT_SCHEMA','GROUNDING',
        'ENTITY_RESOLUTION','RETRIEVAL','RERANKING','COMPARISON','CONFIDENCE',
        'RISK_ANALYSIS','CONFLICT_ANALYSIS','WORKFLOW','AUTHORIZATION','REPORTING',
        'FRONTEND','PERFORMANCE','INFRASTRUCTURE','USER_EXPECTATION','LABEL_QUALITY'
    ]) WITH ORDINALITY
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog.id, code, replace(initcap(replace(code, '_', ' ')), ' ', ' '), ordinal
FROM catalog CROSS JOIN values_to_insert;

WITH catalog AS (
    SELECT id FROM sprint9_concept_catalog WHERE catalog_code = 'FEEDBACK_TYPE'
), values_to_insert(code, ordinal) AS (
    SELECT * FROM unnest(ARRAY[
        'FALSE_POSITIVE','FALSE_NEGATIVE','WRONG_EXTRACTION','WRONG_GROUNDING',
        'WRONG_ENTITY_MATCH','WRONG_EVIDENCE','WRONG_COMPLIANCE','WRONG_RISK',
        'WRONG_CONFLICT','WRONG_SEVERITY','MISSING_FEATURE','USABILITY',
        'PERFORMANCE','SECURITY','REPORTING','WORKFLOW','DOCUMENT_RENDERING'
    ]) WITH ORDINALITY
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog.id, code, initcap(replace(code, '_', ' ')), ordinal
FROM catalog CROSS JOIN values_to_insert;

WITH catalog AS (
    SELECT id FROM sprint9_concept_catalog WHERE catalog_code = 'FEEDBACK_CLASSIFICATION'
), values_to_insert(code, ordinal) AS (
    SELECT * FROM unnest(ARRAY[
        'BUG','MODEL_ERROR','DATA_ERROR','CONFIGURATION_ERROR','LABEL_ERROR',
        'EXPECTED_BEHAVIOR','FEATURE_REQUEST','USABILITY_REQUEST','TRAINING_NEED',
        'CUSTOMER_POLICY_GAP'
    ]) WITH ORDINALITY
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog.id, code, initcap(replace(code, '_', ' ')), ordinal
FROM catalog CROSS JOIN values_to_insert;

WITH catalog AS (
    SELECT id FROM sprint9_concept_catalog WHERE catalog_code = 'CANDIDATE_TYPE'
), values_to_insert(code, ordinal) AS (
    SELECT * FROM unnest(ARRAY[
        'PARSER_CONFIGURATION','OCR_CONFIGURATION','CONTEXT_POLICY','ONTOLOGY_UPDATE',
        'TERMINOLOGY_UPDATE','PROMPT_UPDATE','OUTPUT_SCHEMA_UPDATE','MODEL_CHANGE',
        'MODEL_ROUTING_UPDATE','GROUNDING_POLICY','RETRIEVAL_POLICY','RERANKING_POLICY',
        'CONFIDENCE_POLICY','WORKFLOW_UPDATE','UI_UPDATE','PERFORMANCE_UPDATE',
        'SECURITY_UPDATE'
    ]) WITH ORDINALITY
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog.id, code, initcap(replace(code, '_', ' ')), ordinal
FROM catalog CROSS JOIN values_to_insert;

WITH catalog AS (
    SELECT id FROM sprint9_concept_catalog WHERE catalog_code = 'EXPERIMENT_TYPE'
), values_to_insert(code, ordinal) AS (
    SELECT * FROM unnest(ARRAY[
        'OFFLINE_EVALUATION','PARSER_BENCHMARK','MODEL_COMPARISON','PROMPT_COMPARISON',
        'POLICY_COMPARISON','ROUTING_COMPARISON','SHADOW_EVALUATION','CANARY_EVALUATION',
        'PERFORMANCE_BENCHMARK','SECURITY_REGRESSION'
    ]) WITH ORDINALITY
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog.id, code, initcap(replace(code, '_', ' ')), ordinal
FROM catalog CROSS JOIN values_to_insert;

WITH catalog AS (
    SELECT id FROM sprint9_concept_catalog WHERE catalog_code = 'RELEASE_TYPE'
), values_to_insert(code, ordinal) AS (
    SELECT * FROM unnest(ARRAY[
        'SNAPSHOT','ALPHA','BETA','RELEASE_CANDIDATE','GENERAL_AVAILABILITY',
        'HOTFIX','SECURITY_PATCH'
    ]) WITH ORDINALITY
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog.id, code, initcap(replace(code, '_', ' ')), ordinal
FROM catalog CROSS JOIN values_to_insert;

-- Common lifecycle catalogs remain extensible; these are initial defaults only.
WITH seeds(catalog_code, codes) AS (VALUES
    ('SEVERITY', ARRAY['LOW','MEDIUM','HIGH','CRITICAL']),
    ('FEEDBACK_STATUS', ARRAY['OPEN','TRIAGED','IN_PROGRESS','RESOLVED','CLOSED']),
    ('REPRODUCIBILITY', ARRAY['ALWAYS','INTERMITTENT','NOT_REPRODUCED','UNKNOWN']),
    ('ASSIGNED_TEAM', ARRAY['DATA','PARSER','AI','PLATFORM','SECURITY','PRODUCT','FRONTEND','OPERATIONS']),
    ('SNAPSHOT_TYPE', ARRAY['BASELINE','CANDIDATE','ACTIVE','ROLLBACK']),
    ('TARGET_COMPONENT', ARRAY['PARSER','OCR','CONTEXT','ONTOLOGY','TERMINOLOGY','PROMPT','MODEL','ROUTING','GROUNDING','RETRIEVAL','RERANKING','CONFIDENCE','WORKFLOW','UI','PERFORMANCE','SECURITY']),
    ('CANDIDATE_STATUS', ARRAY['DRAFT','OFFLINE_EVALUATION','SHADOW','CANARY','APPROVED','ACTIVE','REJECTED','ROLLED_BACK']),
    ('EXPERIMENT_STATUS', ARRAY['DRAFT','QUEUED','RUNNING','PASS','FAIL','CANCELLED']),
    ('REGRESSION_CASE_TYPE', ARRAY['UNIT','INTEGRATION','CONTRACT','E2E','EVALUATION','SECURITY','PERFORMANCE']),
    ('REGRESSION_STATUS', ARRAY['ACTIVE','RETIRED']),
    ('QUALITY_DEBT_STATUS', ARRAY['PROPOSED','ACCEPTED','REJECTED','RESOLVED']),
    ('QUALITY_DEBT_TYPE', ARRAY['KNOWN_LIMITATION','DEFECT_DEFERRAL','PERFORMANCE_DEBT','USABILITY_DEBT','DOCUMENTATION_DEBT']),
    ('DISAGREEMENT', ARRAY['REQUIREMENT_PRESENCE','CATEGORY','MODALITY','EVIDENCE_SUFFICIENCY','COMPLIANCE_DECISION','RISK_SEVERITY','CONFLICT_PRESENCE']),
    ('REVIEW_STATUS', ARRAY['OPEN','IN_ADJUDICATION','RESOLVED']),
    ('PILOT_PHASE', ARRAY['ONBOARDING','BASELINE','GUIDED','INDEPENDENT','CLOSURE']),
    ('PILOT_STATUS', ARRAY['PLANNED','ACTIVE','COMPLETED','CANCELLED']),
    ('PILOT_EVENT_TYPE', ARRAY['ACTION_COMPLETED','ANALYSIS_COMPLETED','MANUAL_REVIEW','EXPERT_CORRECTION','PROCESSING_WARNING']),
    ('RELEASE_STATUS', ARRAY['DRAFT','SCOPE_LOCKED','GATES_RUNNING','GATES_FAILED','AWAITING_APPROVAL','APPROVED','DRY_RUN_REQUESTED','DEPLOYMENT_REQUESTED','DEPLOYED','ROLLBACK_REQUESTED','ROLLED_BACK','REJECTED']),
    ('GATE_STATUS', ARRAY['PASS','FAIL','WAIVED','NOT_RUN']),
    ('APPROVAL_STATUS', ARRAY['REQUESTED','IN_REVIEW','APPROVED','REJECTED']),
    ('GO_LIVE_DECISION', ARRAY['GO','GO_WITH_CONDITIONS','NO_GO','REASSESS']),
    ('SCOPE_STATUS', ARRAY['IN_SCOPE','OUT_OF_SCOPE','DEFERRED','EXPERIMENTAL','DISABLED']),
    ('STABILIZATION_STATUS', ARRAY['PLANNED','ACTIVE','COMPLETED','EXTENDED','CANCELLED']),
    ('COMPATIBILITY_STATUS', ARRAY['SUPPORTED','LIMITED','UNSUPPORTED','NOT_TESTED']),
    ('EVIDENCE_TYPE', ARRAY['SCREENSHOT','LOG_REFERENCE','TRACE_REFERENCE','ENTITY_SNAPSHOT','METRIC_SNAPSHOT']),
    ('VISIBILITY', ARRAY['TENANT','INTERNAL','RESTRICTED']),
    ('ARTIFACT_TYPE', ARRAY['BACKEND_IMAGE','FRONTEND_IMAGE','WORKER_IMAGE','OFFLINE_BUNDLE','MODEL_ARTIFACT','SBOM','LICENSE_REPORT']),
    ('COMPONENT', ARRAY['POSTGRESQL','MINIO','RABBITMQ','REDIS','KEYCLOAK','DOCKER','KUBERNETES','BROWSER','MODEL_RUNTIME','GPU_DRIVER','CPU_ARCHITECTURE','OPERATING_SYSTEM']),
    ('TRAINING_TOPIC', ARRAY['DOCUMENT_UPLOAD','DOCUMENT_CLASSIFICATION','EVIDENCE_APPROVAL','REQUIREMENT_CORRECTION','RISK_INTERPRETATION','REPORTING','WORKFLOW']),
    ('PRIORITY', ARRAY['LOW','MEDIUM','HIGH','URGENT']),
    ('PROCESS', ARRAY['SPECIFICATION_REVIEW','REQUIREMENT_REVIEW','EVIDENCE_REVIEW','REPORT_GENERATION']),
    ('MEASUREMENT_SOURCE', ARRAY['TIME_STUDY','SYSTEM_TELEMETRY','USER_SURVEY','HISTORICAL_RECORD']),
    ('SUPPORT_PROVIDER', ARRAY['GENERIC_WEBHOOK','MANUAL'])
), expanded AS (
    SELECT catalog.id AS catalog_id, value AS code, ordinal
      FROM seeds
      JOIN sprint9_concept_catalog catalog USING (catalog_code)
      CROSS JOIN LATERAL unnest(codes) WITH ORDINALITY AS entry(value, ordinal)
)
INSERT INTO sprint9_concept (catalog_id, concept_code, name, sort_order)
SELECT catalog_id, code, initcap(replace(code, '_', ' ')), ordinal FROM expanded;

INSERT INTO sprint9_policy_version (
    policy_code, policy_type, version_number, configuration_json, status,
    approved_by, approved_at
) VALUES
    ('ERROR_PRIORITY_DEFAULT', 'ERROR_PRIORITY', 1,
     '{"weights":{"impact":0.55,"frequency":0.30,"reproducibility":0.15},'
     '"blockerSeverity":["CRITICAL"],"maximumScore":100}'::jsonb,
     'ACTIVE', 'platform-seed', now()),
    ('RELEASE_BLOCKER_DEFAULT', 'RELEASE_BLOCKER', 1,
     '{"nonDeferrable":["TENANT_DATA_LEAK","UNAUTHORIZED_ACCESS","DATA_LOSS",'
     '"AUDIT_CORRUPTION","BACKUP_RESTORE_FAILURE","CRITICAL_VULNERABILITY",'
     '"RELEASE_ROLLBACK_UNAVAILABLE"],'
     '"requiresHumanDecision":true}'::jsonb,
     'ACTIVE', 'platform-seed', now()),
    ('RELEASE_APPROVAL_DEFAULT', 'RELEASE_APPROVAL', 1,
     '{"steps":["TECHNICAL_LEAD","SECURITY","PRODUCT","OPERATIONS","CUSTOMER_ACCEPTANCE"],'
     '"allRequired":true}'::jsonb,
     'ACTIVE', 'platform-seed', now()),
    ('ROLLOUT_DEFAULT', 'ROLLOUT', 1,
     '{"strategy":"TENANT_BY_TENANT","checkpoints":["HEALTH","ERROR_RATE","LATENCY",'
     '"QUEUE_DEPTH","MODEL_AVAILABILITY","PARSER_AVAILABILITY","TENANT_AUTHORIZATION",'
     '"DOCUMENT_UPLOAD","REQUIREMENT_EXTRACTION","REPORT_GENERATION","AUDIT","BACKUP",'
     '"SECURITY_ALERT"],"stopOnFailure":true}'::jsonb,
     'ACTIVE', 'platform-seed', now()),
    ('AUTOMATIC_ROLLBACK_DEFAULT', 'AUTOMATIC_ROLLBACK', 1,
     '{"signals":["CRITICAL_ERROR_RATE","AUTHENTICATION_FAILURE","CROSS_TENANT_ALERT",'
     '"DATABASE_MIGRATION_FAILURE","QUEUE_BACKLOG","MODEL_TIMEOUT_SPIKE",'
     '"PARSER_FAILURE_SPIKE","GROUNDING_FAILURE_SPIKE","REPORT_FAILURE_SPIKE",'
     '"AUDIT_INTEGRITY_FAILURE"],"safeStopBeforeRollback":true}'::jsonb,
     'ACTIVE', 'platform-seed', now()),
    ('STABILIZATION_MONITORING_DEFAULT', 'MONITORING', 1,
     '{"metrics":["error_rate","queue_depth","model_latency","parser_failure",'
     '"manual_review_rate","user_feedback","security_alert","backup","storage"]}'::jsonb,
     'ACTIVE', 'platform-seed', now()),
    ('HYPERCARE_SUPPORT_DEFAULT', 'SUPPORT', 1,
     '{"cadence":"DAILY","tasks":["SYSTEM_CHECK","ERROR_TRIAGE","USER_INTERVIEW",'
     '"QUALITY_METRICS","CAPACITY","MODEL_PERFORMANCE","BACKUP","SECURITY_ALERT_REVIEW"]}'::jsonb,
     'ACTIVE', 'platform-seed', now());

INSERT INTO release_gate_definition (
    gate_code, name, description, scope, required_by_default
)
SELECT code, initcap(replace(code, '_', ' ')),
       'Required v1.0 release-candidate evidence gate', 'RELEASE_CANDIDATE', TRUE
FROM unnest(ARRAY[
    'BUILD','UNIT_TEST','INTEGRATION_TEST','CONTRACT_TEST','ARCHITECTURE',
    'FRONTEND','E2E','SECURITY','PERFORMANCE','AI_QUALITY','REGRESSION',
    'BACKUP','RESTORE','OFFLINE_INSTALL','UPGRADE','ROLLBACK','UAT',
    'LICENSE','DOCUMENTATION','OPERATIONS'
]) AS code;

INSERT INTO pilot_metric_definition (
    metric_code, name, unit, allowed_dimensions_json
) VALUES
    ('pilot_feedback_total', 'Pilot feedback', 'count', '["project","pilot_phase"]'),
    ('feedback_blocker_total', 'Release blocker feedback', 'count', '["severity"]'),
    ('feedback_resolution_duration_seconds', 'Feedback resolution duration', 'seconds', '["feedback_type"]'),
    ('regression_case_total', 'Regression cases', 'count', '["case_type"]'),
    ('regression_failure_total', 'Regression failures', 'count', '["suite"]'),
    ('experiment_total', 'Experiments', 'count', '["experiment_type"]'),
    ('experiment_failure_total', 'Experiment failures', 'count', '["experiment_type"]'),
    ('quality_gate_failure_total', 'Quality gate failures', 'count', '["gate"]'),
    ('shadow_disagreement_rate', 'Shadow disagreement', 'ratio', '["candidate"]'),
    ('canary_error_rate', 'Canary error rate', 'ratio', '["candidate"]'),
    ('configuration_rollback_total', 'Configuration rollbacks', 'count', '["component"]'),
    ('release_gate_failure_total', 'Release gate failures', 'count', '["gate"]'),
    ('release_deployment_total', 'Release deployments', 'count', '["release_type"]'),
    ('release_rollback_total', 'Release rollbacks', 'count', '["release_type"]'),
    ('go_live_no_go_total', 'No-go decisions', 'count', '["release"]'),
    ('stabilization_incident_total', 'Stabilization incidents', 'count', '["severity"]'),
    ('user_satisfaction_score', 'User satisfaction', 'score', '["question"]'),
    ('manual_time_saved_minutes', 'Manual time saved', 'minutes', '["process"]');

INSERT INTO ui_configuration (
    id, configuration_code, configuration_json, active, created_at, updated_at
) VALUES
    (gen_random_uuid(), 'PILOT_QUALITY_DASHBOARD',
     '{"cards":["totalFeedback","openFeedback","releaseBlockers","meanResolutionHours",'
     '"regressionCoverage","manualReviewRate","expertCorrectionRate","satisfaction","uatStatus"],'
     '"charts":["rootCauseDistribution","qualityTrend","recurringErrors"],'
     '"source":"backend"}'::jsonb, TRUE, now(), now()),
    (gen_random_uuid(), 'ERROR_ANALYSIS_WORKSPACE',
     '{"layout":["feedbackSource","expectedActual","rootCauseVersionsActions"],'
     '"sensitiveContentRequires":["TENANT_SCOPE","DOCUMENT_READ"],'
     '"panels":["feedback","clause","requirement","evidence","modelRun","versions",'
     '"validation","grounding","confidence","trace","triage","candidate"]}'::jsonb,
     TRUE, now(), now()),
    (gen_random_uuid(), 'IMPROVEMENT_WORKSPACE',
     '{"actions":["CREATE","SELECT_BASELINE","SELECT_CANDIDATE","SELECT_DATASET",'
     '"SELECT_GATE","RUN_EXPERIMENT","COMPARE","SHADOW","CANARY","REJECT","ACTIVATE"],'
     '"activationRequires":["OFFLINE_PASS","APPROVAL","AUDIT"]}'::jsonb,
     TRUE, now(), now()),
    (gen_random_uuid(), 'RC_DASHBOARD',
     '{"sections":["scope","manifest","gates","approvals","dryRun","goLive",'
     '"stabilization","knownIssues"],"gateSource":"backend","allowClientOverrides":false}'::jsonb,
     TRUE, now(), now());

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'configuration_snapshot','pilot_session','pilot_event','pilot_metric_snapshot',
        'feedback_case','feedback_evidence','feedback_comment','error_triage_record',
        'sanitized_input_snapshot','reproduction_package','improvement_candidate',
        'experiment_definition','experiment_run','experiment_result','regression_suite',
        'regression_case','quality_debt_item','review_disagreement','training_need',
        'user_satisfaction_survey','process_baseline','business_value_metric_snapshot',
        'release_record','release_scope','release_artifact','release_configuration_manifest',
        'release_gate_result','release_approval_request','compatibility_matrix',
        'release_dry_run','rollout_checkpoint','go_live_decision','stabilization_window',
        'support_ticket_mapping','capacity_plan','configuration_activation_history',
        'diagnostic_bundle_request'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_only_%I ON %I USING (organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
            table_name, table_name
        );
    END LOOP;

    FOREACH table_name IN ARRAY ARRAY[
        'sprint9_concept_catalog','sprint9_concept','sprint9_policy_version',
        'pilot_metric_definition','survey_definition_version'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_or_global_%I ON %I USING (organization_id IS NULL OR organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
            table_name, table_name
        );
    END LOOP;
END
$$;
