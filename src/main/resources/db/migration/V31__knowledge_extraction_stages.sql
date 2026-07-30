-- V31: knowledge extraction stages + document purpose codes
CREATE TABLE IF NOT EXISTS knowledge_extraction_run_stage (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    knowledge_job_id UUID NOT NULL,
    stage_code VARCHAR(80) NOT NULL,
    status_code VARCHAR(80) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_code VARCHAR(120),
    sanitized_error_detail VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_knowledge_stage_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX IF NOT EXISTS ix_knowledge_stage_job
    ON knowledge_extraction_run_stage (knowledge_job_id, created_at);

ALTER TABLE knowledge_extraction_run_stage ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_knowledge_stage ON knowledge_extraction_run_stage;
CREATE POLICY tenant_isolation_knowledge_stage ON knowledge_extraction_run_stage
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

ALTER TABLE knowledge_extraction_job
    ADD COLUMN IF NOT EXISTS document_purpose_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS current_stage_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS existing_knowledge_used BOOLEAN NOT NULL DEFAULT FALSE;
