# Compliance Transaction Boundaries

## Goal

No database transaction may span:

- LLM HTTP calls
- Profile slot waits
- Long computation / retry backoff
- RabbitMQ network I/O

## Flow

```text
RabbitMQ message
→ claimJob (short TX) → RUNNING committed
→ per task: claimTask (short TX)
→ slot wait (no TX)
→ model execute (no TX)
→ complete/fail task (short writes / auto-commit)
→ finalizeJob (short TX)
→ ack
```

## Ownership

| Component | Owns TX? |
|-----------|----------|
| `ComplianceAnalysisProcessor.process` | **No** |
| `ComplianceJobTransactionService` | **Yes** — short `REQUIRES_NEW` only |
| Model / slot / gateway | **No** |

Self-invocation is forbidden for TX boundaries: all short TX operations live on
`ComplianceJobTransactionService`, not on private methods of the processor.

## Visibility

After `claimJob` commits, external `GET /compliance-analyses/{id}` must observe
`status=RUNNING` while the model is still running.
