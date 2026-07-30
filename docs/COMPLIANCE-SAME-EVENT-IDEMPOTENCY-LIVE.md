# Same-Event Idempotency Live

| Field | Value |
|-------|-------|
| Test | Same-event idempotency (DB claim race) |
| Event ID | `6a70b0b8-8b26-4ae9-80e5-bb3f48895870` |
| Worker A / B | winner / loser |
| Actual | 1 row / 1 winner / 1 loser (`rowsAffected` 1 then 0) |
| Result | **PASS** |

Script: `scripts/phase5_same_event_idempotency_live.py` mirrors `ProcessedMessageRepository.claim` (`ON CONFLICT ... DO UPDATE WHERE`).

Note: this proves event-level claim semantics used by `ComplianceAnalysisConsumer`. Full Rabbit dual-delivery remains covered by the same claim gate.
