-- Sprint 8 production controls. Global definitions are intentionally separated
-- from tenant/project assignments so customer policy changes do not require code.

CREATE TABLE feature_definition (
    id UUID PRIMARY KEY,
    feature_code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    default_state BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE feature_assignment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id) ON DELETE CASCADE,
    feature_definition_id UUID NOT NULL REFERENCES feature_definition(id),
    enabled BOOLEAN NOT NULL,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_feature_assignment_window
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from),
    UNIQUE NULLS NOT DISTINCT (organization_id, project_id, feature_definition_id)
);

CREATE TABLE quota_definition (
    id UUID PRIMARY KEY,
    quota_code VARCHAR(120) NOT NULL UNIQUE,
    resource_concept_id UUID,
    default_limit BIGINT NOT NULL,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_quota_default_limit CHECK (default_limit >= 0)
);

CREATE TABLE quota_assignment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id) ON DELETE CASCADE,
    quota_definition_id UUID NOT NULL REFERENCES quota_definition(id),
    limit_value BIGINT NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_quota_assignment_limit CHECK (limit_value >= 0),
    CONSTRAINT ck_quota_assignment_window
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until > valid_from),
    UNIQUE NULLS NOT DISTINCT (organization_id, project_id, quota_definition_id)
);

CREATE TABLE rate_limit_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(120) NOT NULL,
    endpoint_pattern VARCHAR(300) NOT NULL,
    http_method VARCHAR(12) NOT NULL,
    limit_value INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    signal_configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_rate_limit_positive CHECK (limit_value > 0 AND window_seconds > 0),
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE backpressure_policy (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    policy_code VARCHAR(120) NOT NULL,
    workload_type VARCHAR(120) NOT NULL,
    configuration_json JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, policy_code)
);

CREATE TABLE performance_budget (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    deployment_profile VARCHAR(80) NOT NULL,
    operation_code VARCHAR(160) NOT NULL,
    percentile VARCHAR(20) NOT NULL,
    target_milliseconds BIGINT NOT NULL,
    configuration_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_performance_budget_positive CHECK (target_milliseconds > 0),
    UNIQUE NULLS NOT DISTINCT (
        organization_id, deployment_profile, operation_code, percentile
    )
);

CREATE TABLE file_security_assessment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id) ON DELETE CASCADE,
    document_version_id UUID REFERENCES document_version(id) ON DELETE CASCADE,
    content_sha256 VARCHAR(64) NOT NULL,
    scanner VARCHAR(100) NOT NULL,
    scanner_version VARCHAR(100),
    status VARCHAR(40) NOT NULL,
    signals_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    correlation_id UUID NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_file_security_status CHECK (status IN (
        'QUARANTINED', 'SECURITY_SCANNING', 'SAFE', 'MALICIOUS',
        'SECURITY_SCAN_FAILED', 'MANUAL_SECURITY_REVIEW', 'REJECTED'
    ))
);

CREATE TABLE prompt_security_assessment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    clause_id UUID REFERENCES clause(id) ON DELETE CASCADE,
    assessment_profile_id UUID REFERENCES analysis_profile(id),
    status_concept_id UUID,
    signal_score NUMERIC(6,5) NOT NULL,
    signals_json JSONB NOT NULL,
    review_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_prompt_security_score CHECK (signal_score BETWEEN 0 AND 1)
);

CREATE TABLE quality_gate_definition (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    gate_code VARCHAR(120) NOT NULL,
    scope VARCHAR(80) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE NULLS NOT DISTINCT (organization_id, gate_code)
);

CREATE TABLE quality_gate_version (
    id UUID PRIMARY KEY,
    quality_gate_definition_id UUID NOT NULL
        REFERENCES quality_gate_definition(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_quality_gate_version_number CHECK (version_number > 0),
    CONSTRAINT ck_quality_gate_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED')
    ),
    UNIQUE (quality_gate_definition_id, version_number)
);

ALTER TABLE quality_gate_definition
    ADD CONSTRAINT fk_quality_gate_active_version
    FOREIGN KEY (active_version_id) REFERENCES quality_gate_version(id);

