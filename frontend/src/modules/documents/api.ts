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
  | "PARSING"
  | "OCR_PROCESSING"
  | "STRUCTURE_DETECTION"
  | "INDEXING"
  | "READY"
  | "FAILED"
  | "MANUAL_REVIEW_REQUIRED";

export type DocumentVersion = {
  id: string;
  versionNumber: number;
  originalFileName: string;
  mimeType: string;
  fileSize: number;
  sha256: string;
  processingStatus: ProcessingStatus;
  uploadedBy: string;
  uploadedAt: string;
  processingStartedAt?: string;
  processingCompletedAt?: string;
  errorCode?: string;
  errorMessage?: string;
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
  reprocess: (token: string, documentId: string) =>
    apiRequest<ProjectDocument>(
      `/api/v1/documents/${documentId}/reprocess`,
      token,
      { method: "POST" },
    ),
  download: async (token: string, documentId: string) => {
    const response = await apiRequest<{ url: string; expiresInSeconds: number }>(
      `/api/v1/documents/${documentId}/download-url`,
      token,
    );
    window.location.assign(response.url);
  },
};

export const statusLabels: Record<ProcessingStatus, string> = {
  UPLOADED: "Yüklendi",
  VIRUS_SCANNING: "Güvenlik taraması",
  CLASSIFYING: "Sınıflandırılıyor",
  PARSING: "Ayrıştırılıyor",
  OCR_PROCESSING: "OCR işleniyor",
  STRUCTURE_DETECTION: "Yapı belirleniyor",
  INDEXING: "İndeksleniyor",
  READY: "Hazır",
  FAILED: "Başarısız",
  MANUAL_REVIEW_REQUIRED: "Manuel inceleme",
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
