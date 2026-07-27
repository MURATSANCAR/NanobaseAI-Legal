import hashlib
import io
import json
import logging
import os
import re
import socket
import time
import uuid
from dataclasses import dataclass

import clamd
import pika
import requests
from docx import Document as DocxDocument
from minio import Minio
from pypdf import PdfReader

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
log = logging.getLogger("specai-document-worker")

CLAUSE_PATTERN = re.compile(
    r"^\s*(?P<number>\d+(?:\.\d+){0,5})[\s.)-]+(?P<text>\S.*)$"
)


@dataclass(frozen=True)
class Settings:
    rabbit_host: str = os.getenv("RABBITMQ_HOST", "rabbitmq")
    rabbit_user: str = os.getenv("RABBITMQ_USER", "specai")
    rabbit_password: str = os.getenv("RABBITMQ_PASSWORD", "change-me")
    minio_endpoint: str = os.getenv("MINIO_ENDPOINT", "minio:9000")
    minio_access_key: str = os.getenv("MINIO_ACCESS_KEY", "specai")
    minio_secret_key: str = os.getenv("MINIO_SECRET_KEY", "change-me")
    bucket: str = os.getenv("MINIO_BUCKET", "specai-original")
    backend_url: str = os.getenv("BACKEND_URL", "http://backend:8080")
    worker_token: str = os.getenv("DOCUMENT_WORKER_TOKEN", "change-me-worker-token")
    clamav_host: str = os.getenv("CLAMAV_HOST", "clamav")
    clamav_port: int = int(os.getenv("CLAMAV_PORT", "3310"))


settings = Settings()
minio = Minio(settings.minio_endpoint, access_key=settings.minio_access_key,
              secret_key=settings.minio_secret_key, secure=False)


def scan(content: bytes) -> None:
    scanner = clamd.ClamdNetworkSocket(settings.clamav_host, settings.clamav_port, timeout=60)
    result = scanner.instream(io.BytesIO(content))
    state, signature = result.get("stream", ("ERROR", "unknown"))
    if state == "FOUND":
        raise ValueError(f"Malware detected: {signature}")
    if state != "OK":
        raise RuntimeError(f"Malware scanner returned {state}")


def extract_pages(content: bytes, media_type: str) -> list[tuple[int, str]]:
    if media_type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
        document = DocxDocument(io.BytesIO(content))
        return [(1, "\n".join(paragraph.text for paragraph in document.paragraphs))]
    reader = PdfReader(io.BytesIO(content), strict=True)
    if reader.is_encrypted:
        raise PermissionError("Password-protected PDF")
    return [(index + 1, page.extract_text() or "") for index, page in enumerate(reader.pages)]


def extract_clauses(pages: list[tuple[int, str]], version_id: str) -> list[dict]:
    clauses: list[dict] = []
    current: dict | None = None
    namespace = uuid.UUID(version_id)
    for page_number, text in pages:
        for line in text.splitlines():
            normalized = " ".join(line.split())
            if not normalized:
                continue
            match = CLAUSE_PATTERN.match(normalized)
            if match:
                if current:
                    clauses.append(current)
                number = match.group("number")
                body = match.group("text")
                current = {
                    "id": str(uuid.uuid5(namespace, number)),
                    "parentId": None,
                    "number": number,
                    "title": body[:500],
                    "sourceText": body,
                    "pageNumber": page_number,
                    "sortOrder": len(clauses),
                }
            elif current:
                current["sourceText"] = f"{current['sourceText']} {normalized}"[:100_000]
        if current:
            clauses.append(current)
            current = None
    if not clauses:
        for page_number, text in pages:
            normalized = " ".join(text.split())
            if normalized:
                number = f"PAGE-{page_number}"
                clauses.append({
                    "id": str(uuid.uuid5(namespace, number)),
                    "parentId": None,
                    "number": number,
                    "title": f"Sayfa {page_number}",
                    "sourceText": normalized[:100_000],
                    "pageNumber": page_number,
                    "sortOrder": len(clauses),
                })
    return clauses


def callback(event: dict, status: str, clauses: list[dict] | None = None,
             message: str | None = None) -> None:
    response = requests.post(
        f"{settings.backend_url}/internal/v1/documents/processing-result",
        headers={"X-Worker-Token": settings.worker_token},
        json={
            "tenantId": event["tenantId"],
            "documentVersionId": event["documentVersionId"],
            "status": status,
            "message": message,
            "clauses": clauses or [],
        },
        timeout=30,
    )
    response.raise_for_status()


def process(event: dict) -> None:
    response = minio.get_object(settings.bucket, event["objectKey"])
    try:
        content = response.read()
    finally:
        response.close()
        response.release_conn()
    log.info("downloaded document=%s sha256=%s", event["documentId"],
             hashlib.sha256(content).hexdigest())
    callback(event, "VIRUS_SCANNING")
    scan(content)
    callback(event, "PARSING")
    pages = extract_pages(content, event["mediaType"])
    callback(event, "STRUCTURE_DETECTION")
    clauses = extract_clauses(pages, event["documentVersionId"])
    callback(event, "READY", clauses, f"{len(clauses)} clauses extracted")


def on_message(channel, method, properties, body: bytes) -> None:
    event = json.loads(body)
    try:
        process(event)
        channel.basic_ack(method.delivery_tag)
    except PermissionError as exc:
        log.warning("password protected document=%s", event.get("documentId"))
        callback(event, "PASSWORD_PROTECTED", message=str(exc))
        channel.basic_ack(method.delivery_tag)
    except ValueError as exc:
        log.error("document rejected document=%s reason=%s", event.get("documentId"), exc)
        callback(event, "MANUAL_REVIEW_REQUIRED", message=str(exc))
        channel.basic_ack(method.delivery_tag)
    except Exception:
        log.exception("document processing failed document=%s", event.get("documentId"))
        channel.basic_nack(method.delivery_tag, requeue=False)


def run() -> None:
    credentials = pika.PlainCredentials(settings.rabbit_user, settings.rabbit_password)
    while True:
        try:
            connection = pika.BlockingConnection(
                pika.ConnectionParameters(settings.rabbit_host, credentials=credentials,
                                          heartbeat=30, blocked_connection_timeout=60)
            )
            channel = connection.channel()
            channel.basic_qos(prefetch_count=1)
            channel.basic_consume("specai.document.processing", on_message)
            log.info("worker ready")
            channel.start_consuming()
        except (pika.exceptions.AMQPError, socket.error):
            log.exception("broker unavailable; retrying")
            time.sleep(5)


if __name__ == "__main__":
    run()
