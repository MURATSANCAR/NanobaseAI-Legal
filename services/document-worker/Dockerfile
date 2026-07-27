FROM python:3.13.5-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN groupadd --system worker && useradd --system --gid worker --uid 10001 worker
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --require-hashes=false -r requirements.txt
COPY worker.py .
USER 10001
CMD ["python", "worker.py"]
