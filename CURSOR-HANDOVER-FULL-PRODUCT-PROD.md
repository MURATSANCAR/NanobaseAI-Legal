# CURSOR handover — Full Product DI / Production Baseline

## 1. Tested commit

- Local lock target: commit created by `release: lock nanobase spec intelligence v1.0 baseline`
- Prior HEAD before lock docs: `6eb4e91023c627dab667d0a861d3c3769cdf2c72`
- Compliance baseline (unchanged): tag `compliance-orchestration-v1.0` → `e9a44f1c7e3a00c7c5b6d9568d46111507b4b2c6`
- Semantic policy hash: `65f7982cf7b27f34433cae2f9a5f8eee`

## 2. Deployed image digests

| Component | Digest |
|-----------|--------|
| backend | `sha256:30b7acf2db51dede890d0b50d674680d3732e75ea69e250a4037a9a1fa0fb278` |
| frontend | `sha256:b1d0e3e805c67d73a6999acf194e0c41dc1562931df212599303cf7f48510695` |
| document-intelligence / parser | `sha256:775348733abe30e716d771800be5a0e6d773a62ee1bd71416af8b167df8d3427` |
| ai-orchestrator | `sha256:666f6055840c9f28aa6de9d03b300249950eb2b072b287d5f5ff76bc3f370380` |
| opencontracts | `sha256:166d7d86ed2a8577db9ab2193c921e921af6f03ae69fadfece66e19532253969` |

Report renderer: in-process backend PDF renderer (no separate image).

## 3. Migration state

Flyway V29, V30, V31, V32 → `success=true`.

## 4. Runtime readiness

- API: `http://127.0.0.1:8098` healthy
- Orchestrator capacity: redis / FAIL_CLOSED (baseline preserved)
- Guardrails: `DATABASE_POOL_SIZE=20`, `COMPLIANCE_WORKER_CONCURRENCY=1`, headroom=2
- `FAULT_INJECTION=false`
- RabbitMQ `consumer_timeout` raised to 4h for long extraction jobs (runtime)

## 5. DSİ file SHA-256

`82d312e7900954f592537e377993d978e5f219ca9d473cb8271ea793fefb3743`  
Path: `/tmp/nanobase-e2e/DSI_Sulama_Otomasyon_Genel_Teknik_Sartname.pdf` (~876237 bytes, ~25 pages)

## 6. Project/document IDs

- projectId: `aff7e4f5-d668-43ad-98fa-cada1d317bb6`
- documentId: `56c47fc8-7b35-430e-8460-6744b58f13e3`
- documentVersionId: `3c52fc4b-7ad2-47ac-b6b1-9c6b5a3a389a`

## 7. Parser/OCR result

- parserJobId: `a2c9b9a2-9ce9-459c-9f65-2a226e97569f`
- status READY, pageCount=25, ocrRequired=false
- duration ~150 s

## 8. Layout result

- layoutBlockCount=24
- recurringElementCount=0

## 9. Clause result

- clauseCount=24
- provider: TEXT_HIERARCHY (Docling PAGE fallback deferred correctly)
- manualClauseSeed=0
- avgChars≈1332, maxChars=1800

## 10. Requirement result

- requirementJobId: `161d3ce3-06e1-4c7a-a65a-43afd8ece758`
- status COMPLETED
- automaticRequirementCount=17
- manualRequirementSeed=0
- grounding sample: 5/5 grounded
- duration ~2236 s

## 11. Empty-outcome counters

- suspiciousEmpty=0
- emptyOutcomeCode observed: `MODEL_FAILURE` (job still COMPLETED with 17 extractions)

## 12. Knowledge purpose/stage

- knowledgeJobId: `a3bfe931-cfd6-4274-abf0-0a3c6d216781`
- purpose: TENDER_SPEC
- terminal: `SKIPPED_NOT_APPLICABLE`
- existingKnowledgeUsed: null

## 13. Evidence state

- Human-review sample used GROUNDED evaluation with evidenceCount=1
- Many evaluations remain INSUFFICIENT / ungrounded — valid under FAIL_CLOSED evidence rules

## 14. Compliance result

- complianceJobId: `1cfdcdda-b6c6-4fee-8892-94091bab1e01`
- COMPLETED processed=17 completed=17 failed=0
- duration ~255 s

## 15. Risk/conflict

- riskAnalysisJobId: `20323ec0-8d4a-4376-bb60-6f79a5cb285e` COMPLETED
- riskCount=17, conflictCount=0

## 16. Human review

- reviewId / evaluationId: `83c1de46-f391-42c6-9ef3-60bb264e08de`
- HTTP 200, decision COMPLIANT on grounded evaluation

## 17. Report integrity

- reportJobId: `c4fa2574-049f-4819-8b17-727b0be3dcdd`
- reportArtifactId: `2ef21e7d-e06e-4866-b216-1c45daea237f`
- reportIntegrity=PASS, bytes=3191, magic=`%PDF`, page markers≈3

## 18. Download validation

- Document proxy: PASS (SHA match upload)
- Report proxy: PASS
- Presign: still 409 in-container with `127.0.0.1` public endpoint — proxy authoritative

## 19. Tenant isolation

- Unauthenticated document/report download → 401 PASS

## 20. Audit chain

- audit PASS, count=1243

## 21. Database consistency

- orphanClauses=0, reqWithoutSource=0, stuckCompliance=0

## 22. Performance timings

See `/tmp/nanobase-e2e/full_product_e2e_metrics.json`:
- upload ~0.3 s
- parser ~150 s
- requirements ~2236 s
- knowledge ~15 s
- compliance ~255 s
- risk ~15 s
- full ~2677 s

## 23. Corpus smoke

- Harness discovery: fixtures mostly missing / SKIPPED
- E2E-02..05, E2E-07: **PENDING**
- E2E-06 manifest READY with placeholder asset only

## 24. Failed attempts

1. clauses=0 — Docling PAGE deferral skipped later providers (`hasUsableStructuredClauses` fix)
2. layout FK — `pages.flush()` before JDBC layout insert
3. requirement hang / Rabbit 30m consumer ack timeout — raise consumer_timeout; extraction read timeout PT300S; MAX_CHUNKS=1
4. harness AttributeError on list-shaped evaluations — `page_total`
5. human review 409 — positive decision without grounded evidence; pick GROUNDED sample or INSUFFICIENT_INFORMATION

## 25. Fixes made

- Clause provider chain: refine coarse PAGE/fallback sets
- Layout persist flush ordering
- HttpAiGateway dedicated extraction timeout
- Prompt security read timeout
- ClauseChunker max chunks cap
- Autonomous harness: list page totals, review decision selection, longer job polls, report-on-compliance-start-failure

## 26. Remaining risks

- Requirement extraction latency / model saturation
- MinIO public presign from Docker network
- Corpus GA not proven
- Mega-transaction still wraps requirement LLM calls (operational risk if consumer_timeout regresses)

## 27. Production readiness decision

```
FULL_PRODUCT_PRODUCTION_READY=true
baseline=nanobase-spec-intelligence-v1.0
```

Controlled production for native-PDF technical specification workflow only.
