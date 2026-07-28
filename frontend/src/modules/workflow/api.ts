import { apiRequest } from "@/src/shared/api";

export type ConceptOption = {
  id: string;
  code: string;
  name: string;
  description?: string;
  metadata: Record<string, unknown>;
  sortOrder: number;
};

export type WorkflowDefinition = {
  id: string;
  workflowCode: string;
  name: string;
  description?: string;
  scope: string;
  activeVersionId?: string;
};

export type WorkflowNodeDraft = {
  nodeCode: string;
  nodeTypeConceptId: string;
  name: string;
  description?: string;
  configuration: Record<string, unknown>;
  sortOrder: number;
};

export type WorkflowTransitionDraft = {
  sourceNodeCode: string;
  targetNodeCode: string;
  transitionConceptId: string;
  condition: Record<string, unknown>;
  actionConfiguration: Record<string, unknown>;
  priority: number;
};

export type SimulationResult = {
  runId?: string;
  result: {
    valid: boolean;
    visitedNodeCodes: string[];
    findings: Array<{
      code: string;
      severity: string;
      nodeCode?: string;
      message: string;
    }>;
  };
};

export type TaskRecord = {
  id: string;
  projectId?: string;
  workflowInstanceId?: string;
  workflowExecutionId?: string;
  taskCode: string;
  taskTypeConceptCode: string;
  subjectEntityType: string;
  subjectEntityId: string;
  title: string;
  description?: string;
  priorityConceptCode: string;
  statusConceptId: string;
  statusConceptCode: string;
  assignedUserId?: string;
  assignedGroupId?: string;
  dueAt?: string;
  completedAt?: string;
  comments: Array<Record<string, unknown>>;
};

export type ApprovalRequest = {
  id: string;
  projectId?: string;
  workflowInstanceId?: string;
  subjectEntityType: string;
  subjectEntityId: string;
  statusConceptCode: string;
  requestedBy: string;
  requestedAt: string;
  completedAt?: string;
  expiresAt?: string;
  policyConfiguration: Record<string, unknown>;
  steps: Array<Record<string, unknown>>;
  decisions: Array<Record<string, unknown>>;
  availableDecisions: ConceptOption[];
};

export type ClarificationRequest = {
  id: string;
  projectId: string;
  questionCode: string;
  questionText: string;
  reason?: string;
  statusConceptCode: string;
  priorityConceptId: string;
  requiresLegalReview: boolean;
  requiresTechnicalReview: boolean;
  approvedVersionId?: string;
  sentAt?: string;
  answeredAt?: string;
  revisions: Array<Record<string, unknown>>;
  sources: Array<Record<string, unknown>>;
};

export type ClarificationCenter = {
  requests: ClarificationRequest[];
  candidates: Array<{
    id: string;
    sourceType: string;
    sourceId: string;
    question: string;
    reason: string;
    reviewStatus: string;
    priorityConceptId: string;
    requiresLegalReview: boolean;
  }>;
};

export type ReportDefinition = {
  id: string;
  reportCode: string;
  name: string;
  description?: string;
  scope: string;
  activeVersionId?: string;
};

export type ReportArtifact = {
  id: string;
  formatConceptCode: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  sha256: string;
};

export type ReportJob = {
  id: string;
  projectId: string;
  reportDefinitionVersionId: string;
  dataSnapshotId: string;
  statusConceptCode: string;
  progress: number;
  completedAt?: string;
  errorCode?: string;
  errorMessage?: string;
  artifacts: ReportArtifact[];
};

export type DecisionSupportCase = {
  id: string;
  projectId: string;
  caseCode: string;
  recommendedDecisionConceptId: string;
  recommendedDecisionConceptCode: string;
  finalDecisionConceptId?: string;
  finalDecisionConceptCode?: string;
  confidence: number;
  statusConceptCode: string;
  explanation: {
    summary?: string | string[];
    sourceReferences?: Array<Record<string, unknown>>;
    requiresExecutiveReview?: boolean;
  };
  factors: Array<{
    factorConceptCode: string;
    effectScore: number;
    weight: number;
    description: string;
    sourceReference: Record<string, unknown>;
  }>;
  executiveDecisions: Array<Record<string, unknown>>;
};

