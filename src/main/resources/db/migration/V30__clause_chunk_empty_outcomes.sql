-- V30: clause chunks + requirement empty-outcome telemetry
CREATE TABLE IF NOT EXISTS clause_chunk (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    clause_id UUID NOT NULL REFERENCES clause(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    text_content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    context_header_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_block_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_clause_chunk UNIQUE (clause_id, chunk_index),
    CONSTRAINT ck_clause_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_clause_chunk_tokens CHECK (token_count >= 0)
);

CREATE INDEX IF NOT EXISTS ix_clause_chunk_clause ON clause_chunk (clause_id);

ALTER TABLE clause_chunk ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_clause_chunk ON clause_chunk;
CREATE POLICY tenant_isolation_clause_chunk ON clause_chunk
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

ALTER TABLE requirement_extraction_job
    ADD COLUMN IF NOT EXISTS empty_outcome_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS suspicious_empty_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS timeout_empty_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS schema_failure_count INTEGER NOT NULL DEFAULT 0;
