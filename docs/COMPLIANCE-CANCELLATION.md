# Compliance Cancellation

## API

`POST /compliance-analyses/{jobId}/cancel` writes cooperative cancel fields in a
**short** transaction via `ComplianceJobTransactionService.requestCancel`:

- `cancel_requested_at`
- `cancel_requested_by`
- `cancel_reason`

If the job is still `QUEUED`, it is cancelled immediately along with queued tasks.

If the job is `RUNNING`, status stays `RUNNING` until the worker observes the flag,
cancels remaining tasks, and finalizes as `CANCELLED`.

## Guarantees

- Cancel endpoint must not wait on a long `FOR UPDATE` held across LLM
- Late model responses after cancel must not flip the job to `COMPLETED`
- Slot release remains the responsibility of the orchestrator `finally` path
  (unchanged ProfileSlotManager capacity)
