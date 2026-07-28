import { apiRequest } from "@/src/shared/api";

export type PilotDashboard = {
  generatedAt: string;
  configuration: {
    cards?: string[];
    charts?: string[];
  };
  totalFeedback: number;
  openFeedback: number;
  releaseBlockers: number;
  meanResolutionHours: number;
  regressionCases: number;
  manualReviewRate: number;
  expertCorrectionRate: number;
  satisfactionScore?: number;
  uatStatus: string;
  rootCauseDistribution: Array<{ cause: string; count: number }>;
  qualityTrend: Array<Record<string, unknown>>;
  recurringErrors: Array<Record<string, unknown>>;
};

export type FeedbackCase = {
  id: string;
  feedback_code: string;
  project_id: string;
  pilot_session_id?: string;
  feedbackType: string;
  classification: string;
  severity: string;
  entity_type: string;
  entity_id?: string;
  title: string;
  description?: string;
  expected_behavior?: string;
  actual_behavior?: string;
  status: string;
  assignedTeam?: string;
  reported_by: string;
  reported_at: string;
  resolved_at?: string;
  version: number;
  evidence?: Array<Record<string, unknown>>;
  triage?: Array<Record<string, unknown>>;
};

export type ImprovementCandidate = {
  id: string;
  candidate_code: string;
  candidateType: string;
  root_cause_record_id?: string;
  title: string;
  description?: string;
  targetComponent: string;
  baseline_configuration_snapshot_id: string;
  candidate_configuration_snapshot_id: string;
  expected_improvement_json: Record<string, unknown>;
  risk_assessment_json: Record<string, unknown>;
  status: string;
  experiments?: Array<Record<string, unknown>>;
  updated_at: string;
};

export type DynamicConcept = {
  id: string;
  catalogCode: string;
  code: string;
  name: string;
  description?: string;
  metadata: Record<string, unknown>;
  sortOrder: number;
};

export const pilotApi = {
  dashboard: (token: string) =>
    apiRequest<PilotDashboard>("/api/v1/pilot-quality-dashboard", token),
  feedback: (token: string) =>
    apiRequest<FeedbackCase[]>("/api/v1/feedback", token),
  feedbackCase: (token: string, id: string) =>
    apiRequest<FeedbackCase>(`/api/v1/feedback/${id}`, token),
  feedbackHistory: (token: string, id: string) =>
    apiRequest<Array<Record<string, unknown>>>(
      `/api/v1/feedback/${id}/history`,
      token,
    ),
  concepts: (token: string, catalogCode: string) =>
    apiRequest<DynamicConcept[]>(
      `/api/v1/pilot-concepts/${catalogCode}`,
      token,
    ),
  triage: (
    token: string,
    id: string,
    body: {
      rootCauseCode: string;
      secondaryCauseCodes: string[];
      reproducibilityCode: string;
      affectedScope: Record<string, unknown>;
      impactScore: number;
      frequencyScore: number;
      analysisSummary: string;
      analysisSignals: Record<string, unknown>;
    },
  ) => apiRequest<FeedbackCase>(`/api/v1/feedback/${id}/triage`, token, {
    method: "POST",
    body: JSON.stringify(body),
  }),
  candidates: (token: string) =>
    apiRequest<ImprovementCandidate[]>("/api/v1/improvement-candidates", token),
  candidate: (token: string, id: string) =>
    apiRequest<ImprovementCandidate>(
      `/api/v1/improvement-candidates/${id}`,
      token,
    ),
  shadow: (token: string, id: string) =>
    apiRequest<Record<string, unknown>>(
      `/api/v1/improvement-candidates/${id}/shadow`,
      token,
      { method: "POST" },
    ),
  canary: (token: string, id: string, trafficPercentage: number) =>
    apiRequest<Record<string, unknown>>(
      `/api/v1/improvement-candidates/${id}/canary`,
      token,
      {
        method: "POST",
        body: JSON.stringify({ trafficPercentage }),
      },
    ),
  activate: (token: string, id: string) =>
    apiRequest<ImprovementCandidate>(
      `/api/v1/improvement-candidates/${id}/activate`,
      token,
      { method: "POST" },
    ),
  reject: (token: string, id: string) =>
    apiRequest<ImprovementCandidate>(
      `/api/v1/improvement-candidates/${id}/reject`,
      token,
      { method: "POST" },
    ),
};
