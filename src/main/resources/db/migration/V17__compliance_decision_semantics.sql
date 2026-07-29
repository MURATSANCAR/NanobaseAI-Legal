-- Harden compliance decision semantics: missing evidence ≠ NON_COMPLIANT;
-- closed-world may only come from system metadata / business rules.

UPDATE prompt_component
SET content_template =
    'Evaluate only unresolved semantic conditions using supplied evidence. '
    || 'Never emit an evidence ID absent from the request. Preserve contradictions and uncertainty. '
    || 'Decision rules: '
    || '(1) Evidence explicitly supports the requirement → COMPLIANT. '
    || '(2) Evidence explicitly contradicts the requirement → NON_COMPLIANT '
    || '(set explicitContradiction=true). '
    || '(3) Evidence is silent or incomplete about a required element → INSUFFICIENT_INFORMATION '
    || '(list missingRequirementElements / missingInformation). '
    || 'Absence of information is NOT NON_COMPLIANT. '
    || 'Example: requirement needs >=350 km between data centers but evidence never states distance '
    || '→ INSUFFICIENT_INFORMATION. Evidence stating 120 km → NON_COMPLIANT. '
    || 'ISO / certificate sets: if the document set is not proven closed-world complete, '
    || 'missing certificates → INSUFFICIENT_INFORMATION. Only apply closedWorldApplied=true when '
    || 'system metadata or an explicit business rule asserts a closed certificate inventory.',
    updated_at = now()
WHERE component_code = 'COMPLIANCE_EVALUATION_TASK'
  AND organization_id IS NULL;
