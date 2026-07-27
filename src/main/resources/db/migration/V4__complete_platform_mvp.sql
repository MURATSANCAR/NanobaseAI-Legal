ALTER TABLE tender_project RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE tender_project RENAME COLUMN code TO project_code;
ALTER TABLE tender_project RENAME COLUMN contracting_authority TO institution_name;
ALTER TABLE tender_project RENAME COLUMN registration_number TO tender_registration_number;
ALTER TABLE tender_project RENAME COLUMN deadline TO bid_deadline;

ALTER TABLE tender_project DROP CONSTRAINT tender_project_code_key;
ALTER TABLE tender_project
    ADD COLUMN tender_type VARCHAR(80),
    ADD COLUMN business_type VARCHAR(80),
    ADD COLUMN sector VARCHAR(120),
    ADD COLUMN clarification_deadline DATE,
    ADD COLUMN owner_user_id VARCHAR(255);
UPDATE tender_project SET owner_user_id = created_by WHERE owner_user_id IS NULL;
ALTER TABLE tender_project ALTER COLUMN owner_user_id SET NOT NULL;
ALTER TABLE tender_project
    ADD CONSTRAINT uq_tender_project_org_code UNIQUE (organization_id, project_code);
ALTER TABLE tender_project
    ADD CONSTRAINT ck_tender_project_dates
        CHECK (clarification_deadline IS NULL OR bid_deadline IS NULL
               OR clarification_deadline <= bid_deadline);

