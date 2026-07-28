import { apiRequest } from "@/src/shared/api";

export type DynamicColumn = {
  key: string;
  label: string;
  type: string;
  visible: boolean;
};

export type RiskRecord = {
  id: string;
  riskCode: string;
  title: string;
  description: string;
  riskConcept: string;
  severity?: string;
  probabilityScore?: number;
  impactScore?: number;
  exposureScore?: number;
  confidence: number;
  reviewStatus: string;
  status: string;
  ownerUserId?: string;
  dueDate?: string;
  stalenessStatus?: string;
  sources?: RiskSource[];
  factors?: Record<string, unknown>[];
  history?: Record<string, unknown>[];
  propagation?: Record<string, unknown>[];
  mitigations?: Record<string, unknown>[];
};

export type RiskSource = {
  id: string;
  sourceType?: string;
  sourceId?: string;
  documentId: string;
  documentVersionId: string;
  clauseId?: string;
  requirementId?: string;
  pageNumber?: number;
  sourceText: string;
  boundingBoxes?: string;
  side?: string;
};

export type RiskAnalysisJob = {
  id: string;
  projectId: string;
  status: string;
  totalCandidateCount: number;
  processedCandidateCount: number;
  riskCount: number;
  ambiguityCount: number;
  conflictCount: number;
  manualReviewCount: number;
  failedCount: number;
};

export type AmbiguityFinding = {
  id: string;
  entityType: string;
  entityId: string;
  concept: string;
  description: string;
  confidence: number;
  severity?: string;
  reviewStatus: string;
  sources?: RiskSource[];
  interpretations?: Record<string, unknown>[];
};

export type ConflictRecord = {
  id: string;
  conflictCode: string;
  concept: string;
  leftEntityType: string;
  leftEntityId: string;
  rightEntityType: string;
  rightEntityId: string;
  comparisonStrategyCode: string;
  description: string;
  confidence: number;
  severity?: string;
  reviewStatus: string;
  status: string;
  sources?: RiskSource[];
  factors?: Record<string, unknown>[];
  history?: Record<string, unknown>[];
};

export type ChangeItem = {
  id: string;
  changeType: string;
  changeTypeConceptId: string;
  baseClauseId?: string;
  targetClauseId?: string;
  similarityScore: number;
  confidence: number;
  reviewStatus: string;
  attributes?: string;
};

export type ChangeSet = {
  id: string;
  projectId: string;
  baseDocumentVersionId: string;
  targetDocumentVersionId: string;
  status: string;
  summary: string;
  items: ChangeItem[];
};

export type ImpactAnalysis = {
  id: string;
  projectId: string;
  changeSetId: string;
  status: string;
  totalItemCount: number;
  processedItemCount: number;
  affectedRequirementCount: number;
  affectedEvaluationCount: number;
  affectedRiskCount: number;
  affectedReportCount: number;
  affectedEntities: Array<{
    id: string;
    entityType: string;
    entityId: string;
    impactConcept: string;
    reasonCodes: string;
    confidence: number;
  }>;
};

export const riskApi = {
  grid: (token: string) =>
    apiRequest<{ columns: DynamicColumn[] }>(
      "/api/v1/ui-configurations/risk-grid",
      token,
    ),
  list: (token: string, projectId: string) =>
    apiRequest<RiskRecord[]>(`/api/v1/tenders/${projectId}/risks`, token),
  get: (token: string, id: string) =>
    apiRequest<RiskRecord>(`/api/v1/risks/${id}`, token),
  start: (token: string, projectId: string) =>
    apiRequest<RiskAnalysisJob>(
      `/api/v1/tenders/${projectId}/risk-analyses`,
      token,
      { method: "POST" },
    ),
  job: (token: string, id: string) =>
    apiRequest<RiskAnalysisJob>(`/api/v1/risk-analyses/${id}`, token),
  review: (token: string, id: string, reviewStatus: string) =>
    apiRequest<RiskRecord>(`/api/v1/risks/${id}/review`, token, {
      method: "POST",
      body: JSON.stringify({
        reviewStatus,
        feedbackType: reviewStatus === "APPROVED" ? "RISK_CONFIRMED" : "FALSE_RISK",
        reason: "Risk merkezi uzman incelemesi",
        approvedForLearning: false,
      }),
    }),
  ambiguities: (token: string, projectId: string) =>
    apiRequest<AmbiguityFinding[]>(
      `/api/v1/tenders/${projectId}/ambiguities`,
      token,
    ),
  ambiguity: (token: string, id: string) =>
    apiRequest<AmbiguityFinding>(`/api/v1/ambiguities/${id}`, token),
  reviewAmbiguity: (token: string, id: string, reviewStatus: string) =>
    apiRequest<AmbiguityFinding>(`/api/v1/ambiguities/${id}/review`, token, {
      method: "POST",
      body: JSON.stringify({
        reviewStatus,
        feedbackType:
          reviewStatus === "APPROVED" ? "AMBIGUITY_CONFIRMED" : "WRONG_AMBIGUITY",
        reason: "Belirsizlik çalışma alanı uzman incelemesi",
      }),
    }),
  conflicts: (token: string, projectId: string) =>
    apiRequest<ConflictRecord[]>(
      `/api/v1/tenders/${projectId}/conflicts`,
      token,
    ),
  conflict: (token: string, id: string) =>
    apiRequest<ConflictRecord>(`/api/v1/conflicts/${id}`, token),
  reviewConflict: (token: string, id: string, reviewStatus: string) =>
    apiRequest<ConflictRecord>(`/api/v1/conflicts/${id}/review`, token, {
      method: "POST",
      body: JSON.stringify({
        reviewStatus,
        feedbackType:
          reviewStatus === "APPROVED" ? "CONFLICT_CONFIRMED" : "FALSE_CONFLICT",
        reason: "Çelişki çalışma alanı uzman incelemesi",
        resolution: { source: "EXPERT_REVIEW", finalDecision: false },
      }),
    }),
  createChangeSet: (
    token: string,
    documentId: string,
    baseDocumentVersionId: string,
    targetDocumentVersionId: string,
  ) =>
    apiRequest<ChangeSet>(`/api/v1/documents/${documentId}/change-sets`, token, {
      method: "POST",
      body: JSON.stringify({ baseDocumentVersionId, targetDocumentVersionId }),
    }),
  changeSet: (token: string, id: string) =>
    apiRequest<ChangeSet>(`/api/v1/change-sets/${id}`, token),
  correctChange: (
    token: string,
    changeSetId: string,
    item: ChangeItem,
    baseClauseId?: string,
    targetClauseId?: string,
  ) =>
    apiRequest<ChangeSet>(
      `/api/v1/change-sets/${changeSetId}/items/${item.id}`,
      token,
      {
        method: "PUT",
        body: JSON.stringify({
          baseClauseId: baseClauseId || null,
          targetClauseId: targetClauseId || null,
          changeTypeConceptId: item.changeTypeConceptId,
          reviewStatus: "APPROVED",
          attributes: { expertCorrected: true },
        }),
      },
    ),
  impact: (token: string, changeSetId: string) =>
    apiRequest<ImpactAnalysis>(
      `/api/v1/change-sets/${changeSetId}/impact-analyses`,
      token,
      { method: "POST" },
    ),
};
