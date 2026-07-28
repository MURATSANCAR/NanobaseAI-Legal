import { apiRequest } from "@/src/shared/api";
import type { ConceptOption, EvidenceFragment } from "@/src/modules/knowledge/api";

export type ComplianceJob = {
  id: string;
  project_id: string;
  status: string;
  total_requirement_count: number;
  processed_requirement_count: number;
  completed_count: number;
  manual_review_count: number;
  failed_count: number;
};

export type ComplianceEvaluation = {
  id: string;
  requirementId: string;
  requirementCode: string;
  requirementText: string;
  requirementConcept?: string;
  targetScope: string;
  suggestedDecision: string;
  finalDecision?: string;
  combinedConfidence: number;
  groundingStatus: string;
  reviewStatus: string;
  evidenceCount: number;
  contradictionCount: number;
};

export type ComplianceEvidence = EvidenceFragment & {
  evidence_fragment_id: string;
  evidence_claim_id?: string;
  relation_role: string;
  relevance_score: number;
  validity_score: number;
  support_strength: number;
  contradiction_strength: number;
  selected_by_type: string;
  claim_text?: string;
};

export type ComplianceEvaluationDetail = Record<string, unknown> & {
  id: string;
  requirement_id: string;
  requirement_code: string;
  requirement_text: string;
  suggested_decision: string;
  final_decision?: string;
  comparison_summary_json: Record<string, unknown> | string;
  combined_confidence: number;
  grounding_status: string;
  review_status: string;
  evidence: ComplianceEvidence[];
  history: Array<Record<string, unknown>>;
};

export type ComplianceColumn = {
  key: string;
  label: string;
  type: string;
  applicabilityRule?: Record<string, unknown>;
};

export const complianceApi = {
  start: (token: string, projectId: string) =>
    apiRequest<ComplianceJob>(
      `/api/v1/tenders/${projectId}/compliance-analyses`,
      token,
      { method: "POST" },
    ),
  job: (token: string, jobId: string) =>
    apiRequest<ComplianceJob>(`/api/v1/compliance-analyses/${jobId}`, token),
  evaluations: (token: string, projectId: string) =>
    apiRequest<ComplianceEvaluation[]>(
      `/api/v1/tenders/${projectId}/compliance-evaluations`,
      token,
    ),
  evaluation: (token: string, id: string) =>
    apiRequest<ComplianceEvaluationDetail>(
      `/api/v1/compliance-evaluations/${id}`,
      token,
    ),
  matrix: (token: string) =>
    apiRequest<{ columns: ComplianceColumn[] }>(
      "/api/v1/ui-configurations/compliance-matrix",
      token,
    ),
  decisions: (token: string) =>
    apiRequest<ConceptOption[]>(
      "/api/v1/ui-configurations/compliance-decisions",
      token,
    ),
  changeTypes: (token: string) =>
    apiRequest<ConceptOption[]>("/api/v1/ui-configurations/change-types", token),
  review: (
    token: string,
    id: string,
    input: {
      finalDecisionConceptId: string;
      changeTypeConceptId: string;
      reason: string;
    },
  ) =>
    apiRequest<ComplianceEvaluationDetail>(
      `/api/v1/compliance-evaluations/${id}/review`,
      token,
      { method: "POST", body: JSON.stringify(input) },
    ),
};
