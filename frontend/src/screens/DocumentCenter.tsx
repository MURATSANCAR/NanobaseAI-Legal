"use client";

import {
  Activity,
  AlertTriangle,
  Archive,
  ArrowLeft,
  Building2,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  FileClock,
  FileText,
  BrainCircuit,
  FlaskConical,
  GitCompareArrows,
  ClipboardCheck,
  FolderKanban,
  Gauge,
  LoaderCircle,
  Plus,
  RefreshCw,
  Rocket,
  Search,
  ShieldCheck,
  ServerCog,
  Upload,
  UserPlus,
  Users,
  X,
  ZoomIn,
  ZoomOut,
  type LucideIcon,
} from "lucide-react";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import {
  formatBytes,
  formatDate,
  initials,
  processingStatuses,
  type ProjectTab,
} from "@/src/lib/portal-utils";
import { MetricCard as QaMetricCard } from "@/src/components/qa/MetricCard";
import { PageShell } from "@/src/components/qa/PageShell";
import { ScrollTable } from "@/src/components/qa/ScrollTable";
import { StatusBadge } from "@/src/components/qa/StatusBadge";
import { type AuditEvent } from "@/src/modules/audit/api";
import { dashboardMetrics } from "@/src/modules/dashboard/metrics";
import {
  documentApi,
  documentTypeLabels,
  statusLabels,
  subscribeToProcessingEvents,
  type BoundingBox,
  type Clause,
  type DocumentType,
  type ProcessingEvent,
  type ProcessingJob,
  type ProjectDocument,
} from "@/src/modules/documents/api";
import {
  tenderApi,
  type ProjectMember,
  type TenderDraft,
  type TenderProject,
} from "@/src/modules/tenders/api";
import {
  requirementApi,
  type ExtractionJob,
  type Requirement,
  type RequirementColumn,
} from "@/src/modules/requirements/api";
import {
  complianceApi,
  type ComplianceColumn,
  type ComplianceEvaluation,
  type ComplianceEvaluationDetail,
  type ComplianceJob,
} from "@/src/modules/compliance/api";
import {
  dynamicValueLabel,
  knowledgeApi,
  type ConceptOption,
  type EntityDetail,
  type EntityUiConfiguration,
  type EvidenceFragment,
  type KnowledgeEntity,
  type KnowledgeExtractionJob,
} from "@/src/modules/knowledge/api";
import {
  riskApi,
  type AmbiguityFinding,
  type ChangeItem,
  type ChangeSet,
  type ConflictRecord,
  type DynamicColumn,
  type ImpactAnalysis,
  type RiskAnalysisJob,
  type RiskRecord,
  type RiskSource,
} from "@/src/modules/risks/api";
import { type ApiProblem } from "@/src/shared/api";
import {
  operationsApi,
  type AiQualitySnapshot,
  type OperationsSnapshot,
} from "@/src/modules/operations/api";
import {
  decisionApi,
  reportingApi,
  workApi,
  workflowApi,
  type ApprovalRequest,
  type ClarificationCenter,
  type ConceptOption as WorkflowConcept,
  type DecisionSupportCase,
  type DynamicDashboard,
  type ReportDefinition,
  type ReportJob,
  type SimulationResult,
  type TaskRecord,
  type WorkflowDefinition,
  type WorkflowNodeDraft,
  type WorkflowTransitionDraft,
} from "@/src/modules/workflow/api";
import {
  pilotApi,
  type DynamicConcept,
  type FeedbackCase,
  type ImprovementCandidate,
  type PilotDashboard,
} from "@/src/modules/pilot/api";
import {
  releaseApi,
  type GoLivePackage,
  type ReleaseRecord,
  type SystemVersion,
} from "@/src/modules/release/api";


import { Empty, VersionList } from "./_shared";
import { DocumentReview } from "./DocumentReview";

