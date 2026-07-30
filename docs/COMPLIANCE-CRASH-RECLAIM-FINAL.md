# Crash / Reclaim Final

| Field | Value |
|-------|-------|
| Job ID | `f0a567d1-9370-461c-af46-e848d7f9ff59` |
| Worker A | `…09176d52…` |
| Worker B | `…6a617f15…` |
| Task generation | 1 → 2 |
| Kill | `docker kill specai-legal-backend-1` (SIGKILL-equivalent) |
| Evaluations | 1 |
| Result | **PASS** |

Script: `scripts/phase5_crash_reclaim_live.py` (+ lease force-expire after kill so reclaim does not wait 15m).
