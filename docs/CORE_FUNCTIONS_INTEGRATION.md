# Core Functions Integration (D / A / B / C)

## Modules

| Code | File | Role |
|---|---|---|
| D | `markdown_clause_parser.py` | Hierarchical madde/heading parser + table isolation |
| A | `requirement_from_clauses.py` | Deterministic TR tender requirements |
| B | `error_to_state.py` | Guard/parser error → terminal state + audit |
| C | `reprocess_policy.py` | FORCE_* reprocess plan |

## Wire order

### 1) D — replace naive splitter in `markdown_short_circuit.py`

```python
from markdown_clause_parser import parse_markdown_clauses

clauses, tables = parse_markdown_clauses(markdown, page_count=page_count)
```

### 2) A — attach requirements after clauses exist

```python
from requirement_from_clauses import attach_requirements_to_result

result = attach_requirements_to_result(result)
```

Also call after Docling `merge_batch_results` if desired.

### 3) B — guard / SafeProcessingError path in `app.py`

```python
from error_to_state import build_audit_event, resolve_error_state

decision = resolve_error_state(guard_error.code, safe_message=guard_error.message)
audit = build_audit_event(decision, job_id=job_id, ...)
update_job(..., status=decision.jobStatus, stage=decision.terminalStatus, error_code=decision.errorCode)
```

### 4) C — reprocess endpoint

`POST /v1/jobs/{job_id}/reprocess` with body `{ "forceMode": "...", "correlationId": "..." }`.

```python
from reprocess_policy import decide_reprocess_plan, apply_plan_to_parse_options

plan = decide_reprocess_plan(force_mode=request.forceMode, previous_error_code=...)
options = apply_plan_to_parse_options(plan)
# Always new jobId; do not overwrite previous result_json
```

## Force modes

| Mode | OCR | Short-circuit | Docling |
|---|---|---|---|
| AUTO | AUTO | allowed | fallback |
| FORCE_SHORT_CIRCUIT | DISABLED | required/allowed | no |
| FORCE_DOCLING | AUTO | no | yes |
| FORCE_OCR | FORCED | no | yes |

## Tests

```bash
cd services/document-intelligence
pytest test_core_functions.py -q
```
