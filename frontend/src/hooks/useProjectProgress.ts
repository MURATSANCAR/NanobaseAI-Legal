"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  emptyCompletion,
  isDocumentsComplete,
  recommendedStep,
  type StepCompletion,
} from "@/src/lib/analysis-pipeline";
import type { AnalysisStep } from "@/src/lib/portal-utils";
import type { ProjectDocument } from "@/src/modules/documents/api";
import { tenderApi } from "@/src/modules/tenders/api";

export type ProjectProgress = {
  completion: StepCompletion;
  recommended: AnalysisStep;
  loading: boolean;
  refresh: () => Promise<void>;
};

const ANALYSIS_STEPS: AnalysisStep[] = [
  "documents",
  "requirements",
  "knowledge",
  "compliance",
  "risks",
];

function asAnalysisStep(value: string | undefined): AnalysisStep | null {
  return ANALYSIS_STEPS.includes(value as AnalysisStep)
    ? (value as AnalysisStep)
    : null;
}

export function useProjectProgress(
  token: string,
  projectId: string | undefined,
  documents: ProjectDocument[],
): ProjectProgress {
  const [completion, setCompletion] = useState<StepCompletion>(emptyCompletion);
  const [serverRecommended, setServerRecommended] = useState<AnalysisStep | null>(
    null,
  );
  const [loading, setLoading] = useState(false);

  const documentsReady = useMemo(
    () => isDocumentsComplete(documents),
    [documents],
  );

  const refresh = useCallback(async () => {
    if (!token || !projectId) {
      setCompletion(emptyCompletion());
      setServerRecommended(null);
      return;
    }
    setLoading(true);
    try {
      const progress = await tenderApi.analysisProgress(token, projectId);
      setCompletion({
        documents: progress.documents || documentsReady,
        requirements: progress.requirements,
        knowledge: progress.knowledge,
        compliance: progress.compliance,
        risks: progress.risks,
      });
      setServerRecommended(asAnalysisStep(progress.recommendedStep));
    } catch {
      // Keep last known completion; documents flag still updates locally.
      setCompletion((current) => ({
        ...current,
        documents: documentsReady || current.documents,
      }));
    } finally {
      setLoading(false);
    }
  }, [documentsReady, projectId, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void refresh();
    }, 0);
    return () => window.clearTimeout(timer);
  }, [refresh]);

  useEffect(() => {
    setCompletion((current) =>
      current.documents === documentsReady
        ? current
        : { ...current, documents: documentsReady },
    );
  }, [documentsReady]);

  const recommended =
    serverRecommended && !completion[serverRecommended]
      ? serverRecommended
      : recommendedStep(completion);

  return {
    completion,
    recommended,
    loading,
    refresh,
  };
}
