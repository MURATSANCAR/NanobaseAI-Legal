# Compliance 1×5 Live Test

## Fixture

```json
{
  "fixtureCode": "COMPLIANCE_1X5_TIER_TEST",
  "organizationId": "11111111-1111-1111-1111-111111111111",
  "projectId": "76c32181-c0c5-4e6f-90df-d90d3f38845c",
  "requirementId": "184e7eac-7808-4b79-86df-a70bf619bc33",
  "requirementText": "Ana veri merkezi en az TIER 3 standardında olmalıdır.",
  "expectedCandidateCount": 5,
  "expectedRerankedCount": 5,
  "seededFixtureFragmentIds": [
    "a1000000-0000-4000-8000-000000000001",
    "a1000000-0000-4000-8000-000000000002",
    "a1000000-0000-4000-8000-000000000003",
    "a1000000-0000-4000-8000-000000000004",
    "a1000000-0000-4000-8000-000000000005"
  ]
}
```

Script: `scripts/orchestrated_compliance_1x5.py`  
Temporary test policy overrides (restored after run): `reranking=5`, `minimumValidityScore=0.0`.

## Result

| Alan | Değer |
|------|-------|
| Test | 1×5 live |
| Fixture | COMPLIANCE_1X5_TIER_TEST |
| Job ID | `d2b2b9ef-0e05-445d-9f93-624d548a3b45` |
| Task ID | `027a8677-fb31-4e5b-8fd1-fec1ac49a9e1` |
| Worker | `worker-d2b2b9ef-…-ce1ef519-…` |
| Lease generation | 1 |
| Başlangıç | 2026-07-30T18:16:17Z |
| Bitiş | 2026-07-30T18:17:35Z |
| Final status | COMPLETED |
| candidateCount | 6 |
| rerankedCandidateCount | 5 |
| selectedEvidenceCount | 5 |
| claim_duration_ms | 3719 |
| job_duration_ms | 81213 |
| generationMs | ~77952 |
| duplicateEvidenceLinks | 0 |
| LLM_UNAVAILABLE | 0 |
| Slot acq/rel | yes |
| RUNNING poll-visible | yes |
| Sonuç | **PASS** |

## Duplicate candidate SQL

```sql
-- 0 rows for job d2b2b9ef-…
```

Domain note: one requirement → one task; five reranked candidates inside the task (not five tasks).
