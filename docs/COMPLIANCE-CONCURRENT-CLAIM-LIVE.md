# Concurrent Claim Live

## Test B — same job, different workers (conditional UPDATE race)

| Alan | Değer |
|------|-------|
| Job ID | `8b3c7ef0-2e4a-4f6e-bec2-0ff7775f15d6` |
| Before race | `QUEUED` (pending outbox marked published) |
| Worker A claimMs | ~388 |
| Worker B claimMs | ~389 |
| Successful claims | **1** |
| Loser | sees winner's `claimedBy`; no second claim |
| Lock wait | ~1 ms delta (not seconds) |
| Sonuç | **PASS** |

## Test A — same event idempotency

**PENDING** this Phase 4 window (terminal duplicate covered in Phase 3).
