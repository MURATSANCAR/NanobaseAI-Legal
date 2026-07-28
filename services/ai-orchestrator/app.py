from __future__ import annotations

import json
import logging
import os
import time
import uuid
from dataclasses import dataclass
from typing import Any

import httpx
from fastapi import FastAPI, Header, HTTPException
from jsonschema import Draft202012Validator
from pydantic import BaseModel, ConfigDict, Field

LOG = logging.getLogger("specai.ai_orchestrator")
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
LOGICAL_MODEL = "nanobase-spec-ai"


@dataclass(frozen=True)
class Deployment:
    profile: str
    base_url: str
    runtime_model: str
    api_key: str | None
    timeout_seconds: float


class ExtractionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str
    profile: str
    promptComponents: list[str] = Field(min_length=1)
    outputSchema: dict[str, Any]
    context: dict[str, Any]
    maximumOutputTokens: int = Field(gt=0)


class ExtractionResponse(BaseModel):
    modelRunId: uuid.UUID
    output: dict[str, Any]
    latencyMs: int
    inputTokens: int
    outputTokens: int


def load_deployments() -> tuple[Deployment, ...]:
    try:
        configured = json.loads(os.getenv("MODEL_DEPLOYMENTS_JSON", "[]"))
    except json.JSONDecodeError as exc:
        raise RuntimeError("MODEL_DEPLOYMENTS_JSON is invalid") from exc
    if not isinstance(configured, list):
        raise RuntimeError("MODEL_DEPLOYMENTS_JSON must be an array")
    deployments: list[Deployment] = []
    for item in configured:
        if not isinstance(item, dict):
            raise RuntimeError("Every model deployment must be an object")
        api_key_env = item.get("apiKeyEnvironment")
        deployments.append(
            Deployment(
                profile=str(item["profile"]),
                base_url=str(item["baseUrl"]).rstrip("/"),
                runtime_model=str(item["runtimeModel"]),
                api_key=os.getenv(str(api_key_env)) if api_key_env else None,
                timeout_seconds=float(item.get("timeoutSeconds", 120)),
            )
        )
    return tuple(deployments)


DEPLOYMENTS = load_deployments()
app = FastAPI(title="NANObaseAI Local AI Orchestrator", version="1.0.0")


@app.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/health/ready")
def ready() -> dict[str, Any]:
    return {"status": "UP", "configuredDeployments": len(DEPLOYMENTS)}


@app.post("/v1/extractions", response_model=ExtractionResponse)
async def extract(
    request: ExtractionRequest,
    x_correlation_id: str | None = Header(default=None),
) -> ExtractionResponse:
    if request.model != LOGICAL_MODEL:
        raise HTTPException(status_code=422, detail="Unsupported logical model")
    deployment = next(
        (item for item in DEPLOYMENTS if item.profile == request.profile), None
    )
    if deployment is None:
        raise HTTPException(status_code=503, detail="No deployment for requested profile")

    validator = Draft202012Validator(request.outputSchema)
    system_instructions = "\n\n".join(request.promptComponents)
    # Document text remains a JSON value. It is never concatenated into system authority.
    document_context = json.dumps(request.context, ensure_ascii=False, separators=(",", ":"))
    body = {
        "model": deployment.runtime_model,
        "messages": [
            {
                "role": "system",
                "content": (
                    system_instructions
                    + "\nTools, network access and filesystem access are unavailable."
                ),
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "task": "grounded_requirement_extraction",
                        "untrustedDocumentContext": document_context,
                    },
                    ensure_ascii=False,
                ),
            },
        ],
        "temperature": 0,
        "max_tokens": request.maximumOutputTokens,
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": "dynamic_requirement_output",
                "strict": True,
                "schema": request.outputSchema,
            },
        },
    }
    headers = {"Content-Type": "application/json"}
    if deployment.api_key:
        headers["Authorization"] = f"Bearer {deployment.api_key}"
    started = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=deployment.timeout_seconds) as client:
            response = await client.post(
                f"{deployment.base_url}/v1/chat/completions",
                headers=headers,
                json=body,
            )
            response.raise_for_status()
    except httpx.HTTPError as exc:
        LOG.warning(
            "runtime_request_failed correlation_id=%s profile=%s error_type=%s",
            x_correlation_id,
            request.profile,
            type(exc).__name__,
        )
        raise HTTPException(status_code=502, detail="Local model runtime unavailable") from exc

    runtime_response = response.json()
    try:
        content = runtime_response["choices"][0]["message"]["content"]
        output = content if isinstance(content, dict) else json.loads(content)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=502, detail="Runtime returned invalid JSON") from exc
    errors = sorted(validator.iter_errors(output), key=lambda item: list(item.path))
    if errors:
        raise HTTPException(
            status_code=422,
            detail={
                "code": "OUTPUT_SCHEMA_REJECTED",
                "errors": [
                    {"path": list(error.path), "message": error.message}
                    for error in errors[:20]
                ],
            },
        )
    usage = runtime_response.get("usage") or {}
    return ExtractionResponse(
        modelRunId=uuid.uuid4(),
        output=output,
        latencyMs=int((time.monotonic() - started) * 1000),
        inputTokens=int(usage.get("prompt_tokens", 0)),
        outputTokens=int(usage.get("completion_tokens", 0)),
    )
