# Categorizer v2.3 + Title Spacing Normalize

## Modules

| File | Role |
|---|---|
| `title_spacing_normalize.py` | Collapse PDF letter-spacing in titles/body |
| `requirement_categorizer_v2.py` (v2.3) | Priority rules + structural early patterns |
| `categorizer_lexicon.py` | Loads/merges JSON lexicons; batched match (10k+ ready) |
| `categorizer_lexicons/*.json` | Department-agnostic term families |

## Priority (v2.3)

1. SECURITY → 2. DOCUMENT → 3. FINANCIAL → 4. OPERATIONAL (early) →
5. PERSONNEL → 6. SCHEDULE (early) → 7. OPERATIONAL → 8. COMPLIANCE →
9. TECHNICAL → 10. SCHEDULE → 11. ADMINISTRATIVE → OTHER

`en az N` is TECHNICAL only when a tech object is nearby.

Grow vocabulary by editing/adding JSON under `categorizer_lexicons/` (see README there) — not by inventing department categories.

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
python requirement_categorizer_v2.py   # self_check 19/19 OK + lexicon_stats
pytest test_categorizer_title_normalize.py test_core_functions.py -q
```
