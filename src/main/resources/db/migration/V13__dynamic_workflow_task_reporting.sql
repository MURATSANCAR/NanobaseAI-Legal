-- Sprint 7: tenant-scoped, versioned workflow, work management and reporting.
-- Business states, node types, decisions, formats and widget types are ontology
-- concepts. Only immutable platform/version lifecycle states remain strings.

CREATE TABLE workflow_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    workflow_code VARCHAR(160) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    scope VARCHAR(40) NOT NULL,
    entity_type_concept_id UUID REFERENCES ontology_concept(id),
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, workflow_code)
);

CREATE TABLE workflow_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (workflow_definition_id, version_number)
);
ALTER TABLE workflow_definition ADD CONSTRAINT fk_workflow_active_version
    FOREIGN KEY (active_version_id) REFERENCES workflow_version(id);

CREATE TABLE assignment_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE assignment_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    assignment_policy_id UUID NOT NULL REFERENCES assignment_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (assignment_policy_id, version_number)
);
ALTER TABLE assignment_policy ADD CONSTRAINT fk_assignment_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES assignment_policy_version(id);

CREATE TABLE approval_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE approval_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    approval_policy_id UUID NOT NULL REFERENCES approval_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (approval_policy_id, version_number)
);
ALTER TABLE approval_policy ADD CONSTRAINT fk_approval_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES approval_policy_version(id);

CREATE TABLE sla_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE sla_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    sla_policy_id UUID NOT NULL REFERENCES sla_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (sla_policy_id, version_number)
);
ALTER TABLE sla_policy ADD CONSTRAINT fk_sla_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES sla_policy_version(id);

CREATE TABLE escalation_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE escalation_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    escalation_policy_id UUID NOT NULL REFERENCES escalation_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (escalation_policy_id, version_number)
);
ALTER TABLE escalation_policy ADD CONSTRAINT fk_escalation_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES escalation_policy_version(id);

