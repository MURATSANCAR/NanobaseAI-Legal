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


class KnowledgeExtractionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str
    profile: str
    promptComponents: list[str] = Field(min_length=1)
    outputSchema: dict[str, Any]
    ontologyConcepts: list[dict[str, Any]]
    evidenceFragments: list[dict[str, Any]] = Field(min_length=1)
    maximumOutputTokens: int = Field(gt=0)


class ComplianceEvaluationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str
    profile: str
    promptComponents: list[str] = Field(min_length=1)
    outputSchema: dict[str, Any]
    requirement: dict[str, Any]
    ontologyConcepts: list[dict[str, Any]]
    evidence: list[dict[str, Any]]
    allowedDecisionConcepts: list[str] = Field(min_length=1)
    maximumOutputTokens: int = Field(gt=0)


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


async def _structured_runtime_call(
    *,
    deployment: Deployment,
    prompt_components: list[str],
    output_schema: dict[str, Any],
    schema_name: str,
    task: str,
    untrusted_context: dict[str, Any],
    maximum_output_tokens: int,
    correlation_id: str | None,
) -> ExtractionResponse:
    validator = Draft202012Validator(output_schema)
    body = {
        "model": deployment.runtime_model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "\n\n".join(prompt_components)
                    + "\nTools, network access and filesystem access are unavailable."
                ),
            },
            {
                "role": "user",
                "content": json.dumps(
                    {
                        "task": task,
                        "untrustedContext": untrusted_context,
                    },
                    ensure_ascii=False,
                    separators=(",", ":"),
                ),
            },
        ],
        "temperature": 0,
        "max_tokens": maximum_output_tokens,
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": schema_name,
                "strict": True,
                "schema": output_schema,
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
            correlation_id,
            deployment.profile,
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


def _deployment(model: str, profile: str) -> Deployment:
    if model != LOGICAL_MODEL:
        raise HTTPException(status_code=422, detail="Unsupported logical model")
    deployment = next((item for item in DEPLOYMENTS if item.profile == profile), None)
    if deployment is None:
        raise HTTPException(status_code=503, detail="No deployment for requested profile")
    return deployment


def _source_fragment_ids(source_fragments: Any) -> set[str]:
    if not isinstance(source_fragments, list):
        return set()
    values: set[str] = set()
    for source in source_fragments:
        if isinstance(source, str):
            values.add(source)
        elif isinstance(source, dict) and isinstance(source.get("fragmentId"), str):
            values.add(source["fragmentId"])
    return values


