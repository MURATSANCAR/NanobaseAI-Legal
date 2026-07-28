from __future__ import annotations

import pytest
from fastapi import HTTPException

from app import GovernedAnalysisRequest, _validate_governed_output


def request() -> GovernedAnalysisRequest:
    return GovernedAnalysisRequest(
        model="nanobase-spec-ai",
        profile="balanced",
        promptComponents=["Return JSON only."],
        outputSchema={"type": "object"},
        ontologyConcepts=[
            {"code": "DURATION_CONFLICT"},
            {"code": "REQUEST_CLARIFICATION"},
        ],
        sources=[
            {"id": "left-source", "text": "36 months"},
            {"id": "right-source", "text": "24 months"},
        ],
        policy={"onUnknown": "MANUAL_REVIEW"},
        context={},
        maximumOutputTokens=512,
    )


def test_grounded_conflict_is_accepted() -> None:
    _validate_governed_output(
        request=request(),
        task="conflict_analysis",
        output={
            "isConflict": True,
            "conflictConcept": "DURATION_CONFLICT",
            "recommendedActionConcept": "REQUEST_CLARIFICATION",
            "supportingSourceIds": ["left-source", "right-source"],
            "authorityAssessment": {"preferredSourceId": None},
        },
    )


def test_unknown_source_is_rejected() -> None:
    with pytest.raises(HTTPException) as error:
        _validate_governed_output(
            request=request(),
            task="conflict_analysis",
            output={
                "isConflict": True,
                "conflictConcept": "DURATION_CONFLICT",
                "supportingSourceIds": ["left-source", "invented-source"],
            },
        )
    assert error.value.detail["code"] == "UNKNOWN_SOURCE_ID"


def test_unknown_concept_is_rejected() -> None:
    with pytest.raises(HTTPException) as error:
        _validate_governed_output(
            request=request(),
            task="conflict_analysis",
            output={
                "isConflict": True,
                "conflictConcept": "INVENTED_CONFLICT",
                "supportingSourceIds": ["left-source", "right-source"],
            },
        )
    assert error.value.detail["code"] == "UNKNOWN_ONTOLOGY_CONCEPT"


def test_authority_assumption_without_policy_rule_is_rejected() -> None:
    with pytest.raises(HTTPException) as error:
        _validate_governed_output(
            request=request(),
            task="conflict_analysis",
            output={
                "isConflict": True,
                "conflictConcept": "DURATION_CONFLICT",
                "supportingSourceIds": ["left-source", "right-source"],
                "authorityAssessment": {"preferredSourceId": "left-source"},
            },
        )
    assert error.value.detail["code"] == "INVALID_AUTHORITY_ASSUMPTION"


def test_conflict_requires_two_grounded_sources() -> None:
    with pytest.raises(HTTPException) as error:
        _validate_governed_output(
            request=request(),
            task="conflict_analysis",
            output={
                "isConflict": True,
                "conflictConcept": "DURATION_CONFLICT",
                "supportingSourceIds": ["left-source"],
            },
        )
    assert error.value.detail["code"] == "UNGROUNDED_CONFLICT"
