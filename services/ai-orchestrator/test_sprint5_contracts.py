from __future__ import annotations

import asyncio
import uuid

import httpx
import pytest
from fastapi import HTTPException

import app as orchestrator


def deployment(runtime_model: str = "primary") -> orchestrator.Deployment:
    return orchestrator.Deployment(
        profile="balanced",
        base_url="http://runtime.invalid",
        runtime_model=runtime_model,
        api_key=None,
        timeout_seconds=1,
    )


def request() -> orchestrator.ComplianceEvaluationRequest:
    return orchestrator.ComplianceEvaluationRequest(
        model="nanobase-spec-ai",
        profile="balanced",
        promptComponents=["Return JSON only."],
        outputSchema={"type": "object"},
        requirement={"id": "requirement-1", "text": "At least 500 users"},
        ontologyConcepts=[
            {"code": "COMPLIANT", "metadata": {"positive": True}},
            {"code": "INSUFFICIENT_INFORMATION", "metadata": {"positive": False}},
        ],
        evidence=[
            {"id": "evidence-1", "text": "Supports 1,000 users"},
            {
                "id": "evidence-2",
                "text": "Supports 250 users",
                "contradictionStrength": 0.8,
            },
        ],
        allowedDecisionConcepts=["COMPLIANT", "INSUFFICIENT_INFORMATION"],
        maximumOutputTokens=512,
    )


def output(
    *,
    decision: str = "COMPLIANT",
    supporting: list[str] | None = None,
    contradicting: list[str] | None = None,
) -> dict:
    return {
        "recommendedDecisionConcept": decision,
        "summary": "Grounded result",
        "conditionEvaluations": [
            {
                "conditionId": "condition-1",
                "statusConcept": "SATISFIED",
                "reason": "Evidence supports the requirement",
                "supportingEvidenceIds": (
                    ["evidence-1"] if supporting is None else supporting
                ),
                "contradictingEvidenceIds": (
                    ["evidence-2"] if contradicting is None else contradicting
                ),
            }
        ],
        "missingInformation": [],
        "warnings": [],
        "confidence": 0.9,
        "requiresManualReview": True,
    }


def response(value: dict) -> orchestrator.ExtractionResponse:
    return orchestrator.ExtractionResponse(
        modelRunId=uuid.uuid4(),
        output=value,
        latencyMs=1,
        inputTokens=10,
        outputTokens=10,
    )


def run_evaluation(monkeypatch: pytest.MonkeyPatch, value: dict):
    monkeypatch.setattr(orchestrator, "DEPLOYMENTS", (deployment(),))

    async def fake_call(**_kwargs):
        return response(value)

    monkeypatch.setattr(
        orchestrator, "_structured_runtime_call_with_fallback", fake_call
    )
    return asyncio.run(orchestrator.evaluate_compliance(request(), None))


def test_valid_response_is_accepted(monkeypatch: pytest.MonkeyPatch) -> None:
    result = run_evaluation(monkeypatch, output())
    assert result.output["recommendedDecisionConcept"] == "COMPLIANT"


def test_invalid_evidence_id_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(HTTPException) as error:
        run_evaluation(monkeypatch, output(supporting=["invented-evidence"]))
    assert error.value.detail["code"] == "INVALID_EVIDENCE_ID"


