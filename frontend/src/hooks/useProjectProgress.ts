"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  completionFromCounts,
  emptyCompletion,
  isDocumentsComplete,
  recommendedStep,
  type StepCompletion,
} from "@/src/lib/analysis-pipeline";
import type { AnalysisStep } from "@/src/lib/portal-utils";
import { complianceApi } from "@/src/modules/compliance/api";
import type { ProjectDocument } from "@/src/modules/documents/api";
import { knowledgeApi } from "@/src/modules/knowledge/api";
import { requirementApi } from "@/src/modules/requirements/api";
import { riskApi } from "@/src/modules/risks/api";

export type ProjectProgress = {
  completion: StepCompletion;
  recommended: AnalysisStep;
  loading: boolean;
  refresh: () => Promise<void>;
};

async function safeCount(loader: () => Promise<unknown[]>): Promise<number | null> {
  try {
    const items = await loader();
    return items.length;
  } catch {
    return null;
  }
}

export function useProjectProgress(
  token: string,
  projectId: string | undefined,
  documents: ProjectDocument[],
): ProjectProgress {
  const [completion, setCompletion] = useState<StepCompletion>(emptyCompletion);
  const [loading, setLoading] = useState(false);

  const documentsReady = useMemo(
    () => isDocumentsComplete(documents),
    [documents],
  );

  const refresh = useCallback(async () => {
    if (!token || !projectId) {
      setCompletion(emptyCompletion());
      return;
    }
    setLoading(true);
    try {
      const [requirementCount, entityCount, evaluationCount, riskCount] =
        await Promise.all([
          safeCount(async () => {
            const page = await requirementApi.list(token, projectId);
            return page.content;
          }),
          safeCount(() => knowledgeApi.entities(token)),
          safeCount(() => complianceApi.evaluations(token, projectId)),
          safeCount(() => riskApi.list(token, projectId)),
        ]);
      setCompletion(
        completionFromCounts({
          documentsReady,
          requirementCount,
          entityCount,
          evaluationCount,
          riskCount,
        }),
      );
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

  return {
    completion,
    recommended: recommendedStep(completion),
    loading,
    refresh,
  };
}
