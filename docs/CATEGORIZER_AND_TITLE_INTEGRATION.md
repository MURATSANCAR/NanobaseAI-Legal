# Categorizer v2.2 + Title Spacing Normalize

## Modules

| File | Role |
|---|---|
| `title_spacing_normalize.py` | Collapse PDF letter-spacing in titles/body |
| `requirement_categorizer_v2.py` (v2.2) | IT/kamu şartname categories (first match wins) |

## Priority (v2.2)

1. SECURITY → 2. DOCUMENT → 3. FINANCIAL → 4. OPERATIONAL (early) →
5. PERSONNEL → 6. SCHEDULE (early) → 7. OPERATIONAL → 8. COMPLIANCE →
9. TECHNICAL → 10. SCHEDULE → 11. ADMINISTRATIVE → OTHER

`en az N` is TECHNICAL only when a tech object is nearby.

## Wire

```python
from requirement_categorizer_v2 import categorize_requirement, categorize_many
# used inside requirement_from_clauses.attach_requirements_to_result
```

```python
from title_spacing_normalize import normalize_clause_titles
clauses = normalize_clause_titles(clauses)
```

## Self-check

```bash
cd services/document-intelligence
python requirement_categorizer_v2.py   # self_check 19/19 OK
pytest test_categorizer_title_normalize.py test_core_functions.py -q
```
