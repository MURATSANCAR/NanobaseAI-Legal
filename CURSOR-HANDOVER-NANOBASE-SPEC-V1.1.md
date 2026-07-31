# CURSOR handover — Nanobase Spec Intelligence v1.1

## 1. Foundation commit

- Tip / wiring: `cf19d1d` (already on `main`)
- `nanobase-spec-intelligence-v1.1` tag **not** created

## 2. v1.0 baseline verification

- `compliance-orchestration-v1.0` → `e9a44f1`
- `nanobase-spec-intelligence-v1.0` → `10a1cad637798dea37ba35d895bc53daf90e1b63`
- V28–V32 unchanged

## 3. V33 deployment

- **PASS** — Flyway version `33` applied on prod-like DB
- Feature flags remain default **OFF**

## 4–8. Corpus

- Manifests: 15
- Binaries: 0 → `BLOCKED_CORPUS_ASSETS`
- License / privacy / GT: pending
- Request pack: `docs/V1.1-CORPUS-ASSET-REQUEST.md`

## DSİ v1.0 Regression After V33

**PASS** after legal orchestrator stabilization.

Sanitized artifact:

```text
evaluation/reports/dsi-v1-regression-pass-after-orchestrator-fix-2026-07-31.json
```

### Live Job IDs

- Project `f31977f3-ea4c-4967-97d5-15ebdad1bee6`
- Document `0722f37b-efc6-480f-89d5-9421ce0cbcf4`
- Parser `7b5d1b1a-f311-4452-8cd1-f6e65dc418a1`
- Requirements `2b2a469a-dcc2-42a1-88c1-540989108b6b` (extracted=17, TIMEOUT_EMPTY present, seeds 0)
- Knowledge `f65d4bf2-d287-444f-9c38-da6698e5ebc3` → `SKIPPED_NOT_APPLICABLE`
- Compliance `83e5bf4a-c488-405b-a6a9-d8019fdc6585` → **COMPLETED 17/17**
- Report `82bfde6a-b4a3-48a3-a8d7-4b832eda1255`

### Report Integrity Result

`reportIntegrity=PASS` (3191 bytes; proxy download PASS; tenant isolation smoke PASS)

## Balanced Timeout Change

```text
AI_ORCHESTRATOR_BALANCED_GENERATION_TIMEOUT=PT720S
```

Config-driven override of BALANCED `timeoutSeconds` in the AI orchestrator. FAST profiles are not changed.

```text
Validated runtime value for the current BALANCED local-model profile.
Further performance optimization remains part of v1.1 hardening.
```

## Previous Timeout Failures

Prior FAIL: compliance `47d7ac4e-…` PARTIALLY_COMPLETED — two tasks `LLM_GENERATION_TIMEOUT` at ~**600s** generation.

## Legal Orchestrator Compose Topology

Service name: **`ai-orchestrator`** (not `legal-orchestrator`).

Required:

```bash
docker compose \
  -p specai-legal \
  --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml \
  -f compose.easymeeting.yaml \
  up -d --force-recreate ai-orchestrator
```

Never recreate with only `compose.yaml`.

Full runbook: `docs/LEGAL-ORCHESTRATOR-OPERATIONS.md`

## Redis DNS Root Cause

Wrong Compose project/file combination left the orchestrator off the EasyMeeting Redis network → Redis DNS failed.

## FAIL_CLOSED Expected Behavior

When Redis capacity is unreachable, the orchestrator **FAIL_CLOSED** and refuses new model work. This is required security behavior, not a defect to remove.

## Recovery Commands

See `docs/LEGAL-ORCHESTRATOR-OPERATIONS.md` (recreate, Redis DNS `getent`, `/health/ready`).

## Current Corpus Blocker

```text
BROAD_DOCUMENT_GA_READY = false
DECISION_REASON = BLOCKED_CORPUS_ASSETS
```

15 manifests / 0 binaries / 0 licensed assets / 0 ground truth.

## Release Decision

```text
FULL_PRODUCT_PRODUCTION_READY = true
V1_0_REGRESSION = PASS
V1_1_FOUNDATION_IMPLEMENTED = true
V1_1_RUNTIME_REGRESSION_VALIDATED = true
BROAD_DOCUMENT_GA_READY = false
DECISION_REASON = BLOCKED_CORPUS_ASSETS
V1_1_RELEASE_CANDIDATE_ACCEPTED = false
```

No `nanobase-spec-intelligence-v1.1` tag. No automatic remote push.
