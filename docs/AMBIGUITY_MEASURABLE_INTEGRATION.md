# Ambiguity measurable fields (prod)

`textambiguity/` fills structured measurable attributes from requirement text so
the policy-driven ambiguity engine does not treat numeric clauses as missing
`measurement` / `operator` / `testCondition` / `acceptanceThreshold`.

## Package

| File | Role |
|---|---|
| `textambiguity/measurable_fields.py` | Regex extractors for quantity / within-duration / qualitative presence |
| `textambiguity/ambiguity_prioritizer.py` | `apply_auto_resolution`, `prioritize_ambiguities` (HIGH\|MEDIUM\|LOW) |
| `test_ambiguity_measurable.py` | Unit coverage for extract + auto-resolve + priority |

## Behaviour

1. **Measurable extraction**

| Text | operator | threshold | measurement |
|---|---|---|---|
| en az 16 adet DIMM… | `>=` | `16` | `adet DIMM` (approx) |
| 30 takvim günü içinde | `<=` | `30` | `takvim_günü` |
| uygun olacaktır (nitel) | `==` | `true` | `presence` (conf ~0.55) |

2. **Priority**

- TECHNICAL/SECURITY + MUST + missing acceptance threshold → **HIGH**
- Auto-resolve: `measurableConfidence >= 0.85` and all four fields present → `RESOLVED_STRUCTURED` (drops from candidate queue)

3. **Wire (after requirement extract)**

```python
from textambiguity import apply_auto_resolution, prioritize_ambiguities

reqs, events = apply_auto_resolution(reqs)
queue = prioritize_ambiguities(reqs)  # remaining CANDIDATE only
```

Wired in `requirement_from_clauses.attach_requirements_to_result`.

Java risk analysis mirrors enrichment before ambiguity feature scoring
(`MeasurableFieldsEnricher`) so re-running risk on existing requirements applies
the same structured fill / priority description prefix.

## Expected effect (was 40 AMBIGUITY_CANDIDATE)

- Numeric / duration clauses → attributes filled → no longer ambiguity findings
- Qualitative “uygun olacaktır” → remain candidates with `[HIGH|MEDIUM]` description
  and `suggestedFields=…` suffix for UI triage

## Self-check

```bash
cd services/document-intelligence
python -m pytest test_ambiguity_measurable.py -q
```