export type DynamicDashboard = {
  id: string;
  code: string;
  name: string;
  layout: Record<string, unknown>;
  widgets: Array<{
    code: string;
    typeConceptCode: string;
    dataSource: { columns?: string[]; entity?: string; metric?: string };
    display: { title?: string; pageSize?: number };
    visibilityCondition: Record<string, unknown>;
    position: Record<string, number>;
  }>;
};

export const workflowApi = {
  concepts: (token: string, conceptType: string) =>
    apiRequest<ConceptOption[]>(
      `/api/v1/ui-configurations/workflow-concepts/${conceptType}`,
      token,
    ),
  definitions: (token: string) =>
    apiRequest<WorkflowDefinition[]>("/api/v1/workflows", token),
  createDefinition: (
    token: string,
    draft: Pick<WorkflowDefinition, "workflowCode" | "name" | "description" | "scope">,
  ) =>
    apiRequest<WorkflowDefinition>("/api/v1/workflows", token, {
      method: "POST",
      body: JSON.stringify(draft),
    }),
  createVersion: (
    token: string,
    definitionId: string,
    nodes: WorkflowNodeDraft[],
    transitions: WorkflowTransitionDraft[],
  ) =>
    apiRequest<{ id: string }>(`/api/v1/workflows/${definitionId}/versions`, token, {
      method: "POST",
      body: JSON.stringify({ configuration: {}, nodes, transitions }),
    }),
  simulate: (token: string, versionId: string, variables: Record<string, unknown>) =>
    apiRequest<SimulationResult>(`/api/v1/workflow-versions/${versionId}/simulate`, token, {
      method: "POST",
      body: JSON.stringify({ variables, authorizedNodeCodes: [], maximumVisitsPerNode: 3 }),
    }),
  activate: (token: string, versionId: string) =>
    apiRequest(`/api/v1/workflow-versions/${versionId}/activate`, token, {
      method: "POST",
    }),
  dashboard: (token: string) =>
    apiRequest<DynamicDashboard>(
      "/api/v1/ui-configurations/dashboard/SPRINT_7_OPERATIONS",
      token,
    ),
};

export const workApi = {
  taskGrid: (token: string) =>
    apiRequest<{ columns: Array<{ key: string; label: string; visible: boolean }> }>(
      "/api/v1/ui-configurations/task-grid",
      token,
    ),
  tasks: (token: string, projectId?: string) =>
    apiRequest<TaskRecord[]>(
      `/api/v1/tasks${projectId ? `?projectId=${projectId}` : ""}`,
      token,
    ),
  claim: (token: string, id: string) =>
    apiRequest<TaskRecord>(`/api/v1/tasks/${id}/claim`, token, { method: "POST" }),
  complete: (token: string, id: string, targetStatusConceptId: string) =>
    apiRequest<TaskRecord>(`/api/v1/tasks/${id}/complete`, token, {
      method: "POST",
      body: JSON.stringify({ targetStatusConceptId }),
    }),
  block: (token: string, id: string, targetStatusConceptId: string) =>
    apiRequest<TaskRecord>(`/api/v1/tasks/${id}/block`, token, {
      method: "POST",
      body: JSON.stringify({ targetStatusConceptId }),
    }),
  comment: (token: string, id: string, commentText: string, visibilityConceptId: string) =>
    apiRequest<TaskRecord>(`/api/v1/tasks/${id}/comments`, token, {
      method: "POST",
      body: JSON.stringify({ commentText, visibilityConceptId }),
    }),
  approvals: (token: string, projectId?: string) =>
    apiRequest<ApprovalRequest[]>(
      `/api/v1/approvals${projectId ? `?projectId=${projectId}` : ""}`,
      token,
    ),
  decide: (
    token: string,
    approvalId: string,
    approvalStepId: string,
    decisionConceptId: string,
    comment: string,
  ) =>
    apiRequest(`/api/v1/approvals/${approvalId}/decisions`, token, {
      method: "POST",
      body: JSON.stringify({
        approvalStepId,
        decisionConceptId,
        comment,
        decisionSnapshot: { source: "HUMAN_UI" },
      }),
    }),
  clarifications: (token: string, projectId: string) =>
    apiRequest<ClarificationCenter>(
      `/api/v1/tenders/${projectId}/clarifications`,
      token,
    ),
  reviseClarification: (
    token: string,
    id: string,
    questionText: string,
    reason: string,
  ) =>
    apiRequest<ClarificationRequest>(`/api/v1/clarifications/${id}/revisions`, token, {
      method: "POST",
      body: JSON.stringify({ questionText, reason, sourceSnapshot: {} }),
    }),
  clarificationStatus: (
    token: string,
    id: string,
    action: "submit-review" | "approve" | "mark-sent",
    targetStatusConceptId: string,
  ) =>
    apiRequest<ClarificationRequest>(`/api/v1/clarifications/${id}/${action}`, token, {
      method: "POST",
      body: JSON.stringify({ targetStatusConceptId }),
    }),
};