CREATE SEQUENCE tender_project_code_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE project_member (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    project_id UUID NOT NULL REFERENCES tender_project(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    project_role VARCHAR(40) NOT NULL,
    can_view_documents BOOLEAN NOT NULL DEFAULT TRUE,
    can_upload_documents BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_members BOOLEAN NOT NULL DEFAULT FALSE,
    can_archive_project BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_project_member_project_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_project_member_role CHECK (project_role IN ('OWNER', 'MANAGER', 'REVIEWER', 'VIEWER'))
);

INSERT INTO project_member (
    id, organization_id, project_id, user_id, project_role,
    can_view_documents, can_upload_documents, can_manage_members,
    can_archive_project, created_at
)
SELECT gen_random_uuid(), organization_id, id, owner_user_id, 'OWNER',
       TRUE, TRUE, TRUE, TRUE, created_at
FROM tender_project
ON CONFLICT (project_id, user_id) DO NOTHING;

ALTER TABLE document RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE document RENAME COLUMN tender_project_id TO project_id;
ALTER TABLE document RENAME COLUMN name TO logical_name;
ALTER TABLE document RENAME COLUMN current_version TO current_version_number;
ALTER TABLE document
    ADD COLUMN current_version_id UUID,
    ADD COLUMN included_in_analysis BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'migration';

ALTER TABLE document_version RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE document_version RENAME COLUMN object_key TO object_storage_key;
ALTER TABLE document_version RENAME COLUMN original_filename TO original_file_name;
ALTER TABLE document_version RENAME COLUMN media_type TO mime_type;
ALTER TABLE document_version RENAME COLUMN size_bytes TO file_size;
ALTER TABLE document_version RENAME COLUMN created_at TO uploaded_at;
ALTER TABLE document_version
    ADD COLUMN page_count INTEGER,
    ADD COLUMN language VARCHAR(20),
    ADD COLUMN ocr_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ocr_quality_score NUMERIC(5,2),
    ADD COLUMN processing_status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    ADD COLUMN uploaded_by VARCHAR(255) NOT NULL DEFAULT 'migration',
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN processing_completed_at TIMESTAMPTZ,
    ADD COLUMN error_code VARCHAR(100),
    ADD COLUMN error_message VARCHAR(2000),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE document_version version_row
SET processing_status = document.status,
    uploaded_by = document.created_by
FROM document
WHERE document.id = version_row.document_id;

UPDATE document
SET current_version_id = version_row.id
FROM document_version version_row
WHERE version_row.document_id = document.id
  AND version_row.version_number = document.current_version_number;

ALTER TABLE document
    ADD CONSTRAINT fk_document_current_version
        FOREIGN KEY (current_version_id) REFERENCES document_version(id)
        DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE document DROP CONSTRAINT ck_document_type;
UPDATE document SET document_type = 'DRAFT_CONTRACT' WHERE document_type = 'CONTRACT';
UPDATE document SET document_type = 'PRODUCT_CATALOG' WHERE document_type = 'TECHNICAL_CATALOG';
ALTER TABLE document
    ADD CONSTRAINT ck_document_type CHECK (document_type IN (
        'TECHNICAL_SPECIFICATION', 'ADMINISTRATIVE_SPECIFICATION', 'DRAFT_CONTRACT',
        'ADDENDUM', 'PRICE_SCHEDULE', 'PRODUCT_CATALOG', 'CERTIFICATE',
        'TECHNICAL_DRAWING', 'OTHER'
    ));

UPDATE document SET status = 'FAILED'
WHERE status IN ('PARSING_FAILED', 'OCR_FAILED', 'UNSUPPORTED_FORMAT', 'PASSWORD_PROTECTED');
ALTER TABLE document
    ADD CONSTRAINT ck_document_status CHECK (status IN (
        'UPLOADED', 'VIRUS_SCANNING', 'CLASSIFYING', 'PARSING', 'OCR_PROCESSING',
        'STRUCTURE_DETECTION', 'INDEXING', 'READY', 'FAILED', 'MANUAL_REVIEW_REQUIRED'
    ));
ALTER TABLE document_version
    ADD CONSTRAINT ck_document_version_status CHECK (processing_status IN (
        'UPLOADED', 'VIRUS_SCANNING', 'CLASSIFYING', 'PARSING', 'OCR_PROCESSING',
        'STRUCTURE_DETECTION', 'INDEXING', 'READY', 'FAILED', 'MANUAL_REVIEW_REQUIRED'
    )),
    ADD CONSTRAINT ck_document_version_file_size CHECK (file_size > 0),
    ADD CONSTRAINT ck_document_version_number CHECK (version_number > 0),
    ADD CONSTRAINT ck_document_version_page_count CHECK (page_count IS NULL OR page_count >= 0),
    ADD CONSTRAINT ck_document_version_ocr_score
        CHECK (ocr_quality_score IS NULL OR (ocr_quality_score >= 0 AND ocr_quality_score <= 100));

ALTER TABLE external_document_mapping RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE external_document_mapping
    ALTER COLUMN external_document_id DROP NOT NULL,
    ADD COLUMN error_code VARCHAR(100),
    ADD COLUMN error_message VARCHAR(2000),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE external_document_mapping
    ADD CONSTRAINT ck_external_provider CHECK (provider IN ('OPENCONTRACTS', 'DOCLING', 'MINERU', 'CUSTOM')),
    ADD CONSTRAINT ck_external_sync_status CHECK (sync_status IN ('PENDING', 'SYNCING', 'SYNCED', 'FAILED'));

ALTER TABLE outbox_event RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE outbox_event RENAME COLUMN payload TO payload_json;
ALTER TABLE outbox_event RENAME COLUMN attempt_count TO retry_count;
ALTER TABLE outbox_event
    ADD COLUMN aggregate_type VARCHAR(100) NOT NULL DEFAULT 'Document',
    ADD COLUMN aggregate_id UUID,
    ADD COLUMN event_type VARCHAR(100) NOT NULL DEFAULT 'DocumentUploaded',
    ADD COLUMN event_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN correlation_id UUID,
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN last_error VARCHAR(2000);
UPDATE outbox_event
SET aggregate_id = id,
    correlation_id = id,
    status = CASE WHEN published_at IS NULL THEN 'FAILED' ELSE 'PUBLISHED' END,
    last_error = CASE WHEN published_at IS NULL
        THEN 'Legacy event envelope requires manual replay'
        ELSE NULL END;
ALTER TABLE outbox_event
    ALTER COLUMN aggregate_id SET NOT NULL,
    ALTER COLUMN correlation_id SET NOT NULL;
ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'));

ALTER TABLE audit_event RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE audit_event RENAME COLUMN actor_id TO user_id;
ALTER TABLE audit_event RENAME COLUMN aggregate_type TO entity_type;
ALTER TABLE audit_event RENAME COLUMN aggregate_id TO entity_id;
ALTER TABLE audit_event RENAME COLUMN occurred_at TO created_at;
ALTER TABLE audit_event RENAME COLUMN payload TO after_json;
ALTER TABLE audit_event
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN user_agent VARCHAR(500),
    ADD COLUMN before_json JSONB,
    ADD COLUMN correlation_id UUID;
UPDATE audit_event SET correlation_id = id WHERE correlation_id IS NULL;
ALTER TABLE audit_event ALTER COLUMN correlation_id SET NOT NULL;

ALTER TABLE clause RENAME COLUMN tenant_id TO organization_id;

CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

DROP INDEX IF EXISTS ix_tender_project_tenant_created;
DROP INDEX IF EXISTS ix_document_project;
DROP INDEX IF EXISTS ix_outbox_pending;
DROP INDEX IF EXISTS ix_audit_tenant_time;
DROP INDEX IF EXISTS ix_audit_aggregate;
DROP INDEX IF EXISTS ix_clause_document_order;

CREATE INDEX ix_tender_project_org_status ON tender_project (organization_id, status);
CREATE INDEX ix_tender_project_org_bid_deadline ON tender_project (organization_id, bid_deadline);
CREATE INDEX ix_tender_project_org_code ON tender_project (organization_id, project_code);
CREATE INDEX ix_project_member_project_user ON project_member (project_id, user_id);
CREATE INDEX ix_project_member_org_user ON project_member (organization_id, user_id);
CREATE INDEX ix_document_project_status ON document (project_id, status);
CREATE INDEX ix_document_org_project ON document (organization_id, project_id);
CREATE INDEX ix_document_version_document_number ON document_version (document_id, version_number);
CREATE INDEX ix_document_version_org_status ON document_version (organization_id, processing_status);
CREATE INDEX ix_document_version_sha256 ON document_version (sha256);
CREATE INDEX ix_external_mapping_version_provider
    ON external_document_mapping (document_version_id, provider);
CREATE INDEX ix_outbox_status_next_attempt ON outbox_event (status, next_attempt_at);
CREATE INDEX ix_audit_org_created ON audit_event (organization_id, created_at DESC);
CREATE INDEX ix_audit_entity ON audit_event (entity_type, entity_id);
