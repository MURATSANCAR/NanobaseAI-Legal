import { apiRequest } from "@/src/shared/api";

export type Priority = "LOW" | "NORMAL" | "HIGH" | "CRITICAL";
export type TenderStatus =
  | "DRAFT"
  | "DOCUMENTS_PENDING"
  | "ANALYSIS_IN_PROGRESS"
  | "REVIEW_IN_PROGRESS"
  | "COMPLETED"
  | "ARCHIVED";

export type TenderProject = {
  id: string;
  projectCode: string;
  name: string;
  institutionName: string;
  tenderRegistrationNumber?: string;
  tenderType?: string;
  businessType?: string;
  sector?: string;
  priority: Priority;
  status: TenderStatus;
  bidDeadline?: string;
  clarificationDeadline?: string;
  description?: string;
  currency?: string;
  ownerUserId: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type TenderPage = {
  content: TenderProject[];
  totalElements: number;
  totalPages: number;
  number: number;
};

export type TenderDraft = {
  name: string;
  institutionName: string;
  tenderRegistrationNumber?: string;
  tenderType?: string;
  businessType?: string;
  sector?: string;
  priority: Priority;
  bidDeadline?: string;
  clarificationDeadline?: string;
  description?: string;
  currency?: string;
};

export type ProjectMember = {
  id: string;
  userId: string;
  projectRole: "OWNER" | "MANAGER" | "REVIEWER" | "VIEWER";
  canViewDocuments: boolean;
  canUploadDocuments: boolean;
  canManageMembers: boolean;
  canArchiveProject: boolean;
  createdAt: string;
};

export type AnalysisProgressResponse = {
  projectId: string;
  documents: boolean;
  requirements: boolean;
  knowledge: boolean;
  compliance: boolean;
  risks: boolean;
  recommendedStep: string;
  counts: {
    readyDocuments: number;
    requirements: number;
    knowledgeEntities: number;
    complianceEvaluations: number;
    risks: number;
  };
};

export const tenderApi = {
  list: (token: string) =>
    apiRequest<TenderPage>("/api/v1/tenders?size=100&sort=createdAt,desc", token),
  create: (token: string, draft: TenderDraft) =>
    apiRequest<TenderProject>("/api/v1/tenders", token, {
      method: "POST",
      body: JSON.stringify(draft),
    }),
  update: (token: string, project: TenderProject, draft: TenderDraft) =>
    apiRequest<TenderProject>(`/api/v1/tenders/${project.id}`, token, {
      method: "PUT",
      body: JSON.stringify({ ...draft, version: project.version }),
    }),
  archive: (token: string, projectId: string) =>
    apiRequest<TenderProject>(`/api/v1/tenders/${projectId}/archive`, token, {
      method: "POST",
    }),
  analysisProgress: (token: string, projectId: string) =>
    apiRequest<AnalysisProgressResponse>(
      `/api/v1/tenders/${projectId}/analysis-progress`,
      token,
    ),
  members: (token: string, projectId: string) =>
    apiRequest<ProjectMember[]>(`/api/v1/tenders/${projectId}/members`, token),
  addMember: (
    token: string,
    projectId: string,
    member: Omit<ProjectMember, "id" | "createdAt">,
  ) =>
    apiRequest<ProjectMember>(`/api/v1/tenders/${projectId}/members`, token, {
      method: "POST",
      body: JSON.stringify(member),
    }),
  removeMember: (token: string, projectId: string, memberId: string) =>
    apiRequest<void>(
      `/api/v1/tenders/${projectId}/members/${memberId}`,
      token,
      { method: "DELETE" },
    ),
};
