import { apiRequest } from "@/src/shared/api";

export type DocumentType =
  | "TECHNICAL_SPECIFICATION"
  | "ADMINISTRATIVE_SPECIFICATION"
  | "DRAFT_CONTRACT"
  | "ADDENDUM"
  | "PRICE_SCHEDULE"
  | "PRODUCT_CATALOG"
  | "CERTIFICATE"
  | "TECHNICAL_DRAWING"
  | "OTHER";

export type ProcessingStatus =
  | "UPLOADED"
  | "VIRUS_SCANNING"
  | "CLASSIFYING"
  | "QUEUED"
  | "PARSING"
  | "OCR_PROCESSING"
  | "STRUCTURE_DETECTION"
  | "INDEXING"
  | "READY"
  | "FAILED"
  | "MANUAL_REVIEW_REQUIRED"
  | "CANCELLED";

export type DocumentVersion = {
  id: string;
  versionNumber: number;
  originalFileName: string;
  mimeType: string;
  fileSize: number;
  sha256: string;
  pageCount?: number;
  language?: string;
  ocrRequired: boolean;
  ocrQualityScore?: number;
  processingStatus: ProcessingStatus;
  uploadedBy: string;
  uploadedAt: string;
  processingStartedAt?: string;
  processingCompletedAt?: string;
  errorCode?: string;
  errorMessage?: string;
};

export type BoundingBox = {
  page: number;
  x: number;
  y: number;
  width: number;
  height: number;
};

export type ParserWarning = {
  id: string;
  warningCode: string;
  severity: "INFO" | "WARNING" | "ERROR";
  message: string;
  pageNumber?: number;
  metadata: Record<string, unknown>;
};

export type ProcessingJob = {
  id: string;
  documentVersionId: string;
  provider?: string;
  status: ProcessingStatus;
  currentStage: ProcessingStatus;
  progress: number;
  attemptCount: number;
  externalReference?: string;
  correlationId: string;
  startedAt?: string;
  completedAt?: string;
  heartbeatAt?: string;
  errorCode?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
  warnings: ParserWarning[];
};

export type Clause = {
  id: string;
  parentId?: string;
  number?: string;
  title?: string;
  rawText: string;
  normalizedText: string;
  clauseType?: string;
  pageStart: number;
  pageEnd: number;
  boundingBoxes: BoundingBox[];
  contentHash: string;
  sortOrder: number;
};

