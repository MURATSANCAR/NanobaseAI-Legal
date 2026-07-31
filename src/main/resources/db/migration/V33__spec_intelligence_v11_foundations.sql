-- V33: Spec Intelligence v1.1 foundations
-- Capability profiles, OCR quality, DOCX structure, canonical table cells,
-- evaluation corpus, performance budgets/timings, report visual validation,
-- extraction deployment guardrails (feature-flagged rollout).
-- Does NOT modify V28–V32 or compliance lease/fencing tables.

-- ---------------------------------------------------------------------------
-- Feature flags (default OFF)
-- ---------------------------------------------------------------------------
INSERT INTO feature_definition (
    id, feature_code, name, description, default_state, created_at, updated_at
) VALUES
    ('81000000-0000-0000-0000-000000000020', 'DOCUMENT_CAPABILITY_PROFILE_ENABLED',
     'Document capability profile', 'Profile-driven document processing routing', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000021', 'OCR_QUALITY_GATES_ENABLED',
     'OCR quality gates', 'Page/block OCR quality assessment and routing', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000022', 'OCR_NUMERIC_INTEGRITY_ENABLED',
     'OCR numeric integrity', 'Ambiguous numeric OCR validation', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000023', 'DOCX_STRUCTURE_PIPELINE_ENABLED',
     'DOCX structure pipeline', 'Preserve DOCX hierarchy/lists/tables', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000024', 'CANONICAL_TABLE_CELLS_ENABLED',
     'Canonical table cells', 'Normalize PDF/DOCX tables to cell model', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000025', 'TABLE_REQUIREMENT_EXTRACTION_ENABLED',
     'Table requirement extraction', 'Header-context aware table requirements', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000026', 'KNOWLEDGE_CORPUS_V11_ENABLED',
     'Knowledge corpus v1.1', 'Certificate/datasheet knowledge extraction gates', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000027', 'REPORT_VISUAL_VALIDATION_ENABLED',
     'Report visual validation', 'Structural visual rules before COMPLETED', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000028', 'OBJECT_DELIVERY_STRATEGY_ENABLED',
     'Object delivery strategy', 'DIRECT_PUBLIC / REVERSE_PROXY / BACKEND_PROXY_ONLY', FALSE, now(), now()),
    ('81000000-0000-0000-0000-000000000029', 'REQUIREMENT_EXTRACTION_TIMING_ENABLED',
     'Requirement extraction timing', 'Stage-level extraction telemetry', FALSE, now(), now()),
    ('81000000-0000-0000-0000-00000000002a', 'EXTRACTION_DEPLOYMENT_GUARDRAILS_ENABLED',
     'Extraction deployment guardrails', 'Pool vs concurrency startup checks', FALSE, now(), now()),
    ('81000000-0000-0000-0000-00000000002b', 'BROAD_DOCUMENT_CORPUS_ENABLED',
     'Broad document corpus', 'Multi-format corpus evaluation harness', FALSE, now(), now())
