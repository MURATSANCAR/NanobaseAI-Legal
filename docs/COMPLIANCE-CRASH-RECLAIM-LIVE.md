# Compliance Crash / Reclaim Live

## Code delivered

`ComplianceLeaseReclaimScheduler`:

- Scans orgs every `specai.compliance.reclaim-interval-ms` (default 30s).
- Finds `RUNNING` jobs with `lease_expires_at < now` and no cancel.
- Requeues: status → `QUEUED`, clears claim, republishes `ComplianceAnalysisRequested`.
- Max attempts → `WORKER_REPEATEDLY_INTERRUPTED` / job `FAILED`.

## Live status

**PENDING — docker kill + reclaim cycle not completed in this Phase 3 window**

Scheduler is deployed on nanobase backend image. No `COMPLIANCE_JOB_RECLAIM_SCHEDULED` log observed yet (no expired leases during session).

## Recovery model (documented)

1. Message may already be ACK’d when worker crashes mid-execute.
2. Recovery is **lease expiry + reclaim scheduler → new outbox event**, not Rabbit redelivery alone.
3. Fencing via `lease_generation` rejects stale worker persist after reclaim.

## Script

`scripts/compliance_crash_reclaim_live.py` — force-expire lease; optional `--kill-backend`.
