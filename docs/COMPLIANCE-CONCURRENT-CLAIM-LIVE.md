# Concurrent Claim Live

## Test B — same job, different workers (conditional UPDATE race)

| Alan | Değer |
|------|-------|
| Job ID | from `/tmp/phase4_concurrent_claim_report.json` on nanobase |
| Before race | `QUEUED` (pending outbox marked published) |
| Worker A claimMs | ~388 |
| Worker B claimMs | ~389 |
| Successful claims | **1** |
| Loser | sees winner's `claimedBy`; no second claim |
| Lock wait | ~1 ms delta (not seconds) |
| Sonuç | **PASS** |

## Test A — same event idempotency

**PENDING** this Phase 4 window (terminal duplicate covered in Phase 3).
