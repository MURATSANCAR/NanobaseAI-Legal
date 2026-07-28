from __future__ import annotations

import json
from pathlib import Path
from statistics import mean


def binary_metrics(cases: list[dict], task: str) -> dict[str, float | None]:
    selected = [case for case in cases if case["task"] == task]
    true_positive = sum(case["expected"] and case["predicted"] for case in selected)
    false_positive = sum(not case["expected"] and case["predicted"] for case in selected)
    false_negative = sum(case["expected"] and not case["predicted"] for case in selected)
    precision = (
        true_positive / (true_positive + false_positive)
        if true_positive + false_positive
        else None
    )
    recall = (
        true_positive / (true_positive + false_negative)
        if true_positive + false_negative
        else None
    )
    return {"precision": precision, "recall": recall}


def accuracy(cases: list[dict], key: str) -> float | None:
    values = [bool(case[key]) for case in cases if key in case]
    return mean(values) if values else None


def main() -> None:
    cases = json.loads(
        Path(__file__).with_name("sprint6_cases.json").read_text(encoding="utf-8")
    )
    calibration = [
        (float(case["probability"]) - float(case["outcome"])) ** 2
        for case in cases
        if "probability" in case and "outcome" in case
    ]
    result = {
        "datasetType": "contract-golden",
        "caseCount": len(cases),
        "risk": binary_metrics(cases, "risk"),
        "conflict": binary_metrics(cases, "conflict"),
        "ambiguity": binary_metrics(cases, "ambiguity"),
        "severityAccuracy": accuracy(cases, "severityCorrect"),
        "probabilityBrierScore": mean(calibration) if calibration else None,
        "impactAccuracy": accuracy(cases, "impactCorrect"),
        "sourceGrounding": accuracy(cases, "grounded"),
        "authorityDecisionAccuracy": accuracy(cases, "authorityCorrect"),
        "changeMatchingAccuracy": accuracy(cases, "changeCorrect"),
        "stalenessDetectionAccuracy": accuracy(cases, "stalenessCorrect"),
        "manualReviewRate": mean(bool(case["manualReview"]) for case in cases),
        "llmCallRate": mean(bool(case["llmCalled"]) for case in cases),
        "deterministicResolutionRate": mean(not bool(case["llmCalled"]) for case in cases),
        "tokenUsage": sum(int(case["tokens"]) for case in cases),
        "meanAnalysisMs": mean(float(case["durationMs"]) for case in cases),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
