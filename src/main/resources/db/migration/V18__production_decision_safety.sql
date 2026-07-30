-- Production V1 decision-safety schema fields and stronger closed-world semantics.
-- FAST/SHADOW remain in code behind feature flags; this migration does not remove them.

UPDATE output_schema_version
SET json_schema = jsonb_set(
        jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        json_schema,
                        '{properties,explicitContradiction}',
                        '{"type":"boolean"}'::jsonb,
                        true
                    ),
                    '{properties,closedWorldApplied}',
                    '{"type":"boolean"}'::jsonb,
                    true
                ),
                '{properties,missingRequirementElements}',
                '{"type":"array","items":{"type":"string"}}'::jsonb,
                true
            ),
            '{properties,supportingEvidenceIds}',
            '{"type":"array","items":{"type":"string"}}'::jsonb,
            true
        ),
        '{properties,contradictingEvidenceIds}',
        '{"type":"array","items":{"type":"string"}}'::jsonb,
        true
    )
WHERE schema_definition_id = (
    SELECT id FROM output_schema_definition
    WHERE schema_code = 'BASE_COMPLIANCE_V1' AND organization_id IS NULL
)
  AND status = 'ACTIVE';

UPDATE prompt_component
SET content_template =
    'Evaluate only unresolved semantic conditions using supplied evidence. '
    || 'Never emit an evidence ID absent from the request. Preserve contradictions and uncertainty. '
    || 'Decision rules: '
    || '(1) Evidence explicitly supports the requirement → COMPLIANT '
    || '(set supportingEvidenceIds). '
    || '(2) Evidence explicitly contradicts the requirement → NON_COMPLIANT '
    || '(set explicitContradiction=true and contradictingEvidenceIds). '
    || '(3) Evidence is silent or incomplete about a required element → INSUFFICIENT_INFORMATION '
    || '(list missingRequirementElements / missingInformation). '
    || 'Absence of information is NOT NON_COMPLIANT. '
    || 'Numeric thresholds (distance, duration, percent, capacity, count, RTO, RPO, SLA, '
    || 'validity dates, min/max): if the required value is missing from evidence → '
    || 'INSUFFICIENT_INFORMATION. Only NON_COMPLIANT when an explicit numeric value contradicts. '
    || 'Example: requirement needs >=350 km but evidence never states distance '
    || '→ INSUFFICIENT_INFORMATION. Evidence stating 120 km → NON_COMPLIANT. '
    || 'ISO / certificate sets: if the document set is not proven closed-world complete, '
    || 'missing certificates → INSUFFICIENT_INFORMATION. Only apply closedWorldApplied=true when '
    || 'system metadata or an explicit business rule asserts a closed certificate inventory. '
    || 'Never invent closedWorldApplied.',
    updated_at = now()
WHERE component_code = 'COMPLIANCE_EVALUATION_TASK'
  AND organization_id IS NULL;
