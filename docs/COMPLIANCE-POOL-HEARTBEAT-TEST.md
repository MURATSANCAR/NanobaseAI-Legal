# Pool Heartbeat Test

Under pool=5 with up to 3 concurrent RUNNING executes:

- Job/task heartbeats continued (no lease loss attributed to DB pool).
- Hikari timeout count remained **0**.
- Redis capacity heartbeats kept active leases ≤ 3; cleaned to **0** at end.

No `LEASE_EXPIRED` caused by connection starvation observed in the PASS run.