CREATE TABLE business_calendar (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    name VARCHAR(240) NOT NULL,
    timezone VARCHAR(80) NOT NULL,
    configuration_json JSONB NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE calendar_exception (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    business_calendar_id UUID NOT NULL REFERENCES business_calendar(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    exception_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (business_calendar_id, exception_date)
);

CREATE TABLE workflow_node (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    workflow_version_id UUID NOT NULL REFERENCES workflow_version(id) ON DELETE CASCADE,
    node_code VARCHAR(160) NOT NULL,
    node_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    name VARCHAR(240) NOT NULL,
    description TEXT,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (workflow_version_id, node_code)
);

CREATE TABLE workflow_transition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    workflow_version_id UUID NOT NULL REFERENCES workflow_version(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES workflow_node(id),
    target_node_id UUID NOT NULL REFERENCES workflow_node(id),
    transition_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    condition_expression_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    authorization_policy_id UUID REFERENCES policy_version(id),
    action_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workflow_instance (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definition(id),
    workflow_version_id UUID NOT NULL REFERENCES workflow_version(id),
    project_id UUID REFERENCES tender_project(id),
    subject_entity_type VARCHAR(160) NOT NULL,
    subject_entity_id UUID NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    current_state_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    context_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_by VARCHAR(255) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workflow_token (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    current_node_id UUID NOT NULL REFERENCES workflow_node(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    parent_token_id UUID REFERENCES workflow_token(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE workflow_execution (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    workflow_token_id UUID NOT NULL REFERENCES workflow_token(id),
    node_id UUID NOT NULL REFERENCES workflow_node(id),
    execution_status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    input_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(160),
    error_message VARCHAR(1000),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workflow_transition_log (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES workflow_node(id),
    target_node_id UUID NOT NULL REFERENCES workflow_node(id),
    transition_id UUID NOT NULL REFERENCES workflow_transition(id),
    decision_context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    triggered_by_type VARCHAR(80) NOT NULL,
    triggered_by_id VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workflow_simulation_run (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workflow_version_id UUID NOT NULL REFERENCES workflow_version(id) ON DELETE CASCADE,
    input_snapshot_json JSONB NOT NULL,
    result_json JSONB NOT NULL,
    valid BOOLEAN NOT NULL,
    simulated_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE business_role (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    role_code VARCHAR(160) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    scope VARCHAR(40) NOT NULL,
    attributes_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, role_code)
);

CREATE TABLE user_business_role (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    user_id VARCHAR(255) NOT NULL,
    business_role_id UUID NOT NULL REFERENCES business_role(id),
    scope_entity_type VARCHAR(160),
    scope_entity_id UUID,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE task_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id),
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    workflow_execution_id UUID REFERENCES workflow_execution(id),
    task_code VARCHAR(160) NOT NULL,
    task_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    subject_entity_type VARCHAR(160) NOT NULL,
    subject_entity_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    priority_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    assignment_policy_id UUID REFERENCES assignment_policy_version(id),
    assigned_user_id VARCHAR(255),
    assigned_group_id VARCHAR(255),
    due_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, task_code)
);

CREATE TABLE task_dependency (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    source_task_id UUID NOT NULL REFERENCES task_record(id),
    target_task_id UUID NOT NULL REFERENCES task_record(id),
    dependency_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (source_task_id, target_task_id, dependency_concept_id)
);

CREATE TABLE task_comment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    task_id UUID NOT NULL REFERENCES task_record(id) ON DELETE CASCADE,
    author_user_id VARCHAR(255) NOT NULL,
    comment_text TEXT NOT NULL,
    visibility_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE task_attachment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    task_id UUID NOT NULL REFERENCES task_record(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES document(id),
    document_version_id UUID REFERENCES document_version(id),
    attachment_role_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE approval_request (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id),
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    workflow_execution_id UUID REFERENCES workflow_execution(id),
    subject_entity_type VARCHAR(160) NOT NULL,
    subject_entity_id UUID NOT NULL,
    approval_policy_id UUID NOT NULL REFERENCES approval_policy_version(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE approval_step (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    approval_request_id UUID NOT NULL REFERENCES approval_request(id) ON DELETE CASCADE,
    step_code VARCHAR(160) NOT NULL,
    step_order INTEGER NOT NULL,
    approval_mode_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    required_approval_count INTEGER,
    assignment_policy_id UUID REFERENCES assignment_policy_version(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (approval_request_id, step_code)
);

CREATE TABLE approval_decision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    approval_request_id UUID NOT NULL REFERENCES approval_request(id),
    approval_step_id UUID NOT NULL REFERENCES approval_step(id),
    reviewer_user_id VARCHAR(255) NOT NULL,
    decision_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    comment TEXT,
    decision_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    decided_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (approval_step_id, reviewer_user_id)
);

CREATE TABLE task_sla_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    task_id UUID NOT NULL REFERENCES task_record(id) ON DELETE CASCADE,
    sla_policy_version_id UUID NOT NULL REFERENCES sla_policy_version(id),
    target_due_at TIMESTAMPTZ NOT NULL,
    warning_at TIMESTAMPTZ,
    breach_at TIMESTAMPTZ NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id)
);

CREATE TABLE escalation_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    task_id UUID REFERENCES task_record(id),
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    escalation_policy_version_id UUID NOT NULL REFERENCES escalation_policy_version(id),
    level_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    trigger_reason_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    target_user_id VARCHAR(255),
    target_group_id VARCHAR(255),
    triggered_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification_template (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    template_code VARCHAR(160) NOT NULL,
    channel_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    language VARCHAR(20) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, template_code, language)
);

CREATE TABLE notification_template_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    notification_template_id UUID NOT NULL REFERENCES notification_template(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    subject_template TEXT NOT NULL,
    body_template TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (notification_template_id, version_number)
);
ALTER TABLE notification_template ADD CONSTRAINT fk_notification_template_active_version
    FOREIGN KEY (active_version_id) REFERENCES notification_template_version(id);

CREATE TABLE notification_rule (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    rule_code VARCHAR(160) NOT NULL,
    trigger_event_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    condition_expression_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    recipient_policy_id UUID REFERENCES assignment_policy_version(id),
    template_id UUID NOT NULL REFERENCES notification_template(id),
    channel_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, rule_code)
);

CREATE TABLE notification_delivery (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    notification_rule_id UUID REFERENCES notification_rule(id),
    event_id UUID NOT NULL,
    recipient_reference VARCHAR(255) NOT NULL,
    safe_payload_json JSONB NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    provider_message_id VARCHAR(255),
    error_code VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    UNIQUE (organization_id, event_id, recipient_reference)
);

CREATE TABLE clarification_request (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    source_type VARCHAR(160) NOT NULL,
    source_id UUID NOT NULL,
    question_code VARCHAR(160) NOT NULL,
    question_text TEXT NOT NULL,
    reason TEXT,
    priority_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    requires_legal_review BOOLEAN NOT NULL DEFAULT FALSE,
    requires_technical_review BOOLEAN NOT NULL DEFAULT FALSE,
    external_recipient_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    approved_version_id UUID,
    sent_at TIMESTAMPTZ,
    answered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, project_id, question_code)
);

CREATE TABLE clarification_revision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    clarification_request_id UUID NOT NULL REFERENCES clarification_request(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    question_text TEXT NOT NULL,
    reason TEXT,
    source_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    edited_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (clarification_request_id, revision_number)
);
ALTER TABLE clarification_request ADD CONSTRAINT fk_clarification_approved_version
    FOREIGN KEY (approved_version_id) REFERENCES clarification_revision(id);

CREATE TABLE clarification_source (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    clarification_request_id UUID NOT NULL REFERENCES clarification_request(id) ON DELETE CASCADE,
    document_id UUID REFERENCES document(id),
    document_version_id UUID REFERENCES document_version(id),
    clause_id UUID REFERENCES clause(id),
    requirement_id UUID REFERENCES requirement(id),
    risk_id UUID REFERENCES risk_record(id),
    conflict_id UUID REFERENCES conflict_record(id),
    ambiguity_id UUID REFERENCES ambiguity_finding(id),
    source_text TEXT NOT NULL,
    page_number INTEGER,
    bounding_boxes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE clarification_answer (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    clarification_request_id UUID NOT NULL REFERENCES clarification_request(id),
    document_id UUID NOT NULL REFERENCES document(id),
    document_version_id UUID REFERENCES document_version(id),
    impact_analysis_job_id UUID REFERENCES impact_analysis_job(id),
    received_by VARCHAR(255) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE report_data_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE report_data_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    report_data_policy_id UUID NOT NULL REFERENCES report_data_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_data_policy_id, version_number)
);
ALTER TABLE report_data_policy ADD CONSTRAINT fk_report_data_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES report_data_policy_version(id);

CREATE TABLE report_template (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    template_code VARCHAR(160) NOT NULL,
    format_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    language VARCHAR(20) NOT NULL,
    branding_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, template_code, language, format_concept_id)
);

CREATE TABLE report_template_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    report_template_id UUID NOT NULL REFERENCES report_template(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    template_object_key VARCHAR(1000) NOT NULL,
    stylesheet_object_key VARCHAR(1000),
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_template_id, version_number)
);
ALTER TABLE report_template ADD CONSTRAINT fk_report_template_active_version
    FOREIGN KEY (active_version_id) REFERENCES report_template_version(id);

CREATE TABLE report_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    report_code VARCHAR(160) NOT NULL,
    name VARCHAR(240) NOT NULL,
    description TEXT,
    scope VARCHAR(40) NOT NULL,
    subject_entity_type_concept_id UUID REFERENCES ontology_concept(id),
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, report_code)
);

