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




export function MetricCard({ label, value, detail, tone = "teal" }: {
  label: string;
  value: string | number;
  detail: string;
  tone?: "teal" | "amber" | "red";
}) {
  return <article className={`metric-card ${tone}`}>
    <span>{label}</span><strong>{value}</strong><small>{detail}</small>
  </article>;
}

export function formatRatio(value?: number) {
  return value == null ? "Ölçülmedi" : `%${(value * 100).toFixed(1)}`;
}

export function prettyJson(value: unknown) {
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value ?? {}, null, 2);
}

export function readUnknown(value: Record<string, unknown>, ...keys: string[]) {
  for (const key of keys) {
    if (value[key] !== undefined && value[key] !== null) return value[key];
  }
  return undefined;
}

export function DynamicField({ label, value }: { label: string; value: string }) {
  return <div className="dynamic-field"><span>{label}</span><b>{value}</b></div>;
}

export function EvidenceDocumentPane({ evidence, token }: {
  evidence?: EvidenceFragment;
  token: string;
}) {
  const [url, setUrl] = useState("");

  useEffect(() => {
    let active = true;
    if (!evidence?.document_id) {
      const timer = window.setTimeout(() => setUrl(""), 0);
      return () => {
        active = false;
        window.clearTimeout(timer);
      };
    }
    documentApi.downloadUrl(token, evidence.document_id)
      .then((response) => active && setUrl(response.url))
      .catch(() => active && setUrl(""));
    return () => { active = false; };
  }, [evidence?.document_id, token]);

  if (!evidence) return <Empty text="Kaynak metni ve PDF bölgesini görmek için evidence seçin." />;
  const highlights = parseEvidenceBoxes(evidence.bounding_boxes_json);
  return <div className="evidence-document">
    <div className="evidence-meta">
      <b>{evidence.document_name || evidence.document_type || "Kanıt belgesi"}</b>
      <span>Sayfa {evidence.page_number ?? "—"} ·
        {" "}{evidence.validity_status || "Validity bekliyor"}</span>
      <small>Parser %{Math.round((evidence.parser_quality ?? 0) * 100)} · OCR
        {" "}%{Math.round((evidence.ocr_quality ?? 0) * 100)}</small>
    </div>
    <blockquote>{evidence.fragment_text || "Kaynak metin detay isteğiyle yüklenir."}</blockquote>
    {url ? <PdfCanvas url={url} pageNumber={evidence.page_number ?? 1}
      zoom={0.72} highlights={highlights} /> :
      <div className="pdf-placeholder"><FileText /><p>PDF önizlemesi hazırlanıyor.</p></div>}
  </div>;
}

export function parseEvidenceBoxes(value?: string): BoundingBox[] {
  if (!value) return [];
  try {
    const boxes = JSON.parse(value);
    return Array.isArray(boxes) ? boxes : [];
  } catch {
    return [];
  }
}

export function conceptMetadata(concept: ConceptOption): Record<string, unknown> {
  if (typeof concept.metadata === "object") return concept.metadata;
  try {
    return JSON.parse(concept.metadata) as Record<string, unknown>;
  } catch {
    return {};
  }
}

export function formatComplianceValue(
  evaluation: ComplianceEvaluation,
  column: ComplianceColumn,
) {
  const aliases: Record<string, unknown> = {
    requirementConcept: evaluation.requirementConcept,
    targetEntity: evaluation.targetScope,
    suggestedDecision: evaluation.suggestedDecision,
    finalDecision: evaluation.finalDecision,
  };
  const value = column.key in aliases
    ? aliases[column.key] : valueAt(evaluation, column.key);
  if (value === undefined || value === null || value === "") return "—";
  if (column.type === "PERCENT" && typeof value === "number") {
    return `%${Math.round(value * 100)}`;
  }
  return typeof value === "object" ? JSON.stringify(value) : String(value);
}

export function AnalysisProgress({ job }: { job: RiskAnalysisJob }) {
  return <div className="analysis-progress">
    <div><b>{job.status.replaceAll("_", " ")}</b>
      <span>{job.processedCandidateCount}/{job.totalCandidateCount} aday ·
        {" "}{job.riskCount} risk · {job.ambiguityCount} belirsizlik ·
        {" "}{job.conflictCount} çelişki</span></div>
    <progress max={Math.max(job.totalCandidateCount, 1)}
      value={job.processedCandidateCount} />
  </div>;
}