CREATE TABLE evaluation_result_item (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    evaluation_run_id UUID NOT NULL REFERENCES evaluation_run(id) ON DELETE CASCADE,
    evaluation_case_id UUID NOT NULL REFERENCES evaluation_case(id),
    expected_output_json JSONB NOT NULL,
    actual_output_json JSONB NOT NULL,
    metric_results_json JSONB NOT NULL,
    error_analysis_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_run_id UUID REFERENCES model_run(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (evaluation_run_id, evaluation_case_id)
);

ALTER TABLE evaluation_run
    ADD COLUMN candidate_configuration_snapshot_id UUID,
    ADD COLUMN baseline_configuration_snapshot_id UUID,
    ADD COLUMN comparison_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN created_by VARCHAR(255);

ALTER TABLE evaluation_case
    ADD COLUMN second_reviewer VARCHAR(255),
    ADD COLUMN disagreement_status VARCHAR(40),
    ADD COLUMN adjudicated_by VARCHAR(255),
    ADD COLUMN ocr_quality VARCHAR(40),
    ADD COLUMN label_source VARCHAR(120);

CREATE TABLE shadow_execution (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id) ON DELETE CASCADE,
    active_configuration_snapshot_id UUID NOT NULL,
    candidate_configuration_snapshot_id UUID NOT NULL,
    workload_type VARCHAR(120) NOT NULL,
    active_result_reference UUID,
    candidate_result_json JSONB,
    comparison_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_shadow_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    )
);

CREATE TABLE canary_assignment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID REFERENCES tender_project(id) ON DELETE CASCADE,
    user_group VARCHAR(160),
    configuration_snapshot_id UUID NOT NULL,
    traffic_percentage NUMERIC(5,2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    rollback_configuration_snapshot_id UUID NOT NULL,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_canary_percentage CHECK (
        traffic_percentage > 0 AND traffic_percentage <= 100
    ),
    CONSTRAINT ck_canary_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ROLLED_BACK', 'COMPLETED')
    )
);

CREATE TABLE recovery_policy (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    deployment_profile_id VARCHAR(120) NOT NULL,
    rpo_minutes INTEGER NOT NULL,
    rto_minutes INTEGER NOT NULL,
    backup_configuration_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_recovery_targets CHECK (rpo_minutes >= 0 AND rto_minutes > 0),
    UNIQUE (organization_id, deployment_profile_id)
);

CREATE TABLE retention_policy (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    resource_concept_id UUID,
    resource_code VARCHAR(120) NOT NULL,
    retention_period INTERVAL NOT NULL,
    archive_behavior_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    deletion_behavior_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    legal_hold_supported BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, resource_code)
);

CREATE TABLE data_classification_policy (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    policy_code VARCHAR(120) NOT NULL,
    scope VARCHAR(80) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, policy_code)
);

CREATE TABLE data_classification_policy_version (
    id UUID PRIMARY KEY,
    data_classification_policy_id UUID NOT NULL
        REFERENCES data_classification_policy(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    classes_json JSONB NOT NULL,
    enforcement_json JSONB NOT NULL,
    masking_json JSONB NOT NULL,
    status VARCHAR(40) NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (data_classification_policy_id, version_number)
);

ALTER TABLE data_classification_policy
    ADD CONSTRAINT fk_classification_active_version
    FOREIGN KEY (active_version_id) REFERENCES data_classification_policy_version(id);

CREATE TABLE go_live_checklist_item (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    category VARCHAR(120) NOT NULL,
    item_code VARCHAR(160) NOT NULL,
    owner VARCHAR(255),
    evidence_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(40) NOT NULL,
    due_date DATE,
    blocker_severity VARCHAR(40),
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, item_code)
);

CREATE INDEX ix_feature_assignment_resolution
    ON feature_assignment (organization_id, project_id, feature_definition_id);
CREATE INDEX ix_quota_assignment_resolution
    ON quota_assignment (organization_id, project_id, quota_definition_id);
CREATE INDEX ix_security_assessment_hash
    ON file_security_assessment (organization_id, content_sha256, assessed_at DESC);
CREATE INDEX ix_prompt_security_review
    ON prompt_security_assessment (organization_id, review_status, created_at DESC);
CREATE INDEX ix_evaluation_result_run
    ON evaluation_result_item (evaluation_run_id, evaluation_case_id);
CREATE INDEX ix_shadow_execution_status
    ON shadow_execution (organization_id, status, created_at DESC);
CREATE INDEX ix_go_live_blockers
    ON go_live_checklist_item (organization_id, blocker_severity, status);

