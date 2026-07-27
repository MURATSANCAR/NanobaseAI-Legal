ALTER TABLE tender_project RENAME COLUMN tenant_id TO organization_id;
ALTER TABLE tender_project RENAME COLUMN code TO project_code;
ALTER TABLE tender_project RENAME COLUMN contracting_authority TO institution_name;
ALTER TABLE tender_project RENAME COLUMN registration_number TO tender_registration_number;
ALTER TABLE tender_project RENAME COLUMN deadline TO bid_deadline;

ALTER TABLE tender_project
    ADD COLUMN tender_type VARCHAR(80),
    ADD COLUMN business_type VARCHAR(80),
    ADD COLUMN sector VARCHAR(120),
    ADD COLUMN clarification_deadline DATE,
    ADD COLUMN owner_user_id VARCHAR(255);

UPDATE tender_project SET owner_user_id = created_by WHERE owner_user_id IS NULL;
ALTER TABLE tender_project ALTER COLUMN owner_user_id SET NOT NULL;

ALTER TABLE tender_project
    ADD CONSTRAINT ck_tender_clarification_deadline
    CHECK (clarification_deadline IS NULL OR bid_deadline IS NULL
        OR clarification_deadline <= bid_deadline);
