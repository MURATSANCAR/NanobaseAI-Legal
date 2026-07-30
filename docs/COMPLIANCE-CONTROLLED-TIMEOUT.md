# Compliance Controlled Timeout

## Status

**PENDING — not executed live in Phase 3**

## Required design (unchanged)

- Fault-inject delay for a single task/correlation only.
- Expected error class: `MODEL_TIMEOUT` (not `LLM_UNAVAILABLE`).
- Retry policy from `HttpComplianceAiGateway` (`specai.ai-orchestrator.compliance-retry-backoff`, max attempts).
- Slot must release on timeout.

## Blockers

No WireMock / per-correlation delay endpoint was enabled on nanobase without changing model runtime globally. Global timeout would violate “do not break global model service”.

## Next step

Add orchestrator test profile delay keyed by `correlationId` or task id, then re-run with `scripts/` harness.
