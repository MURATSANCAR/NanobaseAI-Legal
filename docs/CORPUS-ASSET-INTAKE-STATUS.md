# Corpus Asset Intake Status

**Date:** 2026-07-31 (post V33 recovery)

## Decision

```text
BROAD_DOCUMENT_GA_READY = false
DECISION_REASON = BLOCKED_CORPUS_ASSETS
```

## Inventory

| Metric | Value |
|--------|-------|
| Manifests | 15 |
| Binaries | 0 |
| READY_FOR_SMOKE | 0 |
| READY_FOR_QUALITY_GATE | 0 |
| Status | 15× `MISSING_ASSET` |

Asset root (prod-like): `/data/nanobaseai/legal/evaluation/corpus/assets/local`  
(`NANOBASE_CORPUS_ASSET_ROOT`; `/opt/nanobase/corpus` not writable on this host without elevated perms)

Artifacts: `/tmp/nanobase-corpus/intake-report.json`, `/tmp/nanobase-corpus/corpus-inventory.json`

## Commands

```bash
export NANOBASE_CORPUS_ASSET_ROOT=/data/nanobaseai/legal/evaluation/corpus/assets/local

python3 scripts/corpus_intake.py \
  --manifest-dir evaluation/corpus/manifests \
  --asset-root "$NANOBASE_CORPUS_ASSET_ROOT" \
  --write-proposed-manifest-patches \
  --output /tmp/nanobase-corpus/intake-report.json

python3 scripts/run_v11_corpus_e2e.py \
  --api http://127.0.0.1:8098 \
  --manifest-dir evaluation/corpus/manifests \
  --asset-root "$NANOBASE_CORPUS_ASSET_ROOT" \
  --slice all \
  --output-dir /tmp/nanobase-v11-e2e \
  --execute-live
```
