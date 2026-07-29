"use client";

import { ArrowLeft, ArrowRight, ListChecks } from "lucide-react";
import {
  isStepReachable,
  nextStep,
  previousStep,
  type StepCompletion,
} from "@/src/lib/analysis-pipeline";
import type { AnalysisStep } from "@/src/lib/portal-utils";

export function PipelineNav({
  step,
  completion,
  onStep,
  onGoFindings,
}: {
  step: AnalysisStep;
  completion: StepCompletion;
  onStep: (step: AnalysisStep) => void;
  onGoFindings: () => void;
}) {
  const prev = previousStep(step);
  const next = nextStep(step);
  const canAdvance =
    next != null && completion[step] && isStepReachable(next, completion);
  const isLast = next == null;

  return (
    <div className="pipeline-footer">
      <button
        type="button"
        className="btn-secondary"
        disabled={!prev}
        onClick={() => {
          if (prev) onStep(prev);
        }}
      >
        <ArrowLeft />
        Geri
      </button>
      <p className="pipeline-footer-hint">
        {isLast
          ? completion.risks
            ? "Risk analizi tamam. Bulguları inceleyebilirsiniz."
            : "Risk analizini başlatın, ardından bulgulara geçin."
          : completion[step]
            ? "Adım tamam. Sonrakine geçebilirsiniz."
            : "Bu adımı tamamladıktan sonra devam edin."}
      </p>
      {isLast ? (
        <button type="button" className="btn-primary" onClick={onGoFindings}>
          <ListChecks />
          Bulgulara git
        </button>
      ) : (
        <button
          type="button"
          className="btn-primary"
          disabled={!canAdvance}
          onClick={() => {
            if (next && canAdvance) onStep(next);
          }}
        >
          Devam et
          <ArrowRight />
        </button>
      )}
    </div>
  );
}
