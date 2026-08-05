# Categorizer v2 + Title Spacing Normalize

## Modules

| File | Role |
|---|---|
| `title_spacing_normalize.py` | Collapse PDF letter-spacing in titles/body (`SU NU CU` → `SUNUCU`) |
| `requirement_categorizer_v2.py` | IT/kamu şartname categories before OTHER |

## Wire order

### 1) Clause list — `normalize_clause_titles`

Applied at the end of `markdown_clause_parser.parse_markdown_clauses` and again
(defensively) after parse in `markdown_short_circuit.py`.

```python
from title_spacing_normalize import normalize_clause_titles

clauses = normalize_clause_titles(clauses)
```

### 2) Requirement extract — `categorize_requirement` / `categorize_many`

`requirement_from_clauses.py` imports v2 and sets `category` via
`categorize_requirement`, then runs `categorize_many` for a final pass.

```python
from requirement_categorizer_v2 import categorize_requirement, categorize_many
```

Priority scan: TECHNICAL → COMPLIANCE → SECURITY → DOCUMENT → SCHEDULE →
FINANCIAL → PERSONNEL → OPERATIONAL → ADMINISTRATIVE → OTHER.

### 3) Image rebuild

Dockerfile must COPY:

- `requirement_categorizer_v2.py`
- `title_spacing_normalize.py`

## Tests

```bash
cd services/document-intelligence
pytest test_core_functions.py test_categorizer_title_normalize.py -q
```

## Reprocess check (DMO)

After deploy, `POST /v1/jobs/{id}/reprocess` with `forceMode=FORCE_SHORT_CIRCUIT`
(or portal reprocess). Expect MUST `mustCategories`: TECHNICAL ↑, OTHER ↓.
