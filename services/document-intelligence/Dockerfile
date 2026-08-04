FROM python:3.12.10-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DOCLING_TELEMETRY_ENABLED=false

RUN groupadd --system docling && useradd --system --gid docling --create-home docling
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --requirement requirements.txt

COPY app.py page_capability.py page_capability_pdf_inspector.py \
     parser_checkpoint.py bounded_parser.py pdf_inspector_bridge.py \
     markdown_short_circuit.py ./
RUN mkdir -p /var/lib/specai /tmp/specai-docling \
    && chown -R docling:docling /var/lib/specai /tmp/specai-docling /app

USER docling
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health/live', timeout=3)"]
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1", "--no-access-log"]
