# Crash / Reclaim Live

**PENDING** — SIGKILL + short-lease reclaim cycle not completed in Phase 4 window.

Scheduler `ComplianceLeaseReclaimScheduler` remains deployed. Fault-injection pause + lease force-expire scripts exist (`scripts/compliance_crash_reclaim_live.py`) but docker kill loop was not executed after fault-injection restore.