export type DocumentPage = {
  id: string;
  pageNumber: number;
  width?: number;
  height?: number;
  rotation: number;
  rawText: string;
  normalizedText: string;
  textQualityScore?: number;
  thumbnailObjectKey?: string;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ProcessingEvent = {
  eventId: string;
  jobId?: string;
  documentId: string;
  documentVersionId: string;
  stage: ProcessingStatus;
  progress: number;
  message: string;
  occurredAt: string;
};

export type ProjectDocument = {
  id: string;
  projectId: string;
  logicalName: string;
  documentType: DocumentType;
  currentVersionId: string;
  currentVersionNumber: number;
  status: ProcessingStatus;
  includedInAnalysis: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  currentVersion?: DocumentVersion;
  currentJob?: ProcessingJob;
};

export const documentApi = {
  list: (token: string, projectId: string) =>
    apiRequest<ProjectDocument[]>(
      `/api/v1/tenders/${projectId}/documents`,
      token,
    ),
  upload: (
    token: string,
    projectId: string,
    input: {
      file: File;
      documentType: DocumentType;
      logicalName: string;
      includedInAnalysis: boolean;
    },
  ) => {
    const body = new FormData();
    body.set("file", input.file);
    body.set("documentType", input.documentType);
    body.set("logicalName", input.logicalName);
    body.set("includedInAnalysis", String(input.includedInAnalysis));
    return apiRequest<ProjectDocument>(
      `/api/v1/tenders/${projectId}/documents`,
      token,
      { method: "POST", body },
    );
  },
  uploadVersion: (token: string, documentId: string, file: File) => {
    const body = new FormData();
    body.set("file", file);
    return apiRequest<ProjectDocument>(
      `/api/v1/documents/${documentId}/versions`,
      token,
      { method: "POST", body },
    );
  },
  versions: (token: string, documentId: string) =>
    apiRequest<DocumentVersion[]>(
      `/api/v1/documents/${documentId}/versions`,
      token,
    ),
  get: (token: string, documentId: string) =>
    apiRequest<ProjectDocument>(`/api/v1/documents/${documentId}`, token),
  pages: (token: string, documentId: string, page = 0, size = 50) =>
    apiRequest<PageResponse<DocumentPage>>(
      `/api/v1/documents/${documentId}/pages?page=${page}&size=${size}`,
      token,
    ),
  clauses: (token: string, documentId: string, search = "") =>
    apiRequest<PageResponse<Clause>>(
      `/api/v1/documents/${documentId}/clauses?size=200&search=${encodeURIComponent(search)}`,
      token,
    ),
  clause: (token: string, documentId: string, clauseId: string) =>
    apiRequest<Clause>(
      `/api/v1/documents/${documentId}/clauses/${clauseId}`,
      token,
    ),
  jobs: (token: string, documentId: string) =>
    apiRequest<PageResponse<ProcessingJob>>(
      `/api/v1/documents/${documentId}/processing-jobs?size=100`,
      token,
    ),
  cancel: (token: string, jobId: string) =>
    apiRequest<ProcessingJob>(
      `/api/v1/processing-jobs/${jobId}/cancel`,
      token,
      { method: "POST" },
    ),
  reprocess: (token: string, documentId: string) =>
    apiRequest<ProjectDocument>(
      `/api/v1/documents/${documentId}/reprocess`,
      token,
      { method: "POST" },
    ),
  download: async (token: string, documentId: string) => {
    const response = await documentApi.downloadUrl(token, documentId);
    window.location.assign(response.url);
  },
  downloadUrl: (token: string, documentId: string) =>
    apiRequest<{ url: string; expiresInSeconds: number }>(
      `/api/v1/documents/${documentId}/download-url`,
      token,
    ),
};

export async function subscribeToProcessingEvents(
  token: string,
  documentId: string,
  onEvent: (event: ProcessingEvent) => void,
  signal: AbortSignal,
  lastEventId?: string,
) {
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const response = await fetch(
    `${apiBase}/api/v1/documents/${documentId}/processing-events`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "text/event-stream",
        ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}),
      },
      signal,
      cache: "no-store",
    },
  );
  if (!response.ok || !response.body) {
    throw new Error(`SSE connection failed (${response.status})`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (!signal.aborted) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";
    for (const frame of frames) {
      const lines = frame.split("\n");
      const eventName = lines.find((line) => line.startsWith("event:"))?.slice(6).trim();
      const data = lines.filter((line) => line.startsWith("data:"))
        .map((line) => line.slice(5).trim()).join("\n");
      if (eventName === "processing" && data) {
        onEvent(JSON.parse(data) as ProcessingEvent);
      }
    }
  }
}

export const statusLabels: Record<ProcessingStatus, string> = {
  UPLOADED: "Yüklendi",
  VIRUS_SCANNING: "Güvenlik taraması",
  CLASSIFYING: "Sınıflandırılıyor",
  QUEUED: "Kuyrukta",
  PARSING: "Ayrıştırılıyor",
  OCR_PROCESSING: "OCR işleniyor",
  STRUCTURE_DETECTION: "Yapı belirleniyor",
  INDEXING: "İndeksleniyor",
  READY: "Hazır",
  FAILED: "Başarısız",
  MANUAL_REVIEW_REQUIRED: "Manuel inceleme",
  CANCELLED: "İptal edildi",
};

export const documentTypeLabels: Record<DocumentType, string> = {
  TECHNICAL_SPECIFICATION: "Teknik şartname",
  ADMINISTRATIVE_SPECIFICATION: "İdari şartname",
  DRAFT_CONTRACT: "Sözleşme taslağı",
  ADDENDUM: "Zeyilname",
  PRICE_SCHEDULE: "Fiyat cetveli",
  PRODUCT_CATALOG: "Ürün kataloğu",
  CERTIFICATE: "Sertifika",
  TECHNICAL_DRAWING: "Teknik çizim",
  OTHER: "Diğer",
};
