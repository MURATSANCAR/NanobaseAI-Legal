CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organization(id),
    external_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, external_subject),
    UNIQUE (tenant_id, email)
);

CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_role CHECK (role IN (
        'SYSTEM_ADMIN', 'TENANT_ADMIN', 'TENDER_MANAGER', 'TECHNICAL_REVIEWER',
        'LEGAL_REVIEWER', 'PROCUREMENT_REVIEWER', 'REPORT_VIEWER', 'EXTERNAL_REVIEWER'
    ))
);

CREATE TABLE document (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organization(id),
    tender_project_id UUID NOT NULL REFERENCES tender_project(id),
    name VARCHAR(255) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_document_type CHECK (document_type IN (
        'TECHNICAL_SPECIFICATION', 'ADMINISTRATIVE_SPECIFICATION', 'CONTRACT', 'ADDENDUM',
        'TECHNICAL_CATALOG', 'CERTIFICATE', 'PRICE_SCHEDULE', 'OTHER'
    ))
);
CREATE INDEX ix_document_project ON document (tenant_id, tender_project_id, created_at DESC);

CREATE TABLE document_version (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organization(id),
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_id, version_number),
    UNIQUE (tenant_id, object_key)
);

CREATE TABLE external_document_mapping (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    provider VARCHAR(50) NOT NULL,
    external_corpus_id VARCHAR(255),
    external_document_id VARCHAR(255) NOT NULL,
    external_version VARCHAR(100),
    sync_status VARCHAR(50) NOT NULL,
    last_synced_at TIMESTAMPTZ,
    UNIQUE (provider, external_document_id)
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES organization(id),
    routing_key VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;
