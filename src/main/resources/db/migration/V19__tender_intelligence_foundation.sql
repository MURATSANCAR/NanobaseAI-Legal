-- Phase 1: Tender workspace enrichment, document hierarchy metadata, feature flags.
-- Backward compatible: legacy tender statuses and document types are retained.

-- ---------------------------------------------------------------------------
-- Feature flags (default OFF — safe rollout)
-- ---------------------------------------------------------------------------
INSERT INTO feature_definition (
    id, feature_code, name, description, default_state, created_at, updated_at
) VALUES
    ('81000000-0000-0000-0000-000000000010', 'TENDER_DOMAIN_V2_ENABLED',
     'Tender domain v2', 'Extended tender workspace fields and statuses', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000011', 'REQUIREMENT_CLASSIFICATION_ENABLED',
     'Requirement classification', 'Obligation, lifecycle and criticality classification', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000012', 'COMPANY_CAPABILITY_REGISTRY_ENABLED',
     'Company capability registry', 'Structured capability registry and verification', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000013', 'DETERMINISTIC_EVALUATION_ENABLED',
     'Deterministic evaluation', 'Numeric/date/capability deterministic evaluators', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000014', 'GAP_ANALYSIS_ENABLED',
     'Gap analysis', 'Compliance gap and remediation planning', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000015', 'CLARIFICATION_MANAGEMENT_ENABLED',
     'Clarification management', 'Institution clarification question workflow', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000016', 'RISK_ENGINE_ENABLED',
     'Tender risk engine', 'Structured tender risk scoring and contractual risk', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000017', 'BID_DECISION_ENABLED',
     'Bid decision engine', 'Bid / conditional bid / no-bid recommendations', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000018', 'OBLIGATION_MANAGEMENT_ENABLED',
     'Obligation management', 'Post-award contract obligation tracking', FALSE, now(), now())
ON CONFLICT (feature_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Tender workspace enrichment
-- ---------------------------------------------------------------------------
ALTER TABLE tender_project
    ADD COLUMN IF NOT EXISTS country VARCHAR(2),
    ADD COLUMN IF NOT EXISTS estimated_value NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS publication_date DATE,
    ADD COLUMN IF NOT EXISTS award_date DATE,
    ADD COLUMN IF NOT EXISTS contract_start_date DATE,
    ADD COLUMN IF NOT EXISTS contract_end_date DATE,
    ADD COLUMN IF NOT EXISTS business_owner_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS legal_owner_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS technical_owner_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS financial_owner_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

ALTER TABLE tender_project DROP CONSTRAINT IF EXISTS ck_tender_status;
ALTER TABLE tender_project
    ADD CONSTRAINT ck_tender_status CHECK (status IN (
        -- Legacy (kept for backward compatibility)
        'DRAFT', 'DOCUMENTS_PENDING', 'ANALYSIS_IN_PROGRESS', 'REVIEW_IN_PROGRESS',
        'COMPLETED', 'ARCHIVED',
        -- Tender intelligence v2
        'ANALYSIS_PENDING', 'ANALYZING', 'REVIEW_REQUIRED', 'READY_FOR_DECISION',
        'BID_APPROVED', 'CONDITIONAL_BID', 'NO_BID', 'SUBMITTED',
        'AWARDED', 'NOT_AWARDED', 'CONTRACT_ACTIVE', 'CLOSED', 'CANCELLED'
    ));

-- ---------------------------------------------------------------------------
-- Document hierarchy / authority metadata
-- ---------------------------------------------------------------------------
ALTER TABLE document
    ADD COLUMN IF NOT EXISTS effective_date DATE,
    ADD COLUMN IF NOT EXISTS publication_date DATE,
    ADD COLUMN IF NOT EXISTS is_authoritative BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS supersedes_document_id UUID,
    ADD COLUMN IF NOT EXISTS superseded_by_document_id UUID,
    ADD COLUMN IF NOT EXISTS document_priority INTEGER NOT NULL DEFAULT 100,
    ADD COLUMN IF NOT EXISTS source_institution VARCHAR(200),
    ADD COLUMN IF NOT EXISTS analysis_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS content_checksum VARCHAR(64);

ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_document_type;
ALTER TABLE document
    ADD CONSTRAINT ck_document_type CHECK (document_type IN (
        -- Legacy
        'TECHNICAL_SPECIFICATION', 'ADMINISTRATIVE_SPECIFICATION', 'DRAFT_CONTRACT',
        'ADDENDUM', 'PRICE_SCHEDULE', 'PRODUCT_CATALOG', 'CERTIFICATE',
        'TECHNICAL_DRAWING', 'OTHER',
        -- Tender intelligence v2
        'AMENDMENT', 'ANNEX', 'TECHNICAL_FORM', 'FINANCIAL_FORM',
        'OFFICIAL_CLARIFICATION', 'QUESTION_RESPONSE', 'AWARD_NOTICE'
    ));

ALTER TABLE document DROP CONSTRAINT IF EXISTS ck_document_analysis_status;
ALTER TABLE document
    ADD CONSTRAINT ck_document_analysis_status CHECK (analysis_status IN (
        'NOT_STARTED', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'STALE'
    ));

ALTER TABLE document DROP CONSTRAINT IF EXISTS fk_document_supersedes;
ALTER TABLE document
    ADD CONSTRAINT fk_document_supersedes
        FOREIGN KEY (supersedes_document_id, organization_id)
        REFERENCES document(id, organization_id);

ALTER TABLE document DROP CONSTRAINT IF EXISTS fk_document_superseded_by;
ALTER TABLE document
    ADD CONSTRAINT fk_document_superseded_by
        FOREIGN KEY (superseded_by_document_id, organization_id)
        REFERENCES document(id, organization_id);

CREATE INDEX IF NOT EXISTS ix_document_project_type
    ON document (organization_id, project_id, document_type);
CREATE INDEX IF NOT EXISTS ix_document_supersedes
    ON document (organization_id, supersedes_document_id)
    WHERE supersedes_document_id IS NOT NULL;
