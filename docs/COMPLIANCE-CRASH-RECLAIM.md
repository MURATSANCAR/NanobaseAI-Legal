# Compliance Crash / Reclaim

Heartbeat stop → lease expire → reclaim increments `lease_generation` + `attempt_count`.
Late persist from old generation is rejected as `STALE_WORKER_RESULT`.