CREATE TABLE report_definition_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    report_definition_id UUID NOT NULL REFERENCES report_definition(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    section_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    data_policy_version_id UUID NOT NULL REFERENCES report_data_policy_version(id),
    template_version_id UUID NOT NULL REFERENCES report_template_version(id),
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_definition_id, version_number)
);
ALTER TABLE report_definition ADD CONSTRAINT fk_report_definition_active_version
    FOREIGN KEY (active_version_id) REFERENCES report_definition_version(id);

CREATE TABLE report_section_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    report_definition_version_id UUID NOT NULL REFERENCES report_definition_version(id) ON DELETE CASCADE,
    section_code VARCHAR(160) NOT NULL,
    section_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    title_template TEXT NOT NULL,
    data_query_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    render_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    visibility_condition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_definition_version_id, section_code)
);

CREATE TABLE report_data_snapshot (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    snapshot_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    requirement_version_cutoff BIGINT NOT NULL,
    compliance_version_cutoff BIGINT NOT NULL,
    risk_version_cutoff BIGINT NOT NULL,
    conflict_version_cutoff BIGINT NOT NULL,
    task_version_cutoff BIGINT NOT NULL,
    workflow_version_cutoff BIGINT NOT NULL,
    knowledge_snapshot_id UUID REFERENCES knowledge_snapshot(id),
    staleness_summary_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    snapshot_payload_json JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, content_hash)
);

CREATE TABLE report_generation_job (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    report_definition_version_id UUID NOT NULL REFERENCES report_definition_version(id),
    template_version_id UUID NOT NULL REFERENCES report_template_version(id),
    data_snapshot_id UUID NOT NULL REFERENCES report_data_snapshot(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    progress INTEGER NOT NULL DEFAULT 0,
    requested_by VARCHAR(255) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(160),
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (progress BETWEEN 0 AND 100)
);

CREATE TABLE report_artifact (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    report_generation_job_id UUID NOT NULL REFERENCES report_generation_job(id),
    format_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    object_storage_key VARCHAR(1000) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    mime_type VARCHAR(160) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    version_number INTEGER NOT NULL,
    stale_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (report_generation_job_id, format_concept_id, version_number)
);

CREATE TABLE decision_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE decision_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    decision_policy_id UUID NOT NULL REFERENCES decision_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (decision_policy_id, version_number)
);
ALTER TABLE decision_policy ADD CONSTRAINT fk_decision_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES decision_policy_version(id);

CREATE TABLE decision_support_case (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    case_code VARCHAR(160) NOT NULL,
    decision_policy_version_id UUID NOT NULL REFERENCES decision_policy_version(id),
    data_snapshot_id UUID NOT NULL REFERENCES report_data_snapshot(id),
    recommended_decision_concept_id UUID REFERENCES ontology_concept(id),
    final_decision_concept_id UUID REFERENCES ontology_concept(id),
    confidence NUMERIC(10,6) NOT NULL,
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    explanation_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (organization_id, case_code)
);

CREATE TABLE decision_support_factor (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    decision_support_case_id UUID NOT NULL REFERENCES decision_support_case(id) ON DELETE CASCADE,
    factor_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    effect_score NUMERIC(10,6) NOT NULL,
    weight NUMERIC(10,6) NOT NULL,
    description TEXT NOT NULL,
    source_reference_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE executive_decision (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    decision_support_case_id UUID NOT NULL REFERENCES decision_support_case(id),
    decision_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    decision_text TEXT NOT NULL,
    conditions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    decided_by VARCHAR(255) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE finalization_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(160) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE finalization_policy_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    finalization_policy_id UUID NOT NULL REFERENCES finalization_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (finalization_policy_id, version_number)
);
ALTER TABLE finalization_policy ADD CONSTRAINT fk_finalization_policy_active_version
    FOREIGN KEY (active_version_id) REFERENCES finalization_policy_version(id);

CREATE TABLE project_finalization_record (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id),
    workflow_instance_id UUID REFERENCES workflow_instance(id),
    finalization_policy_version_id UUID NOT NULL REFERENCES finalization_policy_version(id),
    decision_support_case_id UUID REFERENCES decision_support_case(id),
    executive_decision_id UUID REFERENCES executive_decision(id),
    final_report_artifact_id UUID REFERENCES report_artifact(id),
    status_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    validation_result_json JSONB NOT NULL,
    finalized_by VARCHAR(255),
    finalized_at TIMESTAMPTZ,
    reopened_at TIMESTAMPTZ,
    reopen_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE dashboard_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    dashboard_code VARCHAR(160) NOT NULL,
    name VARCHAR(240) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, dashboard_code)
);