ON CONFLICT (feature_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- document_capability_profile
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS document_capability_profile (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    format_concept_code VARCHAR(80) NOT NULL,
    content_mode_concept_code VARCHAR(80) NOT NULL,
    layout_complexity_concept_code VARCHAR(80) NOT NULL,
    ocr_need_concept_code VARCHAR(80) NOT NULL,
    table_density NUMERIC(8, 4) NOT NULL DEFAULT 0,
    image_density NUMERIC(8, 4) NOT NULL DEFAULT 0,
    text_density NUMERIC(8, 4) NOT NULL DEFAULT 0,
    heading_confidence NUMERIC(8, 4) NOT NULL DEFAULT 0,
    language_profile_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    page_count INTEGER NOT NULL DEFAULT 0,
    estimated_token_count INTEGER NOT NULL DEFAULT 0,
    recommended_parser_profile_code VARCHAR(80),
    recommended_ocr_profile_code VARCHAR(80),
    recommended_segmentation_profile_code VARCHAR(80),
    recommended_extraction_profile_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_capability_profile_version UNIQUE (document_version_id)
);

CREATE INDEX IF NOT EXISTS ix_document_capability_profile_org
    ON document_capability_profile (organization_id, created_at DESC);

ALTER TABLE document_capability_profile ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_document_capability_profile ON document_capability_profile;
CREATE POLICY tenant_isolation_document_capability_profile ON document_capability_profile
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

-- ---------------------------------------------------------------------------
-- ocr_quality_assessment
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ocr_quality_assessment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    page_id UUID REFERENCES document_page(id),
    block_id UUID,
    character_confidence NUMERIC(8, 4),
    word_confidence NUMERIC(8, 4),
    layout_confidence NUMERIC(8, 4),
    language_confidence NUMERIC(8, 4),
    numeric_confidence NUMERIC(8, 4),
    quality_status_concept_code VARCHAR(80) NOT NULL,
    issues_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_ocr_quality_assessment_version
    ON ocr_quality_assessment (document_version_id, created_at DESC);

ALTER TABLE ocr_quality_assessment ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_ocr_quality_assessment ON ocr_quality_assessment;
CREATE POLICY tenant_isolation_ocr_quality_assessment ON ocr_quality_assessment
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

-- ---------------------------------------------------------------------------
-- docx_structure_block
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS docx_structure_block (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    document_version_id UUID NOT NULL REFERENCES document_version(id),
    block_type_concept_code VARCHAR(80) NOT NULL,
    style_name VARCHAR(255),
    outline_level INTEGER,
    numbering_id VARCHAR(120),
    list_level INTEGER,
    text_content TEXT NOT NULL DEFAULT '',
    table_reference UUID,
    parent_block_id UUID,
    order_index INTEGER NOT NULL DEFAULT 0,
    source_xml_path VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_docx_structure_block_version
    ON docx_structure_block (document_version_id, order_index);

ALTER TABLE docx_structure_block ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_docx_structure_block ON docx_structure_block;
CREATE POLICY tenant_isolation_docx_structure_block ON docx_structure_block
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

-- ---------------------------------------------------------------------------
-- Extend document_table + document_table_cell (canonical)
-- ---------------------------------------------------------------------------
ALTER TABLE document_table
    ADD COLUMN IF NOT EXISTS table_index INTEGER,
    ADD COLUMN IF NOT EXISTS title VARCHAR(500),
    ADD COLUMN IF NOT EXISTS header_rows_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS source_provider VARCHAR(80),
    ADD COLUMN IF NOT EXISTS confidence NUMERIC(8, 4);

CREATE TABLE IF NOT EXISTS document_table_cell (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    table_id UUID NOT NULL REFERENCES document_table(id) ON DELETE CASCADE,
    row_index INTEGER NOT NULL,
    column_index INTEGER NOT NULL,
    row_span INTEGER NOT NULL DEFAULT 1,
    column_span INTEGER NOT NULL DEFAULT 1,
    text_content TEXT NOT NULL DEFAULT '',
    normalized_text TEXT NOT NULL DEFAULT '',
    header_context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    bounding_box_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_table_cell_span CHECK (row_span >= 1 AND column_span >= 1)
);

CREATE INDEX IF NOT EXISTS ix_document_table_cell_table
    ON document_table_cell (table_id, row_index, column_index);

ALTER TABLE document_table_cell ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_document_table_cell ON document_table_cell;
CREATE POLICY tenant_isolation_document_table_cell ON document_table_cell
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

-- ---------------------------------------------------------------------------
-- Evaluation corpus
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evaluation_corpus (
    id UUID PRIMARY KEY,
    corpus_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version VARCHAR(40) NOT NULL,
    status_concept_code VARCHAR(80) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS corpus_document (
    id UUID PRIMARY KEY,
    corpus_id UUID NOT NULL REFERENCES evaluation_corpus(id) ON DELETE CASCADE,
    fixture_code VARCHAR(120) NOT NULL,
    document_type_concept_code VARCHAR(80) NOT NULL,
    format_concept_code VARCHAR(80) NOT NULL,
    source_reference VARCHAR(1000),
    content_hash VARCHAR(64),
    expected_behavior_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    license_status_concept_code VARCHAR(80) NOT NULL DEFAULT 'INTERNAL_TEST',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_corpus_document_fixture UNIQUE (corpus_id, fixture_code)
);

CREATE TABLE IF NOT EXISTS corpus_annotation (
    id UUID PRIMARY KEY,
    corpus_document_id UUID NOT NULL REFERENCES corpus_document(id) ON DELETE CASCADE,
    annotation_type_concept_code VARCHAR(80) NOT NULL,
    annotation_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_status_concept_code VARCHAR(80) NOT NULL DEFAULT 'DRAFT',
    reviewed_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_corpus_document_corpus
    ON corpus_document (corpus_id, fixture_code);
CREATE INDEX IF NOT EXISTS ix_corpus_annotation_document
    ON corpus_annotation (corpus_document_id);

-- Seed v1.1 corpus metadata (fixtures themselves may be PENDING)
INSERT INTO evaluation_corpus (id, corpus_code, name, description, version, status_concept_code)
VALUES (
    '82000000-0000-0000-0000-000000000001',
    'NANOBASE_SPEC_V11',
    'Nanobase Spec Intelligence v1.1 corpus',
    'Native/scanned PDF, DOCX, table-heavy, knowledge evidence slices',
    '1.1.0',
    'DRAFT'
) ON CONFLICT (corpus_code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Performance budget + requirement extraction timing
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS performance_budget_profile (
    id UUID PRIMARY KEY,
    profile_code VARCHAR(80) NOT NULL UNIQUE,
    document_slice VARCHAR(80) NOT NULL,
    page_count_min INTEGER NOT NULL DEFAULT 1,
    page_count_max INTEGER NOT NULL DEFAULT 9999,
    target_p50_ms BIGINT NOT NULL,
    target_p95_ms BIGINT NOT NULL,
    max_timeout_rate NUMERIC(8, 4) NOT NULL DEFAULT 0.05,
    max_queue_wait_ms BIGINT NOT NULL DEFAULT 60000,
    quality_gate_version_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO performance_budget_profile (
    id, profile_code, document_slice, page_count_min, page_count_max,
    target_p50_ms, target_p95_ms, max_timeout_rate, max_queue_wait_ms, quality_gate_version_code
) VALUES
    ('83000000-0000-0000-0000-000000000001', 'NATIVE_PDF_25P', 'native_pdf', 1, 50,
     900000, 2400000, 0.05, 120000, 'gates-v1.1'),
    ('83000000-0000-0000-0000-000000000002', 'SCANNED_PDF_25P', 'scanned_pdf', 1, 50,
     1200000, 3600000, 0.08, 180000, 'gates-v1.1'),
    ('83000000-0000-0000-0000-000000000003', 'DOCX_25P', 'docx', 1, 50,
     600000, 1800000, 0.05, 120000, 'gates-v1.1'),
    ('83000000-0000-0000-0000-000000000004', 'TABLE_HEAVY_25P', 'table_heavy', 1, 50,
     1200000, 3600000, 0.08, 180000, 'gates-v1.1'),
    ('83000000-0000-0000-0000-000000000005', 'LONG_DOC_100P', 'long_document', 51, 150,
     2400000, 7200000, 0.10, 300000, 'gates-v1.1'),
    ('83000000-0000-0000-0000-000000000006', 'LONG_DOC_250P', 'long_document', 151, 300,
     4800000, 14400000, 0.12, 600000, 'gates-v1.1')
ON CONFLICT (profile_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS requirement_extraction_timing (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    job_id UUID NOT NULL,
    task_id UUID,
    clause_id UUID,
    chunk_id UUID,
    stage_concept_code VARCHAR(80) NOT NULL,
    duration_ms BIGINT NOT NULL,
    queue_wait_ms BIGINT,
    capacity_wait_ms BIGINT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    model_profile VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_requirement_extraction_timing_job
    ON requirement_extraction_timing (job_id, created_at);

ALTER TABLE requirement_extraction_timing ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_requirement_extraction_timing ON requirement_extraction_timing;
CREATE POLICY tenant_isolation_requirement_extraction_timing ON requirement_extraction_timing
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());

-- ---------------------------------------------------------------------------
-- Report visual validation
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_visual_validation_result (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    report_generation_job_id UUID NOT NULL,
    report_artifact_id UUID,
    status_code VARCHAR(80) NOT NULL,
    page_count INTEGER,
    overflow_page_count INTEGER NOT NULL DEFAULT 0,
    cut_text_count INTEGER NOT NULL DEFAULT 0,
    missing_section_count INTEGER NOT NULL DEFAULT 0,
    turkish_glyph_issues INTEGER NOT NULL DEFAULT 0,
    details_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_report_visual_validation_job
    ON report_visual_validation_result (report_generation_job_id);

ALTER TABLE report_visual_validation_result ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation_report_visual_validation ON report_visual_validation_result;
CREATE POLICY tenant_isolation_report_visual_validation ON report_visual_validation_result
    USING (organization_id = app_current_organization_id())
    WITH CHECK (organization_id = app_current_organization_id());
