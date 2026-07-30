-- V29: layout blocks + recurring page elements for provider-neutral clause segmentation
CREATE TABLE IF NOT EXISTS document_layout_block (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    page_id UUID REFERENCES document_page(id) ON DELETE SET NULL,
    block_index INTEGER NOT NULL,
    block_type_code VARCHAR(80) NOT NULL,
    text_content TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    bounding_box_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    font_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    list_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    table_metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    reading_order INTEGER NOT NULL DEFAULT 0,
    source_provider VARCHAR(80) NOT NULL,
    provider_version VARCHAR(80),
    confidence NUMERIC(10,6) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_layout_block_version_index UNIQUE (document_version_id, block_index),
    CONSTRAINT ck_layout_block_index CHECK (block_index >= 0)
);

CREATE INDEX IF NOT EXISTS ix_layout_block_version_order
    ON document_layout_block (document_version_id, reading_order);
CREATE INDEX IF NOT EXISTS ix_layout_block_org_version
    ON document_layout_block (organization_id, document_version_id);

ALTER TABLE document_layout_block ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_document_layout_block ON document_layout_block;
CREATE POLICY tenant_isolation_document_layout_block ON document_layout_block
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

CREATE TABLE IF NOT EXISTS recurring_page_element (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
    normalized_signature VARCHAR(512) NOT NULL,
    element_type_code VARCHAR(80) NOT NULL,
    page_occurrence_count INTEGER NOT NULL DEFAULT 0,
    page_ratio NUMERIC(10,6) NOT NULL DEFAULT 0,
    suppression_status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    confidence NUMERIC(10,6) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recurring_page_element UNIQUE (document_version_id, normalized_signature),
    CONSTRAINT ck_recurring_page_ratio CHECK (page_ratio >= 0 AND page_ratio <= 1)
);

CREATE INDEX IF NOT EXISTS ix_recurring_page_element_version
    ON recurring_page_element (document_version_id);

ALTER TABLE recurring_page_element ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_recurring_page_element ON recurring_page_element;
CREATE POLICY tenant_isolation_recurring_page_element ON recurring_page_element
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

ALTER TABLE clause
    ADD COLUMN IF NOT EXISTS segmentation_provider VARCHAR(80),
    ADD COLUMN IF NOT EXISTS segmentation_profile_version VARCHAR(80),
    ADD COLUMN IF NOT EXISTS structure_confidence NUMERIC(10,6),
    ADD COLUMN IF NOT EXISTS source_block_ids_json JSONB NOT NULL DEFAULT '[]'::jsonb;
