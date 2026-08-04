# NanobaseAI-Legal – pdf-inspector Production Integration

**Prod-üstü seviye** entegrasyon paketi.

## Dosyalar

```text
services/document-intelligence/
├── pdf_inspector_bridge.py              # Core adapter (timeout, fallback, health)
├── page_capability_pdf_inspector.py     # Enhanced classifier (drop-in)
├── test_pdf_inspector_bridge.py         # Unit + optional live tests
└── requirements.txt                     # includes pdf-inspector>=0.1.0
docs/
└── PARSER-ROUTING-PDF-INSPECTOR.md      # Architecture decision + ops runbook
```

## Entegrasyon (uygulandı)

1. `pdf-inspector>=0.1.0` dependency eklendi
2. Bridge + enhanced classifier DI altına kondu
3. `bounded_parser.py` classify çağrısı + `pdf_inspector` progress event
4. `/health/ready` `pdf_inspector` anahtarı
5. Compose / `.env.example` env var’ları

## Environment

```bash
PDF_INSPECTOR_ENABLED=true
PDF_INSPECTOR_TIMEOUT_SECONDS=8
PDF_INSPECTOR_MAX_PAGES_FOR_FULL_EXTRACT=120
PDF_INSPECTOR_MIN_CONFIDENCE=0.55
```

## Verify

```bash
cd services/document-intelligence
pytest test_pdf_inspector_bridge.py test_bounded_parser.py test_bounded_integration.py -q

docker compose -f compose.yaml -f compose.easymeeting.yaml build document-intelligence
curl -s http://127.0.0.1:8090/health/ready | jq .pdf_inspector
```

## Rollback

`PDF_INSPECTOR_ENABLED=false` — schema/migration yok.
