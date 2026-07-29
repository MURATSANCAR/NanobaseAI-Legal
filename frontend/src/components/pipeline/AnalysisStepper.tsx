"use client";

import { Check, Lock } from "lucide-react";
import {
  ANALYSIS_STEPS,
  resolveStepStatus,
  type StepCompletion,
} from "@/src/lib/analysis-pipeline";
import type { AnalysisStep } from "@/src/lib/portal-utils";
import { cn } from "@/src/lib/cn";

export function AnalysisStepper({
  current,
  completion,
  onSelect,
}: {
  current: AnalysisStep;
  completion: StepCompletion;
  onSelect: (step: AnalysisStep) => void;
}) {
  return (
    <nav className="analysis-stepper" aria-label="Analiz adımları">
      {ANALYSIS_STEPS.map((step, index) => {
        const status = resolveStepStatus(step.id, current, completion);
        const locked = status === "locked";
        return (
          <button
            key={step.id}
            type="button"
            className={cn("analysis-step", status)}
            disabled={locked}
            onClick={() => {
              if (!locked) onSelect(step.id);
            }}
            title={
              locked
                ? "Önceki adımı tamamlayın"
                : `${step.label}: ${step.description}`
            }
          >
            <span className="analysis-step-index" aria-hidden>
              {status === "done" ? (
                <Check className="h-3.5 w-3.5" />
              ) : locked ? (
                <Lock className="h-3 w-3" />
              ) : (
                index + 1
              )}
            </span>
            <span className="analysis-step-copy">
              <b>{step.label}</b>
              <small className="analysis-step-desc">{step.description}</small>
            </span>
          </button>
        );
      })}
    </nav>
  );
}