CREATE TABLE dashboard_version (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    dashboard_definition_id UUID NOT NULL REFERENCES dashboard_definition(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    layout_configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (dashboard_definition_id, version_number)
);
ALTER TABLE dashboard_definition ADD CONSTRAINT fk_dashboard_active_version
    FOREIGN KEY (active_version_id) REFERENCES dashboard_version(id);

CREATE TABLE dashboard_widget (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    dashboard_version_id UUID NOT NULL REFERENCES dashboard_version(id) ON DELETE CASCADE,
    widget_code VARCHAR(160) NOT NULL,
    widget_type_concept_id UUID NOT NULL REFERENCES ontology_concept(id),
    data_source_configuration_json JSONB NOT NULL,
    display_configuration_json JSONB NOT NULL,
    visibility_condition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    position_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (dashboard_version_id, widget_code)
);

CREATE INDEX ix_workflow_instance_project ON workflow_instance (organization_id, project_id, created_at DESC);
CREATE INDEX ix_workflow_token_instance ON workflow_token (organization_id, workflow_instance_id);
CREATE INDEX ix_workflow_execution_instance ON workflow_execution (organization_id, workflow_instance_id, created_at);
CREATE INDEX ix_workflow_simulation_version ON workflow_simulation_run (organization_id, workflow_version_id, created_at DESC);
CREATE INDEX ix_task_assignee_status ON task_record (organization_id, assigned_user_id, status_concept_id, due_at);
CREATE INDEX ix_task_project ON task_record (organization_id, project_id, created_at DESC);
CREATE INDEX ix_approval_project ON approval_request (organization_id, project_id, requested_at DESC);
CREATE INDEX ix_clarification_project ON clarification_request (organization_id, project_id, created_at DESC);
CREATE INDEX ix_report_project ON report_generation_job (organization_id, project_id, created_at DESC);
CREATE INDEX ix_decision_project ON decision_support_case (organization_id, project_id, created_at DESC);
CREATE INDEX ix_finalization_project ON project_finalization_record (organization_id, project_id, created_at DESC);

-- Extensible global bootstrap concepts. They are data, never Java enums.
INSERT INTO ontology_concept (
    id, organization_id, ontology_version_id, concept_code, name, concept_type,
    metadata_json, active, sort_order, created_at
) VALUES
    ('70000000-0000-0000-0000-000000000001', NULL, '40000000-0000-0000-0000-000000000002', 'START', 'Start', 'WORKFLOW_NODE_TYPE', '{"provider":"automatic"}', TRUE, 200, now()),
    ('70000000-0000-0000-0000-000000000002', NULL, '40000000-0000-0000-0000-000000000002', 'TASK', 'Task', 'WORKFLOW_NODE_TYPE', '{"provider":"task"}', TRUE, 201, now()),
    ('70000000-0000-0000-0000-000000000003', NULL, '40000000-0000-0000-0000-000000000002', 'REVIEW', 'Review', 'WORKFLOW_NODE_TYPE', '{"provider":"approval"}', TRUE, 202, now()),
    ('70000000-0000-0000-0000-000000000004', NULL, '40000000-0000-0000-0000-000000000002', 'APPROVAL', 'Approval', 'WORKFLOW_NODE_TYPE', '{"provider":"approval"}', TRUE, 203, now()),
    ('70000000-0000-0000-0000-000000000005', NULL, '40000000-0000-0000-0000-000000000002', 'PARALLEL_GATEWAY', 'Parallel gateway', 'WORKFLOW_NODE_TYPE', '{"provider":"gateway"}', TRUE, 204, now()),
    ('70000000-0000-0000-0000-000000000006', NULL, '40000000-0000-0000-0000-000000000002', 'EXCLUSIVE_GATEWAY', 'Exclusive gateway', 'WORKFLOW_NODE_TYPE', '{"provider":"gateway"}', TRUE, 205, now()),
    ('70000000-0000-0000-0000-000000000007', NULL, '40000000-0000-0000-0000-000000000002', 'WAIT', 'Wait', 'WORKFLOW_NODE_TYPE', '{"provider":"wait"}', TRUE, 206, now()),
    ('70000000-0000-0000-0000-000000000008', NULL, '40000000-0000-0000-0000-000000000002', 'TIMER', 'Timer', 'WORKFLOW_NODE_TYPE', '{"provider":"timer"}', TRUE, 207, now()),
    ('70000000-0000-0000-0000-000000000009', NULL, '40000000-0000-0000-0000-000000000002', 'AI_ANALYSIS', 'AI analysis', 'WORKFLOW_NODE_TYPE', '{"provider":"analysis"}', TRUE, 208, now()),
    ('70000000-0000-0000-0000-000000000010', NULL, '40000000-0000-0000-0000-000000000002', 'MANUAL_DECISION', 'Manual decision', 'WORKFLOW_NODE_TYPE', '{"provider":"approval"}', TRUE, 209, now()),
    ('70000000-0000-0000-0000-000000000011', NULL, '40000000-0000-0000-0000-000000000002', 'REPORT_GENERATION', 'Report generation', 'WORKFLOW_NODE_TYPE', '{"provider":"report"}', TRUE, 210, now()),
    ('70000000-0000-0000-0000-000000000012', NULL, '40000000-0000-0000-0000-000000000002', 'NOTIFICATION', 'Notification', 'WORKFLOW_NODE_TYPE', '{"provider":"notification"}', TRUE, 211, now()),
    ('70000000-0000-0000-0000-000000000013', NULL, '40000000-0000-0000-0000-000000000002', 'SUB_WORKFLOW', 'Sub-workflow', 'WORKFLOW_NODE_TYPE', '{"provider":"subWorkflow"}', TRUE, 212, now()),
    ('70000000-0000-0000-0000-000000000014', NULL, '40000000-0000-0000-0000-000000000002', 'FINALIZATION', 'Finalization', 'WORKFLOW_NODE_TYPE', '{"provider":"finalization"}', TRUE, 213, now()),
    ('70000000-0000-0000-0000-000000000015', NULL, '40000000-0000-0000-0000-000000000002', 'END', 'End', 'WORKFLOW_NODE_TYPE', '{"provider":"automatic"}', TRUE, 214, now()),
    ('70000000-0000-0000-0000-000000000020', NULL, '40000000-0000-0000-0000-000000000002', 'WORKFLOW_ACTIVE', 'Workflow active', 'WORKFLOW_STATUS', '{}', TRUE, 220, now()),
    ('70000000-0000-0000-0000-000000000021', NULL, '40000000-0000-0000-0000-000000000002', 'WORKFLOW_COMPLETED', 'Workflow completed', 'WORKFLOW_STATUS', '{}', TRUE, 221, now()),
    ('70000000-0000-0000-0000-000000000022', NULL, '40000000-0000-0000-0000-000000000002', 'WORKFLOW_CANCELLED', 'Workflow cancelled', 'WORKFLOW_STATUS', '{}', TRUE, 222, now()),
    ('70000000-0000-0000-0000-000000000023', NULL, '40000000-0000-0000-0000-000000000002', 'EXECUTION_PENDING', 'Execution pending', 'EXECUTION_STATUS', '{}', TRUE, 223, now()),
    ('70000000-0000-0000-0000-000000000024', NULL, '40000000-0000-0000-0000-000000000002', 'EXECUTION_COMPLETED', 'Execution completed', 'EXECUTION_STATUS', '{}', TRUE, 224, now()),
    ('70000000-0000-0000-0000-000000000025', NULL, '40000000-0000-0000-0000-000000000002', 'TASK_OPEN', 'Open', 'TASK_STATUS', '{"actionEffect":"open"}', TRUE, 225, now()),
    ('70000000-0000-0000-0000-000000000026', NULL, '40000000-0000-0000-0000-000000000002', 'TASK_COMPLETED', 'Completed', 'TASK_STATUS', '{"actionEffect":"complete"}', TRUE, 226, now()),
    ('70000000-0000-0000-0000-000000000027', NULL, '40000000-0000-0000-0000-000000000002', 'TASK_BLOCKED', 'Blocked', 'TASK_STATUS', '{"actionEffect":"block"}', TRUE, 227, now()),
    ('70000000-0000-0000-0000-000000000028', NULL, '40000000-0000-0000-0000-000000000002', 'NORMAL_PRIORITY', 'Normal', 'TASK_PRIORITY', '{}', TRUE, 228, now()),
    ('70000000-0000-0000-0000-000000000029', NULL, '40000000-0000-0000-0000-000000000002', 'APPROVAL_PENDING', 'Pending', 'APPROVAL_STATUS', '{}', TRUE, 229, now()),
    ('70000000-0000-0000-0000-000000000030', NULL, '40000000-0000-0000-0000-000000000002', 'APPROVE', 'Approve', 'APPROVAL_DECISION', '{"effect":"positive"}', TRUE, 230, now()),
    ('70000000-0000-0000-0000-000000000031', NULL, '40000000-0000-0000-0000-000000000002', 'REJECT', 'Reject', 'APPROVAL_DECISION', '{"effect":"negative"}', TRUE, 231, now()),
    ('70000000-0000-0000-0000-000000000032', NULL, '40000000-0000-0000-0000-000000000002', 'REQUEST_CHANGES', 'Request changes', 'APPROVAL_DECISION', '{"effect":"changes"}', TRUE, 232, now()),
    ('70000000-0000-0000-0000-000000000033', NULL, '40000000-0000-0000-0000-000000000002', 'ANY_ONE', 'Any one', 'APPROVAL_MODE', '{"provider":"threshold"}', TRUE, 233, now()),
    ('70000000-0000-0000-0000-000000000034', NULL, '40000000-0000-0000-0000-000000000002', 'MINIMUM_COUNT', 'Minimum count', 'APPROVAL_MODE', '{"provider":"threshold"}', TRUE, 234, now()),
    ('70000000-0000-0000-0000-000000000035', NULL, '40000000-0000-0000-0000-000000000002', 'WEIGHTED_APPROVAL', 'Weighted approval', 'APPROVAL_MODE', '{"provider":"weighted"}', TRUE, 235, now()),
    ('70000000-0000-0000-0000-000000000036', NULL, '40000000-0000-0000-0000-000000000002', 'IN_APP', 'In-app', 'NOTIFICATION_CHANNEL', '{"provider":"inApp"}', TRUE, 236, now()),
    ('70000000-0000-0000-0000-000000000037', NULL, '40000000-0000-0000-0000-000000000002', 'EMAIL', 'Email', 'NOTIFICATION_CHANNEL', '{"provider":"email"}', TRUE, 237, now()),
    ('70000000-0000-0000-0000-000000000038', NULL, '40000000-0000-0000-0000-000000000002', 'PDF', 'PDF', 'REPORT_FORMAT', '{"provider":"pdf"}', TRUE, 238, now()),
    ('70000000-0000-0000-0000-000000000039', NULL, '40000000-0000-0000-0000-000000000002', 'DOCX', 'DOCX', 'REPORT_FORMAT', '{"provider":"docx"}', TRUE, 239, now()),
    ('70000000-0000-0000-0000-000000000040', NULL, '40000000-0000-0000-0000-000000000002', 'XLSX', 'XLSX', 'REPORT_FORMAT', '{"provider":"xlsx"}', TRUE, 240, now()),
    ('70000000-0000-0000-0000-000000000041', NULL, '40000000-0000-0000-0000-000000000002', 'CUSTOM_QUERY', 'Custom query', 'REPORT_SECTION_TYPE', '{"provider":"genericQuery"}', TRUE, 241, now()),
    ('70000000-0000-0000-0000-000000000042', NULL, '40000000-0000-0000-0000-000000000002', 'PROCEED_WITH_CONDITIONS', 'Proceed with conditions', 'EXECUTIVE_DECISION', '{}', TRUE, 242, now()),
    ('70000000-0000-0000-0000-000000000043', NULL, '40000000-0000-0000-0000-000000000002', 'MANUAL_EXECUTIVE_REVIEW', 'Manual executive review', 'EXECUTIVE_DECISION', '{}', TRUE, 243, now()),
    ('70000000-0000-0000-0000-000000000044', NULL, '40000000-0000-0000-0000-000000000002', 'KPI', 'KPI', 'DASHBOARD_WIDGET_TYPE', '{"provider":"metric"}', TRUE, 244, now()),
    ('70000000-0000-0000-0000-000000000045', NULL, '40000000-0000-0000-0000-000000000002', 'TABLE', 'Table', 'DASHBOARD_WIDGET_TYPE', '{"provider":"table"}', TRUE, 245, now()),
    ('70000000-0000-0000-0000-000000000046', NULL, '40000000-0000-0000-0000-000000000002', 'TRANSITION', 'Transition', 'WORKFLOW_TRANSITION', '{}', TRUE, 246, now()),
    ('70000000-0000-0000-0000-000000000047', NULL, '40000000-0000-0000-0000-000000000002', 'INTERNAL', 'Internal', 'COMMENT_VISIBILITY', '{}', TRUE, 247, now()),
    ('70000000-0000-0000-0000-000000000048', NULL, '40000000-0000-0000-0000-000000000002', 'REPORT_SNAPSHOT', 'Report snapshot', 'SNAPSHOT_TYPE', '{}', TRUE, 248, now()),
    ('70000000-0000-0000-0000-000000000049', NULL, '40000000-0000-0000-0000-000000000002', 'FINALIZED', 'Finalized', 'FINALIZATION_STATUS', '{"actionEffect":"finalize"}', TRUE, 249, now()),
    ('70000000-0000-0000-0000-000000000050', NULL, '40000000-0000-0000-0000-000000000002', 'REOPENED', 'Reopened', 'FINALIZATION_STATUS', '{"actionEffect":"reopen"}', TRUE, 250, now()),
    ('70000000-0000-0000-0000-000000000051', NULL, '40000000-0000-0000-0000-000000000002', 'APPROVAL_COMPLETED', 'Approval completed', 'APPROVAL_STATUS', '{"terminal":true,"result":"positive"}', TRUE, 251, now()),
    ('70000000-0000-0000-0000-000000000052', NULL, '40000000-0000-0000-0000-000000000002', 'APPROVAL_REJECTED', 'Approval rejected', 'APPROVAL_STATUS', '{"terminal":true,"result":"negative"}', TRUE, 252, now()),
    ('70000000-0000-0000-0000-000000000053', NULL, '40000000-0000-0000-0000-000000000002', 'CLARIFICATION_CANDIDATE', 'Candidate', 'CLARIFICATION_STATUS', '{"actionEffect":"candidate"}', TRUE, 253, now()),
    ('70000000-0000-0000-0000-000000000054', NULL, '40000000-0000-0000-0000-000000000002', 'CLARIFICATION_UNDER_REVIEW', 'Under review', 'CLARIFICATION_STATUS', '{"actionEffect":"review"}', TRUE, 254, now()),
    ('70000000-0000-0000-0000-000000000055', NULL, '40000000-0000-0000-0000-000000000002', 'CLARIFICATION_APPROVED', 'Approved', 'CLARIFICATION_STATUS', '{"actionEffect":"approve"}', TRUE, 255, now()),
    ('70000000-0000-0000-0000-000000000056', NULL, '40000000-0000-0000-0000-000000000002', 'CLARIFICATION_SENT', 'Sent', 'CLARIFICATION_STATUS', '{"actionEffect":"send"}', TRUE, 256, now()),
    ('70000000-0000-0000-0000-000000000057', NULL, '40000000-0000-0000-0000-000000000002', 'CLARIFICATION_ANSWER_RECEIVED', 'Answer received', 'CLARIFICATION_STATUS', '{"actionEffect":"answer"}', TRUE, 257, now()),
    ('70000000-0000-0000-0000-000000000058', NULL, '40000000-0000-0000-0000-000000000002', 'REPORT_JOB_RUNNING', 'Report running', 'REPORT_JOB_STATUS', '{"terminal":false}', TRUE, 258, now()),
    ('70000000-0000-0000-0000-000000000059', NULL, '40000000-0000-0000-0000-000000000002', 'REPORT_JOB_COMPLETED', 'Report completed', 'REPORT_JOB_STATUS', '{"terminal":true,"result":"success"}', TRUE, 259, now()),
    ('70000000-0000-0000-0000-000000000060', NULL, '40000000-0000-0000-0000-000000000002', 'REPORT_JOB_FAILED', 'Report failed', 'REPORT_JOB_STATUS', '{"terminal":true,"result":"failure"}', TRUE, 260, now()),
    ('70000000-0000-0000-0000-000000000061', NULL, '40000000-0000-0000-0000-000000000002', 'DECISION_SUPPORT_READY', 'Decision support ready', 'DECISION_SUPPORT_STATUS', '{"humanDecisionRequired":true}', TRUE, 261, now()),
    ('70000000-0000-0000-0000-000000000062', NULL, '40000000-0000-0000-0000-000000000002', 'VERIFIED_READINESS', 'Verified readiness', 'DECISION_SUPPORT_FACTOR', '{}', TRUE, 262, now()),
    ('70000000-0000-0000-0000-000000000063', NULL, '40000000-0000-0000-0000-000000000002', 'OPEN_CONTROL_ITEMS', 'Open control items', 'DECISION_SUPPORT_FACTOR', '{}', TRUE, 263, now()),
    ('70000000-0000-0000-0000-000000000064', NULL, '40000000-0000-0000-0000-000000000002', 'NOTIFICATION_PENDING', 'Notification pending', 'NOTIFICATION_STATUS', '{"terminal":false}', TRUE, 264, now()),
    ('70000000-0000-0000-0000-000000000065', NULL, '40000000-0000-0000-0000-000000000002', 'NOTIFICATION_SENT', 'Notification sent', 'NOTIFICATION_STATUS', '{"terminal":true,"result":"success"}', TRUE, 265, now()),
    ('70000000-0000-0000-0000-000000000066', NULL, '40000000-0000-0000-0000-000000000002', 'NOTIFICATION_FAILED', 'Notification failed', 'NOTIFICATION_STATUS', '{"terminal":true,"result":"failure"}', TRUE, 266, now());

INSERT INTO report_data_policy (
    id, organization_id, policy_code, scope, created_at, updated_at
) VALUES (
    '70000000-0000-0000-0000-000000000100', NULL,
    'GLOBAL_WARN_ON_STALE', 'GLOBAL', now(), now()
);
INSERT INTO report_data_policy_version (
    id, organization_id, report_data_policy_id, version_number,
    configuration_json, status, approved_by, approved_at, created_at
) VALUES (
    '70000000-0000-0000-0000-000000000101', NULL,
    '70000000-0000-0000-0000-000000000100', 1,
    '{"staleBehavior":"WARN","includeStalenessSummary":true}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE report_data_policy
SET active_version_id = '70000000-0000-0000-0000-000000000101'
WHERE id = '70000000-0000-0000-0000-000000000100';

INSERT INTO report_template (
    id, organization_id, template_code, format_concept_id, language,
    branding_configuration_json, created_at, updated_at
) VALUES (
    '70000000-0000-0000-0000-000000000110', NULL,
    'GLOBAL_SAFE_STRUCTURED', '70000000-0000-0000-0000-000000000038', 'tr',
    '{"title":"NANObaseAI Legal","executableTemplates":false}'::jsonb, now(), now()
);
INSERT INTO report_template_version (
    id, organization_id, report_template_id, version_number,
    template_object_key, configuration_json, status, approved_by, approved_at, created_at
) VALUES (
    '70000000-0000-0000-0000-000000000111', NULL,
    '70000000-0000-0000-0000-000000000110', 1,
    'platform/report-templates/safe-structured-v1.json',
    '{"renderer":"SAFE_STRUCTURED","allowExecutableContent":false}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE report_template
SET active_version_id = '70000000-0000-0000-0000-000000000111'
WHERE id = '70000000-0000-0000-0000-000000000110';

INSERT INTO decision_policy (
    id, organization_id, policy_code, scope, created_at, updated_at
) VALUES (
    '70000000-0000-0000-0000-000000000119', NULL,
    'GLOBAL_EXPLAINABLE_DECISION_SUPPORT', 'GLOBAL', now(), now()
);
INSERT INTO decision_policy_version (
    id, organization_id, decision_policy_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES (
    '70000000-0000-0000-0000-000000000120', NULL,
    '70000000-0000-0000-0000-000000000119', 1,
    '{"defaultConfidence":0.0,"maximumExplanationFactors":10,
      "factorRules":[
        {"factorConceptCode":"VERIFIED_READINESS","effectScore":0.6,"weight":1.0,
         "description":"Verified snapshot contains no open task or approval.",
         "condition":{"all":[
           {"field":"counts.openTasks","operator":"EQUAL","value":0},
           {"field":"counts.pendingApprovals","operator":"EQUAL","value":0}
         ]}},
        {"factorConceptCode":"OPEN_CONTROL_ITEMS","effectScore":-0.8,"weight":1.0,
         "description":"Verified snapshot contains open control items.",
         "condition":{"any":[
           {"field":"counts.openTasks","operator":"GREATER_THAN","value":0},
           {"field":"counts.pendingApprovals","operator":"GREATER_THAN","value":0},
           {"field":"staleness.count","operator":"GREATER_THAN","value":0}
         ]}}
      ],
      "decisionBands":[
        {"minimumScore":-1.0,"maximumScore":-0.000001,
         "decisionConceptCode":"MANUAL_EXECUTIVE_REVIEW","requiresExecutiveReview":true},
        {"minimumScore":0.0,"maximumScore":1.0,
         "decisionConceptCode":"PROCEED_WITH_CONDITIONS","requiresExecutiveReview":true}
      ]}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE decision_policy
SET active_version_id = '70000000-0000-0000-0000-000000000120'
WHERE id = '70000000-0000-0000-0000-000000000119';

INSERT INTO finalization_policy (
    id, organization_id, policy_code, scope, created_at, updated_at
) VALUES (
    '70000000-0000-0000-0000-000000000129', NULL,
    'GLOBAL_HUMAN_CONTROLLED_FINALIZATION', 'GLOBAL', now(), now()
);
INSERT INTO finalization_policy_version (
    id, organization_id, finalization_policy_id, version_number, configuration_json,
    status, approved_by, approved_at, created_at
) VALUES (
    '70000000-0000-0000-0000-000000000130', NULL,
    '70000000-0000-0000-0000-000000000129', 1,
    '{"rules":[
      {"code":"EXECUTIVE_DECISION_REQUIRED","severity":"BLOCKING",
       "condition":{"field":"project.executiveDecisionRecorded","operator":"EQUAL","value":true}},
      {"code":"FINAL_REPORT_REQUIRED","severity":"BLOCKING",
       "condition":{"field":"project.finalReportPresent","operator":"EQUAL","value":true}},
      {"code":"NO_PENDING_MANDATORY_APPROVAL","severity":"BLOCKING",
       "condition":{"field":"project.pendingMandatoryApprovalCount","operator":"EQUAL","value":0}},
      {"code":"STALE_RESULTS_HANDLED","severity":"BLOCKING",
       "condition":{"field":"project.unhandledStaleCount","operator":"EQUAL","value":0}}
    ]}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE finalization_policy
SET active_version_id = '70000000-0000-0000-0000-000000000130'
WHERE id = '70000000-0000-0000-0000-000000000129';

INSERT INTO dashboard_definition (
    id, organization_id, dashboard_code, name, scope, created_at, updated_at
) VALUES (
    '70000000-0000-0000-0000-000000000140', NULL,
    'SPRINT_7_OPERATIONS', 'Workflow operations', 'GLOBAL', now(), now()
);
INSERT INTO dashboard_version (
    id, organization_id, dashboard_definition_id, version_number,
    layout_configuration_json, status, approved_by, approved_at, created_at
) VALUES (
    '70000000-0000-0000-0000-000000000141', NULL,
    '70000000-0000-0000-0000-000000000140', 1,
    '{"columns":12,"rowHeight":72,"responsive":true}'::jsonb,
    'ACTIVE', 'platform', now(), now()
);
UPDATE dashboard_definition
SET active_version_id = '70000000-0000-0000-0000-000000000141'
WHERE id = '70000000-0000-0000-0000-000000000140';
INSERT INTO dashboard_widget (
    id, organization_id, dashboard_version_id, widget_code,
    widget_type_concept_id, data_source_configuration_json,
    display_configuration_json, visibility_condition_json, position_json, created_at
) VALUES
    ('70000000-0000-0000-0000-000000000142', NULL,
     '70000000-0000-0000-0000-000000000141', 'OPEN_TASK_COUNT',
     '70000000-0000-0000-0000-000000000044',
     '{"provider":"SAFE_AGGREGATE","entity":"TASK","metric":"OPEN_COUNT"}'::jsonb,
     '{"title":"Açık görevler","format":"INTEGER"}'::jsonb, '{}'::jsonb,
     '{"x":0,"y":0,"w":3,"h":2}'::jsonb, now()),
    ('70000000-0000-0000-0000-000000000143', NULL,
     '70000000-0000-0000-0000-000000000141', 'PENDING_APPROVAL_COUNT',
     '70000000-0000-0000-0000-000000000044',
     '{"provider":"SAFE_AGGREGATE","entity":"APPROVAL","metric":"PENDING_COUNT"}'::jsonb,
     '{"title":"Bekleyen onaylar","format":"INTEGER"}'::jsonb, '{}'::jsonb,
     '{"x":3,"y":0,"w":3,"h":2}'::jsonb, now()),
    ('70000000-0000-0000-0000-000000000144', NULL,
     '70000000-0000-0000-0000-000000000141', 'TASK_CENTER',
     '70000000-0000-0000-0000-000000000045',
     '{"provider":"SAFE_TASK_LIST","columns":[
       "taskCode","title","taskTypeConceptCode","projectId","assignedUserId",
       "assignedGroupId","priorityConceptCode","dueAt","statusConceptCode",
       "workflowInstanceId"
     ]}'::jsonb,
     '{"title":"Görev merkezi","pageSize":20}'::jsonb, '{}'::jsonb,
     '{"x":0,"y":2,"w":12,"h":6}'::jsonb, now());

-- RLS is forced on every Sprint 7 table, including global configuration.
-- Global rows are readable, but tenant writes must always carry the current tenant.
DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'workflow_definition', 'workflow_version', 'workflow_node', 'workflow_transition',
        'workflow_instance', 'workflow_token', 'workflow_execution', 'workflow_transition_log',
        'workflow_simulation_run',
        'assignment_policy', 'assignment_policy_version', 'business_role', 'user_business_role',
        'task_record', 'task_dependency', 'task_comment', 'task_attachment',
        'approval_policy', 'approval_policy_version', 'approval_request', 'approval_step',
        'approval_decision', 'sla_policy', 'sla_policy_version', 'escalation_policy',
        'escalation_policy_version',
        'task_sla_record', 'escalation_record', 'business_calendar', 'calendar_exception',
        'notification_template', 'notification_template_version', 'notification_rule',
        'notification_delivery', 'clarification_request', 'clarification_revision',
        'clarification_source', 'clarification_answer', 'report_definition',
        'report_definition_version', 'report_section_definition', 'report_template',
        'report_template_version', 'report_data_policy', 'report_data_policy_version',
        'report_generation_job', 'report_data_snapshot', 'report_artifact',
        'decision_policy', 'decision_policy_version', 'decision_support_case',
        'decision_support_factor', 'executive_decision', 'finalization_policy',
        'finalization_policy_version', 'project_finalization_record',
        'dashboard_definition', 'dashboard_version', 'dashboard_widget'
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
