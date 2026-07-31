# Nanobase Spec Intelligence v1.1 evaluation corpus

Provider-neutral evaluation package.

| Path | Purpose |
|------|---------|
| `manifests/` | Fixture manifests (JSON) |
| `annotations/` | Ground-truth annotations |
| `schemas/` | Manifest + annotation JSON Schemas |
| `policy/` | Quality / minimum matrix policy |
| `reports/` | Generated local reports (optional) |
| `assets/local/` | Gitignored binaries for lab use |

Asset root override: `NANOBASE_CORPUS_ASSET_ROOT`.

Commands:

```bash
python3 scripts/corpus_intake.py \
  --manifest-dir evaluation/corpus/manifests \
  --asset-root "${NANOBASE_CORPUS_ASSET_ROOT:-evaluation/corpus/assets/local}" \
  --output /tmp/nanobase-corpus/intake-report.json

python3 scripts/run_v11_corpus_e2e.py \
  --api http://127.0.0.1:8098 \
  --manifest-dir evaluation/corpus/manifests \
  --asset-root "${NANOBASE_CORPUS_ASSET_ROOT:-evaluation/corpus/assets/local}" \
  --slice all \
  --output-dir /tmp/nanobase-v11-e2e
```
