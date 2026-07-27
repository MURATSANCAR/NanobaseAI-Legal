CREATE TABLE clause (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES clause(id) ON DELETE CASCADE,
    clause_number VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_text TEXT NOT NULL,
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    sort_order INTEGER NOT NULL CHECK (sort_order >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_version_id, clause_number)
);
CREATE INDEX ix_clause_document_order
    ON clause (tenant_id, document_version_id, sort_order);
