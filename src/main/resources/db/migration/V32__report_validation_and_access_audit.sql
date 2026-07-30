-- V32: report validation + document access URL audit
CREATE TABLE IF NOT EXISTS report_validation_result (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    report_generation_job_id UUID NOT NULL,
    report_artifact_id UUID,
    status_code VARCHAR(80) NOT NULL,
    error_code VARCHAR(120),
    page_count INTEGER,
    file_size BIGINT,
    sha256 VARCHAR(64),
    required_sections_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_sections_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_report_validation_job
    ON report_validation_result (report_generation_job_id);

ALTER TABLE report_validation_result ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_report_validation ON report_validation_result;
CREATE POLICY tenant_isolation_report_validation ON report_validation_result
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

CREATE TABLE IF NOT EXISTS document_access_url_audit (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    subject_type VARCHAR(40) NOT NULL,
    subject_id UUID NOT NULL,
    access_mode VARCHAR(40) NOT NULL,
    public_host VARCHAR(255),
    expires_at TIMESTAMPTZ,
    requested_by VARCHAR(255),
    correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_document_access_url_audit_subject
    ON document_access_url_audit (subject_type, subject_id, created_at DESC);

ALTER TABLE document_access_url_audit ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_document_access_url_audit ON document_access_url_audit;
CREATE POLICY tenant_isolation_document_access_url_audit ON document_access_url_audit
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());
