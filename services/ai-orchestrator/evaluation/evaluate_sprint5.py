from __future__ import annotations

import json
from pathlib import Path
from statistics import mean


def main() -> None:
    cases = json.loads(
        Path(__file__).with_name("sprint5_cases.json").read_text(encoding="utf-8")
    )
    result = {
        "datasetType": "contract-golden",
        "caseCount": len(cases),
        "decisionAccuracy": mean(
            case["expected"] == case["predicted"] for case in cases
        ),
        "groundingCoverage": mean(bool(case["grounded"]) for case in cases),
        "manualReviewRate": mean(bool(case["manualReview"]) for case in cases),
        "deterministicResolutionRate": mean(
            not bool(case["llmCalled"]) for case in cases
        ),
        "llmCallRate": mean(bool(case["llmCalled"]) for case in cases),
        "tokenUsage": sum(int(case["tokens"]) for case in cases),
        "meanAnalysisMs": mean(float(case["durationMs"]) for case in cases),
        "categories": sorted(case["category"] for case in cases),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