@app.post("/v1/knowledge-extractions", response_model=ExtractionResponse)
async def extract_knowledge(
    request: KnowledgeExtractionRequest,
    x_correlation_id: str | None = Header(default=None),
) -> ExtractionResponse:
    deployment = _deployment(request.model, request.profile)
    response = await _structured_runtime_call(
        deployment=deployment,
        prompt_components=request.promptComponents,
        output_schema=request.outputSchema,
        schema_name="dynamic_knowledge_output",
        task="grounded_dynamic_knowledge_extraction",
        untrusted_context={
            "ontologyConcepts": request.ontologyConcepts,
            "evidenceFragments": request.evidenceFragments,
        },
        maximum_output_tokens=request.maximumOutputTokens,
        correlation_id=x_correlation_id,
    )
    output = response.output
    allowed_fragments = {
        str(item.get("fragmentId"))
        for item in request.evidenceFragments
        if item.get("fragmentId") is not None
    }
    known_concepts = {
        str(item.get("code"))
        for item in request.ontologyConcepts
        if item.get("code") is not None
    }
    violations: list[str] = []
    for entity in output.get("entities", []):
        entity_sources = _source_fragment_ids(entity.get("sourceFragments"))
        if not entity_sources:
            violations.append("Entity has no source fragment")
        if str(entity.get("entityTypeConcept")) not in known_concepts:
            violations.append("Entity uses an unknown concept")
        for attribute in entity.get("attributes", []):
            if not _source_fragment_ids(attribute.get("sourceFragments")):
                violations.append("Attribute has no source fragment")
            if str(attribute.get("attributeConcept")) not in known_concepts:
                violations.append("Attribute uses an unknown concept")
    for relation in output.get("relations", []):
        if not _source_fragment_ids(relation.get("sourceFragments")):
            violations.append("Relation has no source fragment")
        if str(relation.get("relationConcept")) not in known_concepts:
            violations.append("Relation uses an unknown concept")
    for capability in output.get("capabilities", []):
        if not _source_fragment_ids(capability.get("sourceFragments")):
            violations.append("Capability has no source fragment")
        if str(capability.get("capabilityConcept")) not in known_concepts:
            violations.append("Capability uses an unknown concept")
    returned_fragments: set[str] = set()
    for item_type in ("entities", "relations", "capabilities"):
        for item in output.get(item_type, []):
            returned_fragments.update(_source_fragment_ids(item.get("sourceFragments")))
            for attribute in item.get("attributes", []) if isinstance(item, dict) else []:
                if isinstance(attribute, dict):
                    returned_fragments.update(
                        _source_fragment_ids(attribute.get("sourceFragments"))
                    )
    if not returned_fragments.issubset(allowed_fragments):
        violations.append("Output contains an evidence fragment ID absent from the request")
    if violations:
        raise HTTPException(
            status_code=422,
            detail={"code": "UNGROUNDED_KNOWLEDGE_OUTPUT", "errors": violations[:20]},
        )
    return response


def _collect_evidence_ids(value: Any) -> set[str]:
    collected: set[str] = set()
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {"supportingEvidenceIds", "contradictingEvidenceIds"}:
                if isinstance(child, list):
                    collected.update(str(item) for item in child)
            else:
                collected.update(_collect_evidence_ids(child))
    elif isinstance(value, list):
        for child in value:
            collected.update(_collect_evidence_ids(child))
    return collected


@app.post("/v1/compliance-evaluations", response_model=ExtractionResponse)
async def evaluate_compliance(
    request: ComplianceEvaluationRequest,
    x_correlation_id: str | None = Header(default=None),
) -> ExtractionResponse:
    deployment = _deployment(request.model, request.profile)
    response = await _structured_runtime_call(
        deployment=deployment,
        prompt_components=request.promptComponents,
        output_schema=request.outputSchema,
        schema_name="dynamic_compliance_output",
        task="evidence_constrained_semantic_compliance_evaluation",
        untrusted_context={
            "requirement": request.requirement,
            "ontologyConcepts": request.ontologyConcepts,
            "selectedEvidence": request.evidence,
            "allowedDecisionConcepts": request.allowedDecisionConcepts,
        },
        maximum_output_tokens=request.maximumOutputTokens,
        correlation_id=x_correlation_id,
    )
    output = response.output
    if output.get("recommendedDecisionConcept") not in request.allowedDecisionConcepts:
        raise HTTPException(
            status_code=422,
            detail={"code": "UNSUPPORTED_DECISION_CONCEPT"},
        )
    allowed_evidence = {
        str(item.get("id")) for item in request.evidence if item.get("id") is not None
    }
    used_evidence = _collect_evidence_ids(output)
    if not used_evidence.issubset(allowed_evidence):
        raise HTTPException(
            status_code=422,
            detail={"code": "INVALID_EVIDENCE_ID"},
        )
    contradictions = {
        str(item.get("id"))
        for item in request.evidence
        if float(item.get("contradictionStrength", 0)) > 0
    }
    mentioned_contradictions: set[str] = set()
    for evaluation in output.get("conditionEvaluations", []):
        mentioned_contradictions.update(
            str(item) for item in evaluation.get("contradictingEvidenceIds", [])
        )
    if contradictions and not contradictions.issubset(mentioned_contradictions):
        raise HTTPException(
            status_code=422,
            detail={"code": "CONTRADICTION_OMISSION"},
        )
    return response