-- Append-only audit events are additionally chained. pgcrypto is the only
-- extension introduced by this release and is part of the production allowlist.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
ALTER TABLE audit_event
    ADD COLUMN previous_hash VARCHAR(64),
    ADD COLUMN event_hash VARCHAR(64);

CREATE OR REPLACE FUNCTION assign_audit_hash() RETURNS trigger AS $$
BEGIN
    SELECT event_hash INTO NEW.previous_hash
      FROM audit_event
     WHERE organization_id = NEW.organization_id
     ORDER BY created_at DESC, id DESC
     LIMIT 1;
    NEW.event_hash := encode(digest(
        coalesce(NEW.previous_hash, '') || NEW.id::text || NEW.organization_id::text
        || NEW.user_id || NEW.event_type || NEW.entity_type || NEW.entity_id::text
        || NEW.created_at::text || NEW.correlation_id::text
        || coalesce(NEW.before_json::text, '') || NEW.after_json::text,
        'sha256'
    ), 'hex');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE audit_event DISABLE TRIGGER audit_event_no_update;
DO $$
DECLARE
    organization_record RECORD;
    event_record RECORD;
    previous_value VARCHAR(64);
    calculated_hash VARCHAR(64);
BEGIN
    FOR organization_record IN
        SELECT DISTINCT organization_id FROM audit_event
    LOOP
        previous_value := NULL;
        FOR event_record IN
            SELECT * FROM audit_event
             WHERE organization_id = organization_record.organization_id
             ORDER BY created_at, id
        LOOP
            calculated_hash := encode(digest(
                coalesce(previous_value, '') || event_record.id::text
                || event_record.organization_id::text || event_record.user_id
                || event_record.event_type || event_record.entity_type
                || event_record.entity_id::text || event_record.created_at::text
                || event_record.correlation_id::text
                || coalesce(event_record.before_json::text, '')
                || event_record.after_json::text,
                'sha256'
            ), 'hex');
            UPDATE audit_event
               SET previous_hash = previous_value,
                   event_hash = calculated_hash
             WHERE id = event_record.id;
            previous_value := calculated_hash;
        END LOOP;
    END LOOP;
END
$$;
ALTER TABLE audit_event ENABLE TRIGGER audit_event_no_update;

CREATE TRIGGER audit_event_hash_before_insert
    BEFORE INSERT ON audit_event
    FOR EACH ROW EXECUTE FUNCTION assign_audit_hash();

CREATE OR REPLACE FUNCTION verify_audit_chain(requested_organization UUID)
RETURNS TABLE (valid BOOLEAN, invalid_event_id UUID)
LANGUAGE sql
STABLE
AS $$
    WITH calculated AS (
        SELECT id,
               event_hash,
               previous_hash,
               lag(event_hash) OVER (
                   PARTITION BY organization_id ORDER BY created_at, id
               ) AS expected_previous
          FROM audit_event
         WHERE organization_id = requested_organization
    )
    SELECT NOT EXISTS (
               SELECT 1 FROM calculated
                WHERE previous_hash IS DISTINCT FROM expected_previous
           ),
           (SELECT id FROM calculated
             WHERE previous_hash IS DISTINCT FROM expected_previous
             LIMIT 1)
$$;

INSERT INTO feature_definition (
    id, feature_code, name, description, default_state, created_at, updated_at
) VALUES
    ('81000000-0000-0000-0000-000000000001', 'OPENCONTRACTS_INTEGRATION',
     'OpenContracts integration', 'Alternative parser integration', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000002', 'OCR_PROCESSING',
     'OCR processing', 'OCR for image-based documents', TRUE, now(), now()),
    ('81000000-0000-0000-0000-000000000003', 'KNOWLEDGE_GRAPH',
     'Knowledge graph', 'Dynamic entity and relation extraction', TRUE, now(), now()),
    ('81000000-0000-0000-0000-000000000004', 'COMPLIANCE_ANALYSIS',
     'Compliance analysis', 'Evidence-based compliance evaluation', TRUE, now(), now()),
    ('81000000-0000-0000-0000-000000000005', 'RISK_ANALYSIS',
     'Risk analysis', 'Risk, conflict and ambiguity analysis', TRUE, now(), now()),
    ('81000000-0000-0000-0000-000000000006', 'WORKFLOW_DESIGNER',
     'Workflow designer', 'Dynamic workflow authoring', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000007', 'EXECUTIVE_DECISION_SUPPORT',
     'Executive decision support', 'Executive decision workspace', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000008', 'EXPERIMENTAL_MODEL_ROUTING',
     'Experimental model routing', 'Candidate routing for shadow evaluation', FALSE, now(), now());

