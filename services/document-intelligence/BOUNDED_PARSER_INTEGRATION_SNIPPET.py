"""Reference snippet kept for ops docs; wiring is already applied in bounded_parser.py / app.py."""

# Applied steps:
# 1. Graceful import of page_capability_pdf_inspector + pdf_inspector_bridge.health_check
# 2. classify_pdf_pages returns (pages, inspector_result); progress stage=pdf_inspector
# 3. /health/ready includes pdf_inspector
# 4. Env vars documented in compose + .env.example + docs/PARSER-ROUTING-PDF-INSPECTOR.md