export function RiskDetail({ risk, token, canWrite, onClose, onReview, onProblem }: {
  risk: RiskRecord;
  token: string;
  canWrite: boolean;
  onClose: () => void;
  onReview: (status: string) => void;
  onProblem: (error: unknown) => void;
}) {
  return <aside className="panel intelligence-detail">
    <div className="detail-title"><div><p className="eyebrow">{risk.riskCode}</p>
      <h2>{risk.title}</h2></div>
      <button onClick={onClose} aria-label="Risk detayını kapat"><X /></button></div>
    <div className="score-strip">
      <Score label="Olasılık" value={risk.probabilityScore} />
      <Score label="Etki" value={risk.impactScore} />
      <Score label="Maruziyet" value={risk.exposureScore} />
      <Score label="Güven" value={risk.confidence} />
    </div>
    {risk.stalenessStatus && <p className="stale-warning"><FileClock />
      Bu sonuç {risk.stalenessStatus.replaceAll("_", " ")} durumunda.</p>}
    <h3>Risk özeti</h3><p>{risk.description}</p>
    <h3>Kaynaklar</h3>
    {risk.sources?.map((source) => <SourceCard key={source.id}
      source={source} token={token} onProblem={onProblem} />)}
    <h3>Hesaplama faktörleri</h3>
    <JsonList values={risk.factors} empty="Faktör kaydı bulunmuyor." />
    <h3>Yayılım adayları</h3>
    <JsonList values={risk.propagation} empty="Yayılım adayı bulunmuyor." />
    <h3>Mitigation adayları</h3>
    <JsonList values={risk.mitigations} empty="Onaylı katalog eşleşmesi bulunmuyor." />
    <h3>Revision geçmişi</h3>
    <JsonList values={risk.history} empty="Henüz uzman revizyonu yok." />
    {canWrite && <div className="review-actions">
      <button className="primary" onClick={() => onReview("APPROVED")}>
        <CheckCircle2 />Onayla</button>
      <button className="danger" onClick={() => onReview("REJECTED")}>
        <X />Reddet</button>
    </div>}
  </aside>;
}

export function SourcePanel({ title, source, token, onProblem }: {
  title: string;
  source?: RiskSource;
  token: string;
  onProblem: (error: unknown) => void;
}) {
  return <section className="panel source-panel"><p className="eyebrow">{title}</p>
    {source ? <SourceCard source={source} token={token} onProblem={onProblem} />
      : <Empty text="Kaynak yüklenemedi." />}</section>;
}

export function SourceCard({ source, token, onProblem }: {
  source: RiskSource;
  token: string;
  onProblem: (error: unknown) => void;
}) {
  async function openSource() {
    try {
      const response = await documentApi.downloadUrl(token, source.documentId);
      window.open(`${response.url}#page=${source.pageNumber ?? 1}`, "_blank",
        "noopener,noreferrer");
    } catch (error) {
      onProblem(error);
    }
  }
  return <article className="source-card">
    <div><b>{source.side || source.sourceType || "KAYNAK"}</b>
      <small>Sayfa {source.pageNumber ?? "—"} · {source.clauseId || "Clause yok"}</small></div>
    <p>{source.sourceText}</p>
    <button className="secondary" onClick={openSource}><Eye />PDF bölgesini aç</button>
  </article>;
}

export function JsonList({ values, empty }: {
  values?: Record<string, unknown>[];
  empty: string;
}) {
  if (!values?.length) return <p className="muted-copy">{empty}</p>;
  return <div className="json-list">{values.map((value, index) =>
    <pre key={String(value.id ?? index)}>{JSON.stringify(value, null, 2)}</pre>)}</div>;
}

export function Score({ label, value }: { label: string; value?: number }) {
  return <span className="score"><small>{label}</small>
    <b>{value === undefined || value === null ? "—" : `%${Math.round(value * 100)}`}</b></span>;
}

export function formatRiskValue(risk: RiskRecord, column: DynamicColumn) {
  const value = (risk as unknown as Record<string, unknown>)[column.key];
  if (value === undefined || value === null || value === "") return "—";
  if (column.type === "PERCENT" && typeof value === "number") {
    return `%${Math.round(value * 100)}`;
  }
  if (column.type === "DATE" && typeof value === "string") return formatDate(value);
  return String(value).replaceAll("_", " ");
}

export function formatRequirementValue(requirement: Requirement, column: RequirementColumn) {
  const aliases: Record<string, unknown> = {
    sourceClause: requirement.sourceClauseId,
    primaryConcept: requirement.primaryConceptId,
  };
  const value = column.key in aliases ? aliases[column.key] : valueAt(requirement, column.key);
  if (value === undefined || value === null || value === "") return "—";
  if (column.type === "PERCENT" && typeof value === "number") {
    return `%${Math.round(value * 100)}`;
  }
  if (typeof value === "object") return JSON.stringify(value);
  return String(value).replaceAll("_", " ");
}

export function valueAt(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, part) => {
    if (!current || typeof current !== "object") return undefined;
    return (current as Record<string, unknown>)[part];
  }, value);
}

export function VersionList({ token, document, onProblem }: {
  token: string;
  document: ProjectDocument;
  onProblem: (error: unknown) => void;
}) {
  const [items, setItems] = useState<Awaited<ReturnType<typeof documentApi.versions>>>();
  useEffect(() => {
    documentApi.versions(token, document.id).then(setItems).catch(onProblem);
  }, [document.id, onProblem, token]);
  return <div className="version-list">
    {!items ? <LoaderCircle className="spin" /> : items.map((version) =>
      <div key={version.id}><b>v{version.versionNumber}</b>
        <span>{version.originalFileName}</span><small>{statusLabels[version.processingStatus]}
          · {new Date(version.uploadedAt).toLocaleString("tr-TR")}</small></div>)}
  </div>;
}

