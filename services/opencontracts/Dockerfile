FROM python:3.13.5-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .

RUN useradd --system --uid 10001 --create-home opencontracts
USER opencontracts
EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8091/health/ready', timeout=3)"]
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8091", "--no-access-log"]
