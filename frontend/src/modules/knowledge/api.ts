import { apiRequest } from "@/src/shared/api";

export type ConceptOption = {
  id: string;
  code: string;
  name: string;
  description?: string;
  metadata: string | Record<string, unknown>;
  sortOrder: number;
};

export type DynamicValue = {
  type: string;
  textValue?: string;
  numericValue?: number;
  numericValueEnd?: number;
  booleanValue?: boolean;
  dateValue?: string;
  jsonValue?: unknown;
  unitConceptId?: string;
  unsupportedMetadata?: Record<string, unknown>;
};

export type KnowledgeEntity = {
  id: string;
  organizationId: string;
  entityCode: string;
  entityTypeConceptId: string;
  entityTypeCode: string;
  name: string;
  description?: string;
  status: string;
  validFrom?: string;
  validUntil?: string;
  attributes: Record<string, unknown>;
  sourceType: string;
  sourceReferenceId?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
};

export type EntityAttribute = {
  id: string;
  entityId: string;
  attributeConceptId: string;
  attributeConceptCode: string;
  value: DynamicValue;
  confidence: number;
  sourceFragmentId: string;
  reviewStatus: string;
  validFrom?: string;
  validUntil?: string;
};

export type KnowledgeRelation = {
  id: string;
  sourceEntityId: string;
  targetEntityId: string;
  relationConceptCode: string;
  confidence: number;
  sourceFragmentId: string;
  reviewStatus: string;
};

export type Capability = {
  id: string;
  ownerEntityId: string;
  capabilityConceptCode: string;
  name: string;
  description?: string;
  attributes: Record<string, unknown>;
  status: string;
  confidence: number;
  reviewStatus: string;
  evidence: Array<Record<string, unknown>>;
};

export type EvidenceFragment = {
  id: string;
  document_id: string;
  document_version_id: string;
  document_name?: string;
  document_type?: string;
  page_number?: number;
  fragment_text?: string;
  bounding_boxes_json?: string;
  content_hash: string;
  language?: string;
  parser_quality?: number;
  ocr_quality?: number;
  validity_score?: number;
  validity_status?: string;
  validity_factors?: string;
  valid_from?: string;
  valid_until?: string;
  usage_count?: number;
};

export type EntityDetail = {
  entity: KnowledgeEntity;
  attributes: EntityAttribute[];
  relations: KnowledgeRelation[];
  capabilities: Capability[];
  evidence: EvidenceFragment[];
  revisions: Array<Record<string, unknown>>;
};

export type EntityUiConfiguration = {
  conceptId: string;
  conceptCode: string;
  name: string;
  conceptMetadata: Record<string, unknown>;
  profile: {
    tabs: Array<{ key: string; label: string; sections: string[] }>;
    valueRenderers: Record<string, string>;
  };
  attributeDefinitions: ConceptOption[];
};

export type KnowledgeExtractionJob = {
  id: string;
  document_id: string;
  document_version_id: string;
  profile_id: string;
  status: string;
  total_fragment_count: number;
  processed_fragment_count: number;
  extracted_entity_count: number;
  manual_review_count: number;
  error_code?: string;
  error_message?: string;
};

export const knowledgeApi = {
  entities: (token: string, entityTypeConceptId?: string, query?: string) => {
    const parameters = new URLSearchParams();
    if (entityTypeConceptId) parameters.set("entityTypeConceptId", entityTypeConceptId);
    if (query) parameters.set("query", query);
    return apiRequest<KnowledgeEntity[]>(
      `/api/v1/knowledge/entities?${parameters.toString()}`,
      token,
    );
  },
  entity: (token: string, id: string) =>
    apiRequest<EntityDetail>(`/api/v1/knowledge/entities/${id}`, token),
  evidence: (token: string, id: string) =>
    apiRequest<EvidenceFragment>(`/api/v1/evidence/${id}`, token),
  evidenceList: (token: string) =>
    apiRequest<EvidenceFragment[]>("/api/v1/evidence", token),
  entityTypes: (token: string) =>
    apiRequest<ConceptOption[]>("/api/v1/ui-configurations/entity-types", token),
  entityConfiguration: (token: string, conceptId: string) =>
    apiRequest<EntityUiConfiguration>(
      `/api/v1/ui-configurations/entity-types/${conceptId}`,
      token,
    ),
  startExtraction: (token: string, documentId: string, documentRoleConceptId?: string) =>
    apiRequest<KnowledgeExtractionJob>(
      `/api/v1/documents/${documentId}/knowledge-extractions`,
      token,
      {
        method: "POST",
        body: JSON.stringify(
          documentRoleConceptId ? { documentRoleConceptId } : {},
        ),
      },
    ),
  extractionJob: (token: string, jobId: string) =>
    apiRequest<KnowledgeExtractionJob>(
      `/api/v1/knowledge-extractions/${jobId}`,
      token,
    ),
};

export function dynamicValueLabel(value: DynamicValue): string {
  if (value.unsupportedMetadata && Object.keys(value.unsupportedMetadata).length) {
    return JSON.stringify(value.unsupportedMetadata);
  }
  if (value.type === "RANGE") {
    return `${value.numericValue ?? "—"} – ${value.numericValueEnd ?? "—"}`;
  }
  if (value.numericValue !== undefined) return String(value.numericValue);
  if (value.booleanValue !== undefined) return value.booleanValue ? "Evet" : "Hayır";
  if (value.dateValue) return new Date(value.dateValue).toLocaleDateString("tr-TR");
  if (value.textValue) return value.textValue;
  if (value.jsonValue !== undefined) return JSON.stringify(value.jsonValue);
  return "—";
}
