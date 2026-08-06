import { apiRequest } from "@/src/shared/api";

export type CompanyCapability = {
  capabilityId: string;
  organizationId: string;
  kind: string;
  canonicalKey: string;
  label: string;
  attributes: Record<string, unknown>;
  validFrom?: string | null;
  validTo?: string | null;
  validityStatus: string;
  sourceDocumentId?: string | null;
  evidenceSnippet?: string | null;
  confidence: number;
};

export type FitEvidence = {
  capabilityId?: string | null;
  documentId?: string | null;
  snippet?: string | null;
  note?: string | null;
};

export type RequirementFitRow = {
  requirementId: string;
  category?: string | null;
  priority?: string | null;
  textPreview: string;
  status: "MET" | "PARTIAL" | "MISSING" | "AMBIGUOUS" | "NOT_APPLICABLE" | string;
  score: number;
  reasons: string[];
  evidence: FitEvidence[];
  suggestedAction?: string | null;
};

export type CompanyFitReport = {
  id?: string;
  organizationId: string;
  tenderDocumentId: string;
  overall: "FIT" | "CONDITIONAL" | "NOT_FIT" | "INSUFFICIENT_DATA" | string;
  overallScore: number;
  mustMet: number;
  mustTotal: number;
  missingCritical: string[];
  rows: RequirementFitRow[];
  generatedAt?: string;
  policyVersion?: string;
};

export type IngestDocument = {
  documentId: string;
  docType: string;
  title?: string;
  text: string;
};

export type IngestResult = {
  organizationId: string;
  capabilityCount: number;
  capabilities: CompanyCapability[];
};

export const companyFitApi = {
  ingest: (token: string, orgId: string, documents: IngestDocument[]) =>
    apiRequest<IngestResult>(
      `/api/v1/organizations/${orgId}/capabilities/ingest`,
      token,
      { method: "POST", body: JSON.stringify({ documents }) },
    ),
  listCapabilities: (token: string, orgId: string) =>
    apiRequest<CompanyCapability[]>(
      `/api/v1/organizations/${orgId}/capabilities`,
      token,
    ),
  evaluate: (
    token: string,
    documentId: string,
    body: {
      organizationId?: string;
      requirements?: Record<string, unknown>[];
      capabilities?: Record<string, unknown>[];
    } = {},
  ) =>
    apiRequest<CompanyFitReport>(
      `/api/v1/tenders/${documentId}/company-fit`,
      token,
      { method: "POST", body: JSON.stringify(body) },
    ),
  latest: (token: string, documentId: string) =>
    apiRequest<Record<string, unknown>[]>(
      `/api/v1/tenders/${documentId}/company-fit`,
      token,
    ),
};
