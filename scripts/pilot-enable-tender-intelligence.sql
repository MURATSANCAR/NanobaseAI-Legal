-- Pilot organization tender-intelligence enablement (runbook section F).
-- Prerequisites:
--   1. V19–V26 migrated
--   2. Matching env kill switch set to true for the stage you are opening
--   3. Replace :pilot_org_id below
--
-- Example:
--   \set pilot_org_id '11111111-1111-1111-1111-111111111111'

-- Stage 1
INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, TRUE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code = 'TENDER_DOMAIN_V2_ENABLED'
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = TRUE, updated_at = now();

-- Stage 2
INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, TRUE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code = 'REQUIREMENT_CLASSIFICATION_ENABLED'
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = TRUE, updated_at = now();

-- Stage 3
INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, TRUE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code = 'COMPANY_CAPABILITY_REGISTRY_ENABLED'
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = TRUE, updated_at = now();

-- Stage 4
INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, TRUE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code = 'DETERMINISTIC_EVALUATION_ENABLED'
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = TRUE, updated_at = now();

-- Stage 5
INSERT INTO feature_assignment (
    id, organization_id, project_id, feature_definition_id, enabled,
    configuration_json, created_at, updated_at
)
SELECT gen_random_uuid(), :'pilot_org_id'::uuid, NULL, definition.id, TRUE,
       '{}'::jsonb, now(), now()
  FROM feature_definition definition
 WHERE definition.feature_code = 'GAP_ANALYSIS_ENABLED'
ON CONFLICT (organization_id, project_id, feature_definition_id)
DO UPDATE SET enabled = TRUE, updated_at = now();

-- Emergency kill: disable all pilot intelligence assignments without DROP.
-- UPDATE feature_assignment
--    SET enabled = FALSE, updated_at = now()
--  WHERE organization_id = :'pilot_org_id'::uuid
--    AND feature_definition_id IN (
--      SELECT id FROM feature_definition
--       WHERE feature_code IN (
--         'TENDER_DOMAIN_V2_ENABLED',
--         'REQUIREMENT_CLASSIFICATION_ENABLED',
--         'COMPANY_CAPABILITY_REGISTRY_ENABLED',
--         'DETERMINISTIC_EVALUATION_ENABLED',
--         'GAP_ANALYSIS_ENABLED'
--       )
--    );
