from __future__ import annotations

import os
import threading
import uuid
from typing import Any

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

DOCLING_BASE_URL = os.getenv("DOCLING_BASE_URL", "http://document-intelligence:8090").rstrip("/")
SOURCE_BUCKET = os.getenv("S3_SOURCE_BUCKET", "specai-original")
PROVIDER_VERSION = os.getenv("OPENCONTRACTS_PROVIDER_VERSION", "specai-opencontracts-1.0")
API_TOKEN = os.getenv("OPENCONTRACTS_API_TOKEN", "")
HTTP_TIMEOUT = float(os.getenv("HTTP_TIMEOUT_SECONDS", "30"))

app = FastAPI(title="SpecAI OpenContracts Facade", version=PROVIDER_VERSION)
lock = threading.Lock()
corpora: dict[str, dict[str, Any]] = {}
documents: dict[str, dict[str, Any]] = {}


class CorpusRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    organizationId: uuid.UUID
    projectId: uuid.UUID


class CorpusResponse(BaseModel):
    id: str


class SubmissionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentVersionId: uuid.UUID
    objectStorageKey: str = Field(min_length=1, max_length=1024)
    originalFileName: str = Field(min_length=1, max_length=512)
    mimeType: str = Field(min_length=1, max_length=200)
    sha256: str = Field(min_length=64, max_length=64)
    correlationId: uuid.UUID


class SubmissionResponse(BaseModel):
    documentId: str
    status: str
    providerVersion: str


class StatusResponse(BaseModel):
    status: str
    currentStage: str
    progress: int
    message: str
    errorCode: str | None = None


def require_auth(authorization: str | None) -> None:
    if not API_TOKEN:
        return
    expected = f"Bearer {API_TOKEN}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")


def docling_client() -> httpx.Client:
    return httpx.Client(base_url=DOCLING_BASE_URL, timeout=HTTP_TIMEOUT)


@app.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/health/ready")
def ready() -> dict[str, Any]:
    try:
        with docling_client() as client:
            response = client.get("/health/ready")
            response.raise_for_status()
        return {"status": "UP", "docling": "UP", "providerVersion": PROVIDER_VERSION}
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"Docling unavailable: {exc}") from exc


@app.post("/api/v1/corpora", response_model=CorpusResponse)
def create_corpus(
    request: CorpusRequest,
    authorization: str | None = Header(default=None),
) -> CorpusResponse:
    require_auth(authorization)
    corpus_id = str(uuid.uuid4())
    with lock:
        corpora[corpus_id] = {
            "organizationId": str(request.organizationId),
            "projectId": str(request.projectId),
        }
    return CorpusResponse(id=corpus_id)


@app.post("/api/v1/corpora/{corpus_id}/documents", response_model=SubmissionResponse)
def submit_document(
    corpus_id: str,
    request: SubmissionRequest,
    authorization: str | None = Header(default=None),
) -> SubmissionResponse:
    require_auth(authorization)
    with lock:
        if corpus_id not in corpora:
            raise HTTPException(status_code=404, detail="Corpus not found")
    payload = {
        "documentVersionId": str(request.documentVersionId),
        "source": {
            "type": "S3_COMPATIBLE",
            "bucket": SOURCE_BUCKET,
            "objectKey": request.objectStorageKey,
        },
        "mimeType": request.mimeType,
        "languageHint": None,
        "ocrMode": "AUTO",
        "extractTables": True,
        "extractImages": False,
        "correlationId": str(request.correlationId),
    }
    try:
        with docling_client() as client:
            response = client.post("/v1/documents/parse", json=payload)
            response.raise_for_status()
            body = response.json()
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Docling submit failed: {exc}") from exc

    job_id = str(body.get("jobId") or "")
    if not job_id:
        raise HTTPException(status_code=502, detail="Docling response missing jobId")
    status = str(body.get("status") or "QUEUED")
    with lock:
        documents[job_id] = {
            "corpusId": corpus_id,
            "documentVersionId": str(request.documentVersionId),
            "originalFileName": request.originalFileName,
        }
    return SubmissionResponse(
        documentId=job_id,
        status=status,
        providerVersion=PROVIDER_VERSION,
    )


@app.get("/api/v1/documents/{document_id}/status", response_model=StatusResponse)
def document_status(
    document_id: str,
    authorization: str | None = Header(default=None),
) -> StatusResponse:
    require_auth(authorization)
    try:
        with docling_client() as client:
            response = client.get(f"/v1/jobs/{document_id}")
            response.raise_for_status()
            body = response.json()
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Docling status failed: {exc}") from exc
    return StatusResponse(
        status=str(body.get("status") or "QUEUED"),
        currentStage=str(body.get("currentStage") or "QUEUED"),
        progress=int(body.get("progress") or 0),
        message=str(body.get("message") or ""),
        errorCode=body.get("errorCode"),
    )


@app.get("/api/v1/documents/{document_id}/result")
def document_result(
    document_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    require_auth(authorization)
    try:
        with docling_client() as client:
            response = client.get(f"/v1/jobs/{document_id}/result")
            response.raise_for_status()
            body = response.json()
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Docling result failed: {exc}") from exc
    # Adapter expects provider-neutral DocumentExtractionResult; remap provider label.
    if isinstance(body, dict):
        body = dict(body)
        body["provider"] = "OPENCONTRACTS"
        body["providerVersion"] = PROVIDER_VERSION
        metadata = dict(body.get("metadata") or {})
        metadata["facade"] = "specai-opencontracts"
        metadata["upstreamProvider"] = "DOCLING"
        body["metadata"] = metadata
    return body


@app.post("/api/v1/documents/{document_id}/cancel")
def cancel_document(
    document_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, str]:
    require_auth(authorization)
    try:
        with docling_client() as client:
            response = client.post(f"/v1/jobs/{document_id}/cancel")
            response.raise_for_status()
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"Docling cancel failed: {exc}") from exc
    return {"status": "CANCELLED"}