export function DocumentCenter({ project, documents, token, canWrite, onDocuments,
  onProblem, onNotify }: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onDocuments: (documents: ProjectDocument[]) => void;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [uploadOpen, setUploadOpen] = useState(false);
  const [busyId, setBusyId] = useState("");
  const [versions, setVersions] = useState<Record<string, boolean>>({});
  const [reviewDocumentId, setReviewDocumentId] = useState("");
  const reviewDocument = documents.find((item) => item.id === reviewDocumentId);

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const file = form.get("file");
    if (!(file instanceof File) || !file.size) return;
    setBusyId("upload");
    try {
      const created = await documentApi.upload(token, project.id, {
        file,
        documentType: form.get("documentType") as DocumentType,
        logicalName: String(form.get("logicalName") ?? ""),
        includedInAnalysis: form.get("includedInAnalysis") === "on",
      });
      onDocuments([created, ...documents]);
      setUploadOpen(false);
      onNotify("Doküman yüklendi ve işleme kuyruğuna alındı");
    } catch (error) {
      onProblem(error);
    } finally {
      setBusyId("");
    }
  }

  async function reprocess(document: ProjectDocument) {
    setBusyId(document.id);
    try {
      const updated = await documentApi.reprocess(token, document.id);
      onDocuments(documents.map((item) => item.id === updated.id ? updated : item));
      onNotify("Doküman yeniden işleme kuyruğuna alındı");
    } catch (error) {
      onProblem(error);
    } finally {
      setBusyId("");
    }
  }

  async function uploadVersion(document: ProjectDocument, file?: File) {
    if (!file?.size) return;
    setBusyId(document.id);
    try {
      const updated = await documentApi.uploadVersion(token, document.id, file);
      onDocuments(documents.map((item) => item.id === updated.id ? updated : item));
      onNotify(`v${updated.currentVersionNumber} yüklendi`);
    } catch (error) {
      onProblem(error);
    } finally {
      setBusyId("");
    }
  }

  return <section className="panel document-center">
    <div className="panel-head"><div><b>Doküman merkezi</b>
      <span>PDF ve DOCX · durumlar backend enumlarıyla birebir</span></div>
      {canWrite && <button className="primary" onClick={() => setUploadOpen(true)}>
        <Upload />Doküman yükle</button>}
    </div>
    {uploadOpen && <form className="inline-upload" onSubmit={upload}>
      <label>Dosya<input name="file" type="file" accept=".pdf,.docx" required /></label>
      <label>Mantıksal ad<input name="logicalName" maxLength={255}
        placeholder="Örn. Teknik Şartname" required /></label>
      <label>Doküman türü<select name="documentType" defaultValue="TECHNICAL_SPECIFICATION">
        {Object.entries(documentTypeLabels).map(([value, label]) =>
          <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="check"><input name="includedInAnalysis" type="checkbox"
        defaultChecked />Analize dahil et</label>
      <div className="form-actions"><button type="button" className="secondary"
        onClick={() => setUploadOpen(false)}>Vazgeç</button>
        <button className="primary" disabled={busyId === "upload"}>
          {busyId === "upload" ? <LoaderCircle className="spin" /> : <Upload />}Yükle
        </button></div>
    </form>}
    <div className="document-table">
      {documents.map((document) => <article className="document-row" key={document.id}>
        <span className="file-icon"><FileText /></span>
        <div className="document-name"><b>{document.logicalName}</b>
          <small>{documentTypeLabels[document.documentType]} · v{document.currentVersionNumber}
            {document.currentVersion && ` · ${formatBytes(document.currentVersion.fileSize)}`}
            {document.currentVersion?.pageCount
              ? ` · ${document.currentVersion.pageCount} sayfa` : ""}</small>
          <small>{document.currentJob?.provider || "Parser bekleniyor"}
            {document.currentVersion?.ocrRequired ? " · OCR" : " · Dijital metin"}
            {` · ${document.createdBy} · ${new Date(document.createdAt)
              .toLocaleString("tr-TR")}`}</small></div>
        <span className={`processing-badge ${document.status.toLowerCase()}`}>
          {processingStatuses.has(document.status) && <LoaderCircle className="spin" />}
          {statusLabels[document.status]}
          {document.currentJob && processingStatuses.has(document.status)
            ? ` · %${document.currentJob.progress}` : ""}</span>
        {(document.currentJob?.errorMessage || document.currentVersion?.errorMessage) &&
          <p className="document-error">{document.currentJob?.errorMessage
            || document.currentVersion?.errorMessage}</p>}
        {!!document.currentJob?.warnings.length &&
          <p className="document-warning">{document.currentJob.warnings.length}
            {" "}parser uyarısı</p>}
        <div className="document-actions">
          <button title="Dokümanı incele" onClick={() => setReviewDocumentId(document.id)}>
            <Eye />
          </button>
          <button title="Versiyonları göster" onClick={() =>
            setVersions((current) => ({ ...current, [document.id]: !current[document.id] }))}>
            <FileClock />
          </button>
          <button title="İndir" onClick={() => documentApi.download(token, document.id)
            .catch(onProblem)}><Download /></button>
          {canWrite && <>
            <label title="Yeni versiyon yükle"><Upload />
              <input type="file" accept=".pdf,.docx" onChange={(event) =>
                uploadVersion(document, event.target.files?.[0])} /></label>
            <button title="Yeniden işle" disabled={busyId === document.id}
              onClick={() => reprocess(document)}><RefreshCw /></button>
          </>}
        </div>
        {versions[document.id] && <VersionList token={token} document={document}
          onProblem={onProblem} />}
      </article>)}
      {!documents.length && <Empty text="Bu projeye henüz doküman yüklenmedi." />}
    </div>
    {reviewDocument && <DocumentReview document={reviewDocument} token={token}
      canWrite={canWrite} onClose={() => setReviewDocumentId("")}
      onProblem={onProblem} />}
  </section>;
}