def test_unsupported_decision_concept_is_rejected(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    with pytest.raises(HTTPException) as error:
        run_evaluation(monkeypatch, output(decision="INVENTED_DECISION"))
    assert error.value.detail["code"] == "UNSUPPORTED_DECISION_CONCEPT"


def test_ungrounded_positive_claim_is_rejected(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    with pytest.raises(HTTPException) as error:
        run_evaluation(
            monkeypatch,
            output(supporting=[], contradicting=[]),
        )
    assert error.value.detail["code"] == "UNGROUNDED_POSITIVE_DECISION"


def test_contradiction_omission_is_rejected(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    with pytest.raises(HTTPException) as error:
        run_evaluation(monkeypatch, output(contradicting=[]))
    assert error.value.detail["code"] == "CONTRADICTION_OMISSION"


class FakeResponse:
    def __init__(self, value: dict):
        self.value = value

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return {
            "choices": [{"message": {"content": self.value}}],
            "usage": {},
        }


class FakeClient:
    def __init__(self, value: dict | None = None, failure: Exception | None = None):
        self.value = value
        self.failure = failure
        self.last_json: dict | None = None
        self.last_timeout: float | None = None

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args):
        return None

    async def post(self, *_args, **kwargs):
        self.last_json = kwargs.get("json")
        if self.failure is not None:
            raise self.failure
        return FakeResponse(self.value or {})


def runtime_call(
    monkeypatch: pytest.MonkeyPatch,
    client: FakeClient,
    *,
    deployment_override: orchestrator.Deployment | None = None,
    maximum_output_tokens: int = 64,
):
    captured: dict[str, float] = {}

    def fake_client(**kwargs):
        captured["timeout"] = kwargs.get("timeout")
        client.last_timeout = kwargs.get("timeout")
        return client

    monkeypatch.setattr(orchestrator.httpx, "AsyncClient", fake_client)
    return asyncio.run(
        orchestrator._structured_runtime_call(
            deployment=deployment_override or deployment(),
            prompt_components=["Return JSON."],
            output_schema={
                "type": "object",
                "required": ["requiredField"],
                "properties": {"requiredField": {"type": "string"}},
            },
            schema_name="contract",
            task="test",
            untrusted_context={},
            maximum_output_tokens=maximum_output_tokens,
            correlation_id="test",
        )
    )


def test_schema_failure_is_rejected(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(HTTPException) as error:
        runtime_call(monkeypatch, FakeClient(value={}))
    assert error.value.detail["code"] == "OUTPUT_SCHEMA_REJECTED"


def test_model_timeout_is_mapped_to_gateway_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    timeout = httpx.ReadTimeout("timeout", request=httpx.Request("POST", "http://x"))
    with pytest.raises(HTTPException) as error:
        runtime_call(monkeypatch, FakeClient(failure=timeout))
    assert error.value.status_code == 502


def test_model_unavailable_is_mapped_to_gateway_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    unavailable = httpx.ConnectError(
        "unavailable", request=httpx.Request("POST", "http://x")
    )
    with pytest.raises(HTTPException) as error:
        runtime_call(monkeypatch, FakeClient(failure=unavailable))
    assert error.value.status_code == 502


def test_retry_then_success(monkeypatch: pytest.MonkeyPatch) -> None:
    calls = 0

    async def flaky_call(**_kwargs):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise HTTPException(status_code=502, detail="temporary")
        return response(output())

    monkeypatch.setattr(orchestrator, "_structured_runtime_call", flaky_call)
    monkeypatch.setenv("MODEL_RUNTIME_RETRY_ATTEMPTS", "2")
    result = asyncio.run(
        orchestrator._structured_runtime_call_with_fallback(
            deployments=(deployment(),),
            prompt_components=["Return JSON."],
            output_schema={"type": "object"},
            schema_name="retry",
            task="test",
            untrusted_context={},
            maximum_output_tokens=64,
            correlation_id="retry",
        )
    )
    assert calls == 2
    assert result.output["recommendedDecisionConcept"] == "COMPLIANT"


def test_fallback_model_is_used_after_primary_exhaustion(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    called_models: list[str] = []

    async def fallback_call(**kwargs):
        called_models.append(kwargs["deployment"].runtime_model)
        if kwargs["deployment"].runtime_model == "primary":
            raise HTTPException(status_code=502, detail="primary unavailable")
        return response(output())

    monkeypatch.setattr(orchestrator, "_structured_runtime_call", fallback_call)
    monkeypatch.setenv("MODEL_RUNTIME_RETRY_ATTEMPTS", "1")
    result = asyncio.run(
        orchestrator._structured_runtime_call_with_fallback(
            deployments=(deployment("primary"), deployment("fallback")),
            prompt_components=["Return JSON."],
            output_schema={"type": "object"},
            schema_name="fallback",
            task="test",
            untrusted_context={},
            maximum_output_tokens=64,
            correlation_id="fallback",
        )
    )
    assert called_models == ["primary", "fallback"]
    assert result.output["recommendedDecisionConcept"] == "COMPLIANT"


def test_fast_deployment_applies_reasoning_off_top_p_and_token_cap(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = FakeClient(value={"requiredField": "ok"})
    fast = orchestrator.Deployment(
        profile="FAST",
        base_url="http://runtime.invalid",
        runtime_model="nanobase-fast",
        api_key=None,
        timeout_seconds=300,
        temperature=0.0,
        top_p=0.8,
        reasoning=False,
        default_max_tokens=512,
    )
    runtime_call(
        monkeypatch,
        client,
        deployment_override=fast,
        maximum_output_tokens=1024,
    )
    assert client.last_json is not None
    assert client.last_json["max_tokens"] == 512
    assert client.last_json["temperature"] == 0.0
    assert client.last_json["top_p"] == 0.8
    assert client.last_json["enable_thinking"] is False
    assert client.last_json["chat_template_kwargs"]["enable_thinking"] is False
    assert client.last_timeout == 300


def test_load_deployments_parses_fast_runtime_options(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(
        "MODEL_DEPLOYMENTS_JSON",
        (
            '[{"profile":"FAST","baseUrl":"http://fast:8001",'
            '"runtimeModel":"nanobase-fast","timeoutSeconds":300,'
            '"temperature":0,"topP":0.8,"reasoning":false,"maxTokens":512}]'
        ),
    )
    loaded = orchestrator.load_deployments()
    assert len(loaded) == 1
    assert loaded[0].profile == "FAST"
    assert loaded[0].top_p == 0.8
    assert loaded[0].reasoning is False
    assert loaded[0].default_max_tokens == 512
    assert loaded[0].timeout_seconds == 300