export function ClauseTree({ clauses, parentId, selectedId, onSelect }: {
  clauses: Clause[];
  parentId?: string;
  selectedId?: string;
  onSelect: (clause: Clause) => void;
}) {
  const children = clauses.filter((clause) => clause.parentId === parentId
    || (!clause.parentId && !parentId));
  if (!children.length) return parentId ? null : <Empty text="Henüz madde çıkarılmadı." />;
  return <ul>{children.map((clause) => <li key={clause.id}>
    <button className={selectedId === clause.id ? "active" : ""}
      onClick={() => onSelect(clause)}>
      <b>{clause.number || "•"}</b><span>{clause.title || clause.normalizedText.slice(0, 80)}</span>
      <small>s. {clause.pageStart}</small>
    </button>
    <ClauseTree clauses={clauses} parentId={clause.id}
      selectedId={selectedId} onSelect={onSelect} />
  </li>)}</ul>;
}

export function PdfCanvas({ url, pageNumber, zoom, highlights }: {
  url: string;
  pageNumber: number;
  zoom: number;
  highlights: BoundingBox[];
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy>();
  const [renderError, setRenderError] = useState("");

  useEffect(() => {
    let active = true;
    let loaded: PDFDocumentProxy | undefined;
    import("pdfjs-dist").then((pdfjs) => {
      pdfjs.GlobalWorkerOptions.workerSrc =
        new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();
      return pdfjs.getDocument({ url }).promise;
    }).then((document) => {
      loaded = document;
      if (active) {
        setPdf(document);
      }
    }).catch(() => active && setRenderError("PDF güvenli biçimde görüntülenemedi"));
    return () => {
      active = false;
      loaded?.destroy();
    };
  }, [url]);

  useEffect(() => {
    if (!pdf || !canvasRef.current) return;
    let cancelled = false;
    pdf.getPage(Math.min(pageNumber, pdf.numPages)).then(async (pdfPage) => {
      if (cancelled || !canvasRef.current) return;
      const viewport = pdfPage.getViewport({ scale: zoom });
      const canvas = canvasRef.current;
      const context = canvas.getContext("2d");
      if (!context) return;
      canvas.width = Math.floor(viewport.width);
      canvas.height = Math.floor(viewport.height);
      await pdfPage.render({ canvas, canvasContext: context, viewport }).promise;
    }).catch(() => !cancelled && setRenderError("PDF sayfası görüntülenemedi"));
    return () => { cancelled = true; };
  }, [pageNumber, pdf, zoom]);

  if (renderError) return <div className="pdf-placeholder"><AlertTriangle />
    <p>{renderError}</p></div>;
  return <div className="pdf-scroll"><div className="pdf-page">
    <canvas ref={canvasRef} />
    <div className="source-highlights">{highlights.map((box, index) =>
      <span key={`${box.page}-${index}`} style={{
        left: `${box.x * 100}%`,
        top: `${box.y * 100}%`,
        width: `${box.width * 100}%`,
        height: `${box.height * 100}%`,
      }} />)}</div>
  </div></div>;
}

export function ProblemBanner({ problem, onClose, onRetry }: {
  problem: ApiProblem;
  onClose: () => void;
  onRetry: () => void;
}) {
  return <div className="problem-banner" role="alert"><AlertTriangle />
    <div><b>{problem.title}</b><p>{problem.detail}</p>
      {problem.correlationId && <small>Correlation ID: {problem.correlationId}</small>}
      {problem.fieldErrors?.map((error) =>
        <small key={error.field}>{error.field}: {error.message}</small>)}</div>
    {problem.status >= 500 && <button onClick={onRetry}>Tekrar dene</button>}
    <button onClick={onClose} aria-label="Hatayı kapat"><X /></button>
  </div>;
}

export function Empty({ text }: { text: string }) {
  return <div className="empty"><FileText /><p>{text}</p></div>;
}

export function LoadingPanel() {
  return <div className="empty" aria-live="polite"><LoaderCircle className="spin" />
    <p>Veriler yükleniyor…</p></div>;
}

export function EmptyState({ icon: Icon, title, body }: {
  icon: LucideIcon;
  title: string;
  body: string;
}) {
  return <div className="empty"><Icon /><p><b>{title}</b><br />{body}</p></div>;
}

export function LoadingScreen() {
  return <main className="loading-screen"><LoaderCircle className="spin" />
    <p>Güvenli oturum hazırlanıyor…</p></main>;
}

export type Sprint7Tab =
  | "tasks"
  | "approvals"
  | "clarifications"
  | "workflow-designer"
  | "report-designer"
  | "decision-support";

export type Sprint9Tab = "quality" | "errors" | "improvements" | "release";
