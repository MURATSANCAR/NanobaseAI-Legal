# Compliance RabbitMQ ACK / Recovery

## Delivery model

1. Job create writes outbox `ComplianceAnalysisRequested`.
2. Outbox publisher → RabbitMQ → consumer → `ComplianceAnalysisProcessor.process`.
3. Consumer ACK after handler returns (idempotency via processed-message store where configured).

## Crash recovery model (explicit)

If worker dies **after ACK** while job `RUNNING`:

- Rabbit will **not** redeliver.
- Recovery depends on **`ComplianceLeaseReclaimScheduler`**: expired lease → requeue job `QUEUED` → new outbox event.

If worker dies **before ACK**:

- Rabbit redelivery expected; claim conditional update makes second worker no-op or reclaim if lease expired.

## Live dual-delivery observation

Job `de0cbd38-…`: duplicate outbox after terminal → `JOB_ALREADY_COMPLETED` skip in 2 ms. No second evaluation.
