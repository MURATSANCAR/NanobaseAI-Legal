# Compliance Multi-Worker

Duplicate Rabbit delivery: second worker claim fails with `JOB_ALREADY_CLAIMED` /
`LEASE_NOT_EXPIRED` → safe no-op (not `LLM_UNAVAILABLE`). One model call, one slot,
one finalize.
