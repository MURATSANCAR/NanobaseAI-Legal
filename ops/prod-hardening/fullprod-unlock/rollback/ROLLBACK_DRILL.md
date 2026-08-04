# Rollback drill — Markdown short-circuit flag

**Süre:** ~15 dk  
**Amaç:** `PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT=false` ile Docling’e düşüldüğünü kanıtla, sonra geri aç.

## Önkoşul

- Container: `specai-legal-document-intelligence-1` healthy
- Dijital PDF fixture: `/data/fixtures/digital-100p.pdf` (≤120 sayfa)
- Compose overlay: `compose.easymeeting.yaml` (flag burada hardcoded `true`)

## A) Flag kapat

`compose.easymeeting.yaml` içinde:

```yaml
PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT: "false"
```

Recreate:

```bash
cd /data/nanobaseai/legal
sudo docker compose -p specai-legal --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml -f compose.easymeeting.yaml \
  up -d --force-recreate --no-deps document-intelligence
```

Doğrula:

```bash
sudo docker exec specai-legal-document-intelligence-1 \
  printenv PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT
# → false

curl -sS http://document-intelligence:8090/metrics | grep short_circuit_enabled
# gauge 0 (veya container içinden)
```

Parse smoke (aynı dijital PDF): sonuçta `metadata.shortCircuited` **olmamalı** / `provider` Docling tarafı.

## B) Flag geri aç

```yaml
PDF_INSPECTOR_MARKDOWN_SHORT_CIRCUIT: "true"
```

Aynı recreate. `printenv` → `true`. Metrics gauge → `1`. Short-circuit parse tekrar çalışır.

## Kayıt şablonu

Dosya: `ops/prod-hardening/fullprod-unlock/rollback/ROLLBACK_DRILL_RESULT.md`

| Adım | Saat | Kanıt | OK? |
|---|---|---|---|
| Flag false | | env + metrics | |
| Parse w/o SC | | job/result | |
| Flag true | | env + metrics | |
| Parse w/ SC | | shortCircuited=true | |
