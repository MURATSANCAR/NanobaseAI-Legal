import { apiRequest } from "@/src/shared/api";

export type ReleaseRecord = {
  id: string;
  release_code: string;
  semantic_version: string;
  releaseType: string;
  status: string;
  source_commit: string;
  build_number: string;
  scope_locked_at?: string;
  created_at: string;
  approved_at?: string;
  released_at?: string;
};

export type ReleaseGate = {
  gateCode: string;
  name: string;
  required_by_default: boolean;
  status?: "PASS" | "FAIL" | "WAIVED" | "NOT_RUN";
  evidence_references_json?: unknown[];
  summary?: string;
  waiver_reason?: string;
  waived_by?: string;
  evaluated_at?: string;
};

export type GoLivePackage = {
  release: ReleaseRecord;
  manifest: Record<string, unknown>;
  gates: ReleaseGate[];
  artifacts: Array<Record<string, unknown>>;
  approvals: Array<Record<string, unknown>>;
  blockers: Array<Record<string, unknown>>;
  qualityDebt: Array<Record<string, unknown>>;
  dryRuns: Array<Record<string, unknown>>;
  decisions: Array<Record<string, unknown>>;
  eligibleForGoLive: boolean;
  missingEvidence: string[];
};

export type SystemVersion = {
  applicationVersion: string;
  buildNumber: string;
  commitHash: string;
  releaseDate: string;
  databaseMigrationVersion: string;
  configurationManifestVersion: string;
  modelPublicProfileVersion: string;
};

export const releaseApi = {
  releases: (token: string) =>
    apiRequest<ReleaseRecord[]>("/api/v1/releases", token),
  release: (token: string, id: string) =>
    apiRequest<ReleaseRecord>(`/api/v1/releases/${id}`, token),
  goLivePackage: (token: string, id: string) =>
    apiRequest<GoLivePackage>(
      `/api/v1/releases/${id}/go-live-package`,
      token,
    ),
  version: (token: string) =>
    apiRequest<SystemVersion>("/api/v1/system/version", token),
  diagnosticBundle: (token: string) =>
    apiRequest<Record<string, unknown>>(
      "/api/v1/operations/diagnostic-bundles",
      token,
      { method: "POST" },
    ),
  gate: (
    token: string,
    id: string,
    body: {
      gateCode: string;
      status: string;
      evidenceReferences: unknown[];
      summary: string;
      waiverReason?: string;
      compensatingControls: unknown[];
    },
  ) => apiRequest<Record<string, unknown>>(`/api/v1/releases/${id}/gates`, token, {
    method: "POST",
    body: JSON.stringify(body),
  }),
  goLiveDecision: (
    token: string,
    id: string,
    body: {
      decision: string;
      conditions: unknown[];
      openRisks: unknown[];
      rollbackPlanReference: string;
    },
  ) => apiRequest<Record<string, unknown>>(
    `/api/v1/releases/${id}/go-live-decisions`,
    token,
    { method: "POST", body: JSON.stringify(body) },
  ),
  deploy: (token: string, id: string) =>
    apiRequest<ReleaseRecord>(`/api/v1/releases/${id}/deploy`, token, {
      method: "POST",
    }),
  rollback: (token: string, id: string) =>
    apiRequest<ReleaseRecord>(`/api/v1/releases/${id}/rollback`, token, {
      method: "POST",
    }),
};