INSERT INTO quota_definition (
    id, quota_code, default_limit, configuration_json, created_at, updated_at
) VALUES
    ('82000000-0000-0000-0000-000000000001', 'STORAGE_BYTES', 10737418240,
     '{"unit":"bytes","enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000002', 'DOCUMENT_COUNT', 10000,
     '{"enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000003', 'PAGE_COUNT', 250000,
     '{"enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000004', 'PROCESSING_JOB_COUNT', 50000,
     '{"period":"P30D","enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000005', 'CONCURRENT_ANALYSIS', 10,
     '{"enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000006', 'LLM_TOKEN_BUDGET', 10000000,
     '{"period":"P30D","enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000007', 'REPORT_COUNT', 1000,
     '{"period":"P30D","enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000008', 'SSE_CONNECTION_COUNT', 50,
     '{"enforcement":"HARD"}', now(), now()),
    ('82000000-0000-0000-0000-000000000009', 'USER_COUNT', 250,
     '{"enforcement":"HARD"}', now(), now());

INSERT INTO rate_limit_policy (
    id, policy_code, endpoint_pattern, http_method, limit_value, window_seconds,
    signal_configuration_json, enabled, created_at, updated_at
) VALUES
    ('83000000-0000-0000-0000-000000000001', 'FILE_UPLOAD',
     '^/api/v1/(tenders/[^/]+/documents|documents/[^/]+/versions)$',
     'POST', 10, 60, '{"signals":["USER","TENANT","IP","FILE_SIZE"]}', TRUE, now(), now()),
    ('83000000-0000-0000-0000-000000000002', 'SIGNED_DOWNLOAD_URL',
     '^/api/v1/documents/[^/]+/download-url$', 'GET', 30, 60,
     '{"signals":["USER","TENANT","IP"]}', TRUE, now(), now()),
    ('83000000-0000-0000-0000-000000000003', 'ANALYSIS_START',
     '^/api/v1/.+-(extractions|analyses)$', 'POST', 20, 60,
     '{"signals":["USER","TENANT","QUEUE_DEPTH","MODEL_CAPACITY"]}', TRUE, now(), now()),
    ('83000000-0000-0000-0000-000000000004', 'SSE_CONNECTION',
     '^/api/v1/.+/(events|processing-events)$', 'GET', 10, 60,
     '{"signals":["USER","TENANT","IP","CURRENT_WORKLOAD"]}', TRUE, now(), now());

INSERT INTO backpressure_policy (
    id, policy_code, workload_type, configuration_json, created_at, updated_at
) VALUES
    ('84000000-0000-0000-0000-000000000001', 'DOCUMENT_PROCESSING_DEFAULT',
     'DOCUMENT_PROCESSING',
     '{"queueDepth":{"delay":100,"reject":500},"oldestJobSeconds":{"reject":1800},'
       || '"decisions":["ACCEPT","ACCEPT_WITH_DELAY","QUEUE","REJECT_TEMPORARILY"]}',
     now(), now()),
    ('84000000-0000-0000-0000-000000000002', 'MODEL_ANALYSIS_DEFAULT',
     'MODEL_ANALYSIS',
     '{"queueDepth":{"delay":20,"reject":100},"utilization":{"delay":0.80,"reject":0.95},'
       || '"decisions":["ACCEPT","ACCEPT_WITH_DELAY","QUEUE","REJECT_TEMPORARILY",'
       || '"REQUIRE_ADMIN_OVERRIDE"]}',
     now(), now());

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'feature_assignment', 'quota_assignment', 'file_security_assessment',
        'prompt_security_assessment', 'evaluation_result_item', 'shadow_execution',
        'canary_assignment', 'recovery_policy', 'retention_policy',
        'data_classification_policy', 'go_live_checklist_item'
    ]
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY tenant_only_%I ON %I USING (organization_id = app_current_organization_id()) WITH CHECK (organization_id = app_current_organization_id())',
            table_name,
            table_name
        );
    END LOOP;

    FOREACH table_name IN ARRAY ARRAY[
        'rate_limit_policy', 'backpressure_policy', 'performance_budget',
        'quality_gate_definition'
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
