# Compliance Orchestration Tests

## Architecture (unit)

- `ComplianceTransactionBoundaryArchitectureTest`
  - `process` must not be `@Transactional`
  - TX service must not depend on model client / slot manager

## Claim / finalize (unit)

- `ComplianceJobTransactionServiceTest`
  - successful claim
  - already-claimed diagnosis ≠ LLM_UNAVAILABLE
  - finalize idempotent when already terminal

## Live / orchestrated (scripts)

Run in order; do not proceed to multi-candidate optimization until 1–3 pass:

1. `scripts/orchestrated_compliance_1x1.py` — expect `QUEUED → RUNNING → COMPLETED`
2. Polling visibility while model runs (`RUNNING` committed)
3. Cancel while RUNNING — endpoint latency low, job ends `CANCELLED`
4. 1×5 candidates
5. Controlled timeout → `PARTIALLY_COMPLETED` (or retry then partial)
6. Two workers same job — single claim
7. Crash/reclaim after lease expiry

## Acceptance signal

```text
QUEUED → RUNNING (poll visible) → ~model duration → COMPLETED
Cancel not blocked by job-row lock
startedAt < completedAt
claimDurationMs present
LLM_UNAVAILABLE = 0 for healthy model
```
