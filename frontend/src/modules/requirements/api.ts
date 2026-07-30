import { apiRequest } from "@/src/shared/api";

export type Requirement = {
  id: string;
  projectId: string;
  documentId: string;
  documentVersionId: string;
  extractionJobId: string;
  sourceClauseId: string;
  requirementCode: string;
  title?: string;
  requirementText: string;
  primaryConceptId?: string;
  modality?: string;
  modalityConceptId?: string;
  testabilityStatus?: string;
  conditionText?: string;
  subjectText?: string;
  actionText?: string;
  objectText?: string;
  attributes: Record<string, unknown>;
  extractionMethod: string;
  reviewStatus: string;
  groundingStatus: string;
  groundingCoverage: number;
  analysisProfileId: string;
  ontologyVersionId: string;
  policyVersionId: string;
  promptVersionId: string;
  combinedConfidence: number;
  requirementType?: string;
  obligationLevel?: string;
  lifecycleStage?: string;
  criticality?: string;
  evaluationMethod?: string;
  consequenceType?: string;
  remediability?: string;
  requiresClarification?: boolean;
  closedWorldRequired?: boolean;
  classificationStatus?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type RequirementColumn = {
  key: string;
  label: string;
  type: "TEXT" | "NUMBER" | "PERCENT" | "STATUS" | "CONCEPT" | string;
  visible: boolean;
  applicableConcepts?: string[];
};

export type RequirementGrid = {
  columns: RequirementColumn[];
};

export type ExtractionJob = {
  id: string;
  documentId: string;
  documentVersionId: string;
  status: string;
  analysisProfileId: string;
  totalClauseCount: number;
  processedClauseCount: number;
  extractedRequirementCount: number;
  manualReviewCount: number;
  correlationId: string;
  startedAt?: string;
  completedAt?: string;
};

type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number?: number;
  page?: number;
};

export const requirementApi = {
  list: (token: string, projectId: string) =>
    apiRequest<PageResponse<Requirement>>(
      `/api/v1/tenders/${projectId}/requirements?size=200&sort=createdAt,desc`,
      token,
    ),
  grid: (token: string) =>
    apiRequest<RequirementGrid>(
      "/api/v1/ui-configurations/requirement-grid",
      token,
    ),
  start: (token: string, documentId: string) =>
    apiRequest<ExtractionJob>(
      `/api/v1/documents/${documentId}/requirement-extractions`,
      token,
      { method: "POST" },
    ),
  job: (token: string, jobId: string) =>
    apiRequest<ExtractionJob>(
      `/api/v1/requirement-extractions/${jobId}`,
      token,
    ),
  explanation: (token: string, requirementId: string) =>
    apiRequest<Record<string, unknown>>(
      `/api/v1/requirements/${requirementId}/explanation`,
      token,
    ),
  review: (
    token: string,
    requirementId: string,
    body: {
      reviewStatus: string;
      feedbackType?: string;
      reason?: string;
      approvedForLearning?: boolean;
    },
  ) =>
    apiRequest<Requirement>(
      `/api/v1/requirements/${requirementId}/review`,
      token,
      { method: "POST", body: JSON.stringify(body) },
    ),
  split: (
    token: string,
    requirementId: string,
    body: Record<string, unknown>,
  ) =>
    apiRequest<Requirement[]>(
      `/api/v1/requirements/${requirementId}/split`,
      token,
      { method: "POST", body: JSON.stringify(body) },
    ),
  merge: (
    token: string,
    requirementId: string,
    body: Record<string, unknown>,
  ) =>
    apiRequest<Requirement>(
      `/api/v1/requirements/${requirementId}/merge`,
      token,
      { method: "POST", body: JSON.stringify(body) },
    ),
};
