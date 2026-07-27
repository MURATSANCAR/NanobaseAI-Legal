CREATE TABLE organization (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tender_project (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    contracting_authority VARCHAR(200) NOT NULL,
    registration_number VARCHAR(100),
    deadline DATE,
    currency VARCHAR(3),
    priority VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    description VARCHAR(4000),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tender_tenant FOREIGN KEY (tenant_id) REFERENCES organization(id),
    CONSTRAINT ck_tender_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_tender_status CHECK (status IN (
        'DRAFT', 'DOCUMENTS_PENDING', 'ANALYSIS_IN_PROGRESS',
        'REVIEW_IN_PROGRESS', 'COMPLETED', 'ARCHIVED'
    ))
);
CREATE INDEX ix_tender_project_tenant_created ON tender_project (tenant_id, created_at DESC);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES organization(id)
);
CREATE INDEX ix_audit_tenant_time ON audit_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_aggregate ON audit_event (tenant_id, aggregate_type, aggregate_id);

CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_update
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
