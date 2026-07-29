import type { TenderDraft } from "@/src/modules/tenders/api";

export type Screen =
  | "dashboard"
  | "projects"
  | "project"
  | "workflows"
  | "pilot-quality"
  | "operations";

export type ProjectHub = "analysis" | "findings" | "changes" | "project";

export type AnalysisStep =
  | "documents"
  | "requirements"
  | "knowledge"
  | "compliance"
  | "risks";

export type ProjectSubNav = "overview" | "activity" | "settings";

export type FindingsFilter = "risks" | "conflicts" | "ambiguities";

export type ProjectTab =
  | "overview"
  | "documents"
  | "requirements"
  | "knowledge"
  | "compliance"
  | "risks"
  | "conflicts"
  | "ambiguities"
  | "changes"
  | "activity"
  | "settings";

export const emptyDraft: TenderDraft = {
  name: "",
  institutionName: "",
  tenderRegistrationNumber: "",
  tenderType: "",
  businessType: "",
  sector: "",
  priority: "NORMAL",
  bidDeadline: "",
  clarificationDeadline: "",
  description: "",
  currency: "TRY",
};

export const processingStatuses = new Set([
  "UPLOADED",
  "VIRUS_SCANNING",
  "CLASSIFYING",
  "QUEUED",
  "PARSING",
  "OCR_PROCESSING",
  "STRUCTURE_DETECTION",
  "INDEXING",
]);

export function compactDraft(draft: TenderDraft): TenderDraft {
  return Object.fromEntries(
    Object.entries(draft).filter(([, value]) => value !== ""),
  ) as TenderDraft;
}

export function formatDate(value?: string) {
  if (!value) return "Belirtilmedi";
  return new Date(`${value}T00:00:00`).toLocaleDateString("tr-TR");
}

export function formatBytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

export function initials(value: string) {
  return (
    value
      .split(/[\s@._-]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join("") || "K"
  );
}

