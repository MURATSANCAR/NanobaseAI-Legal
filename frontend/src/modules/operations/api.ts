import { apiRequest } from "@/src/shared/api";

export type ServiceStatus = {
  status: string;
  detail: string;
};

export type OperationsSnapshot = {
  environment: string;
  releaseVersion: string;
  generatedAt: string;
  services: Record<string, ServiceStatus>;
  workload: {
    activeJobs: number;
    failedJobs: number;
    outboxPending: number;
    outboxDead: number;
  };
  security: {
    auditIntegrityValid: boolean;
    securityReviewCount24h: number;
  };
  recovery: {
    lastBackupStatus: string;
    lastBackupAt?: string;
    lastRestoreStatus: string;
    lastRestoreAt?: string;
  };
  blockers: string[];
};

export type AiQualitySnapshot = {
  recentEvaluationRuns: Array<Record<string, unknown>>;
  pendingPromptSecurityReviews: number;
  activeShadowRuns: number;
  activeCanaries: number;
};

export const operationsApi = {
  readiness: (token: string) =>
    apiRequest<OperationsSnapshot>("/api/v1/operations/readiness", token),
  aiQuality: (token: string) =>
    apiRequest<AiQualitySnapshot>("/api/v1/operations/ai-quality", token),
};
