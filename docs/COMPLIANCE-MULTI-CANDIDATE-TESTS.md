# Compliance Multi-Candidate Tests

Domain model (V1): **one task per requirement**; candidates live inside the task
(`candidate_count` / `reranked_candidate_count` / selected evidence).

Fixture for 1×5: one requirement, retrieval/rerank top-5 evidence.

Expect: job `QUEUED→RUNNING→COMPLETED`, candidate counts visible, single slot path,
no duplicate evaluation.