export const reportingApi = {
  designerConfiguration: (token: string) =>
    apiRequest<{
      dataPolicies: Array<{ id: string; code: string; versionNumber: number }>;
      templates: Array<{
        id: string;
        code: string;
        versionNumber: number;
        formatConceptCode: string;
        formatConceptId: string;
        language: string;
      }>;
    }>("/api/v1/ui-configurations/report-designer", token),
  definitions: (token: string) =>
    apiRequest<ReportDefinition[]>("/api/v1/report-definitions", token),
  createDefinition: (
    token: string,
    draft: Pick<ReportDefinition, "reportCode" | "name" | "description" | "scope">,
  ) =>
    apiRequest<ReportDefinition>("/api/v1/report-definitions", token, {
      method: "POST",
      body: JSON.stringify(draft),
    }),
  createVersion: (
    token: string,
    definitionId: string,
    body: Record<string, unknown>,
  ) =>
    apiRequest<{ id: string }>(`/api/v1/report-definitions/${definitionId}/versions`, token, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  preview: (token: string, versionId: string, projectId: string) =>
    apiRequest<Record<string, unknown>>(
      `/api/v1/report-definition-versions/${versionId}/preview`,
      token,
      { method: "POST", body: JSON.stringify({ projectId }) },
    ),
  activate: (token: string, versionId: string) =>
    apiRequest(`/api/v1/report-definition-versions/${versionId}/activate`, token, {
      method: "POST",
    }),
  jobs: (token: string, projectId: string) =>
    apiRequest<ReportJob[]>(`/api/v1/tenders/${projectId}/reports`, token),
  generate: (
    token: string,
    projectId: string,
    reportDefinitionVersionId: string,
    formatConceptIds: string[],
  ) =>
    apiRequest<ReportJob>(`/api/v1/tenders/${projectId}/reports`, token, {
      method: "POST",
      body: JSON.stringify({ reportDefinitionVersionId, formatConceptIds }),
    }),
  downloadUrl: (token: string, artifactId: string) =>
    apiRequest<{ url: string }>(`/api/v1/report-artifacts/${artifactId}/download-url`, token),
};

export const decisionApi = {
  policies: (token: string) =>
    apiRequest<Array<{ id: string; code: string; versionNumber: number }>>(
      "/api/v1/ui-configurations/decision-policies",
      token,
    ),
  list: (token: string, projectId: string) =>
    apiRequest<DecisionSupportCase[]>(
      `/api/v1/tenders/${projectId}/decision-support-cases`,
      token,
    ),
  create: (
    token: string,
    projectId: string,
    decisionPolicyVersionId: string,
    dataSnapshotId?: string,
  ) =>
    apiRequest<DecisionSupportCase>(
      `/api/v1/tenders/${projectId}/decision-support-cases`,
      token,
      {
        method: "POST",
        body: JSON.stringify({ decisionPolicyVersionId, dataSnapshotId }),
      },
    ),
  decide: (
    token: string,
    caseId: string,
    decisionConceptId: string,
    decisionText: string,
  ) =>
    apiRequest(`/api/v1/decision-support-cases/${caseId}/decisions`, token, {
      method: "POST",
      body: JSON.stringify({ decisionConceptId, decisionText, conditions: [] }),
    }),
  history: (token: string, projectId: string) =>
    apiRequest<Array<Record<string, unknown>>>(
      `/api/v1/tenders/${projectId}/finalization-history`,
      token,
    ),
};
