# Compliance Error Codes

Orchestration / claim codes (never collapse into `LLM_UNAVAILABLE`):

| Code | Meaning |
|------|---------|
| `JOB_ALREADY_CLAIMED` | Another worker holds a live lease |
| `JOB_LEASE_NOT_EXPIRED` | Running lease still valid |
| `JOB_ALREADY_COMPLETED` | Terminal success/partial/failed |
| `JOB_ALREADY_CANCELLED` | Already cancelled |
| `JOB_NOT_FOUND` | Missing job |
| `AGGREGATION_INCOMPLETE` | Finalize called with active tasks |
| `CANCEL_REQUESTED` / `LLM_CANCELLED` | Cooperative cancel |

Model technical codes (`SemanticEvaluationFailureCode`):

| Code | Use when |
|------|----------|
| `LLM_UNAVAILABLE` | Model endpoint truly unreachable |
| `LLM_GENERATION_TIMEOUT` / `LLM_TIMEOUT` | Generation timeout |
| `LLM_CONNECT_TIMEOUT` | Connect timeout |
| `LLM_INVALID_RESPONSE` | Schema/parse failure after policy |
| `LLM_CANCELLED` | Cancel observed around model path |
| `EVALUATION_ERROR` | Unexpected processing failure |

Do not map claim/lease/slot wait failures to `LLM_UNAVAILABLE`.
