import type { AnalysisStep } from "@/src/lib/portal-utils";
import type { ProjectDocument } from "@/src/modules/documents/api";

export type StepCompletion = {
  documents: boolean;
  requirements: boolean;
  knowledge: boolean;
  compliance: boolean;
  risks: boolean;
};

export type StepStatus = "locked" | "current" | "done" | "available";

export type PipelineStepDef = {
  id: AnalysisStep;
  label: string;
  shortLabel: string;
  description: string;
};

export const ANALYSIS_STEPS: PipelineStepDef[] = [
  {
    id: "documents",
    label: "Dokümanlar",
    shortLabel: "Doküman",
    description: "İhale dokümanlarını yükleyin ve işleyin",
  },
  {
    id: "requirements",
    label: "Gereksinimler",
    shortLabel: "Gereksinim",
    description: "Hazır dokümandan gereksinim matrisini çıkarın",
  },
  {
    id: "knowledge",
    label: "Firma ve ürünler",
    shortLabel: "Bilgi",
    description: "Firma, ürün ve yetenek bilgisini çıkarın",
  },
  {
    id: "compliance",
    label: "Uygunluk",
    shortLabel: "Uygunluk",
    description: "Gereksinimlere karşı uygunluk değerlendirmesi",
  },
  {
    id: "risks",
    label: "Risk analizi",
    shortLabel: "Risk",
    description: "Risk, çelişki ve belirsizlik adaylarını üretin",
  },
];

export const PROJECT_HUBS = [
  { id: "analysis" as const, label: "Analiz" },
  { id: "findings" as const, label: "Bulgular" },
  { id: "changes" as const, label: "Değişiklik" },
  { id: "project" as const, label: "Proje" },
];

export const EXPERT_TABS = [
  ["overview", "Genel bakış"],
  ["documents", "Dokümanlar"],
  ["requirements", "Gereksinim matrisi"],
  ["knowledge", "Firma ve ürünler"],
  ["company-fit", "Firma uygunluk"],
  ["compliance", "Uygunluk"],
  ["risks", "Risk merkezi"],
  ["conflicts", "Çelişkiler"],
  ["ambiguities", "Belirsizlikler"],
  ["changes", "Değişiklik ve etki"],
  ["activity", "Aktivite geçmişi"],
  ["settings", "Ayarlar"],
] as const;

export function isDocumentsComplete(documents: ProjectDocument[]): boolean {
  return documents.some(
    (document) => document.status === "READY" && document.includedInAnalysis,
  );
}

export function emptyCompletion(): StepCompletion {
  return {
    documents: false,
    requirements: false,
    knowledge: false,
    compliance: false,
    risks: false,
  };
}

export function completionFromCounts(input: {
  documentsReady: boolean;
  requirementCount: number | null;
  entityCount: number | null;
  evaluationCount: number | null;
  riskCount: number | null;
}): StepCompletion {
  return {
    documents: input.documentsReady,
    requirements: (input.requirementCount ?? 0) > 0,
    knowledge: (input.entityCount ?? 0) > 0,
    compliance: (input.evaluationCount ?? 0) > 0,
    risks: (input.riskCount ?? 0) > 0,
  };
}

export function stepIndex(step: AnalysisStep): number {
  return ANALYSIS_STEPS.findIndex((item) => item.id === step);
}

export function recommendedStep(completion: StepCompletion): AnalysisStep {
  for (const step of ANALYSIS_STEPS) {
    if (!completion[step.id]) return step.id;
  }
  return "risks";
}

export function isStepReachable(
  step: AnalysisStep,
  completion: StepCompletion,
): boolean {
  const index = stepIndex(step);
  if (index <= 0) return true;
  for (let i = 0; i < index; i += 1) {
    const prior = ANALYSIS_STEPS[i];
    if (!prior || !completion[prior.id]) return false;
  }
  return true;
}

export function resolveStepStatus(
  step: AnalysisStep,
  current: AnalysisStep,
  completion: StepCompletion,
): StepStatus {
  if (step === current) return "current";
  if (completion[step]) return "done";
  if (isStepReachable(step, completion)) return "available";
  return "locked";
}

export function previousStep(step: AnalysisStep): AnalysisStep | null {
  const index = stepIndex(step);
  if (index <= 0) return null;
  return ANALYSIS_STEPS[index - 1]?.id ?? null;
}

export function nextStep(step: AnalysisStep): AnalysisStep | null {
  const index = stepIndex(step);
  if (index < 0 || index >= ANALYSIS_STEPS.length - 1) return null;
  return ANALYSIS_STEPS[index + 1]?.id ?? null;
}
