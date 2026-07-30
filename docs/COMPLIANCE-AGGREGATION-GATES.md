# Compliance Aggregation Gates

## Code change (Phase 3)

`finalizeJob` counts active tasks as:

```text
QUEUED, CLAIMED, READY_FOR_MODEL, WAITING_FOR_SLOT, RUNNING, RETRY_WAIT
```

If active or `RETRY_WAIT` remain and cancel not requested → status `AGGREGATION_DEFERRED` (job stays RUNNING). Processor returns without publishing terminal events.

## Live READY_FOR_MODEL deferred test

**PENDING** — controlled leave-task-in-READY_FOR_MODEL not executed.

## Related

1×5 job `d2b2b9ef-…` finalized to COMPLETED with activeTaskCount=0 after task COMPLETED.
