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


import { Empty, ClauseTree, PdfCanvas } from "./_shared";

export function DocumentReview({ document, token, canWrite, onClose, onProblem }: {
  document: ProjectDocument;
  token: string;
  canWrite: boolean;
  onClose: () => void;
  onProblem: (error: unknown) => void;
}) {
  const [clauses, setClauses] = useState<Clause[]>([]);
  const [selected, setSelected] = useState<Clause>();
  const [jobs, setJobs] = useState<ProcessingJob[]>([]);
  const [pdfUrl, setPdfUrl] = useState("");
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState(1.15);
  const [query, setQuery] = useState("");
  const [liveEvent, setLiveEvent] = useState<ProcessingEvent>();
  const [streamState, setStreamState] = useState<"connecting" | "live" | "polling">(
    "connecting",
  );
  const [mobileReviewTab, setMobileReviewTab] = useState<"clauses" | "pdf" | "detail">("pdf");

  const load = useCallback(async () => {
    const [clausePage, jobPage] = await Promise.all([
      documentApi.clauses(token, document.id, query),
      documentApi.jobs(token, document.id),
    ]);
    setClauses(clausePage.content);
    setJobs(jobPage.content);
    if (!selected && clausePage.content[0]) setSelected(clausePage.content[0]);
  }, [document.id, query, selected, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => load().catch(onProblem), 200);
    return () => window.clearTimeout(timer);
  }, [load, onProblem]);

  useEffect(() => {
    if (document.currentVersion?.mimeType !== "application/pdf") return;
    documentApi.downloadUrl(token, document.id)
      .then((response) => setPdfUrl(response.url))
      .catch(onProblem);
  }, [document.currentVersion?.mimeType, document.id, onProblem, token]);

  useEffect(() => {
    if (!processingStatuses.has(document.status)) {
      return;
    }
    const controller = new AbortController();
    let lastEventId: string | undefined;
    async function connect() {
      while (!controller.signal.aborted) {
        try {
          await subscribeToProcessingEvents(token, document.id, (event) => {
            lastEventId = event.eventId;
            setLiveEvent(event);
            setStreamState("live");
            setJobs((current) => current.map((job) => job.id === event.jobId
              ? { ...job, status: event.stage, currentStage: event.stage,
                progress: event.progress, updatedAt: event.occurredAt }
              : job));
          }, controller.signal, lastEventId);
        } catch {
          if (!controller.signal.aborted) setStreamState("polling");
        }
        if (!controller.signal.aborted) {
          await new Promise((resolve) => window.setTimeout(resolve, 2000));
        }
      }
    }
    connect();
    return () => controller.abort();
  }, [document.id, document.status, token]);

  useEffect(() => {
    if (!processingStatuses.has(document.status) && streamState !== "polling") return;
    const timer = window.setInterval(() => load().catch(() => undefined), 5000);
    return () => window.clearInterval(timer);
  }, [document.status, load, streamState]);

  const activeJob = jobs[0] ?? document.currentJob;
  const selectedBoxes = (selected?.boundingBoxes ?? [])
    .filter((box) => box.page === page);

  async function cancel() {
    if (!activeJob) return;
    try {
      const cancelled = await documentApi.cancel(token, activeJob.id);
      setJobs((current) => current.map((job) =>
        job.id === cancelled.id ? cancelled : job));
    } catch (error) {
      onProblem(error);
    }
  }

  return <div className="review-backdrop">
    <section className="document-review" aria-label="Doküman inceleme">
      <header className="review-head">
        <div><p className="eyebrow">DOKÜMAN İNCELEME</p><h2>{document.logicalName}</h2>
          <span>{activeJob?.provider || "Parser bekleniyor"} ·
            {" "}{activeJob ? statusLabels[activeJob.status] : statusLabels[document.status]}
            {" "}· %{liveEvent?.progress ?? activeJob?.progress ?? 0} ·
            {" "}{!processingStatuses.has(document.status) ? "Tamamlandı"
              : streamState === "live" ? "Canlı" : streamState === "polling"
              ? "Polling fallback" : "Bağlanıyor"}</span></div>
        <div className="review-head-actions">
          {canWrite && activeJob && processingStatuses.has(activeJob.status) &&
            <button className="secondary" onClick={cancel}>İptal</button>}
          <button className="icon-button" onClick={onClose} aria-label="İncelemeyi kapat">
            <X />
          </button>
        </div>
      </header>
      <div className="review-mobile-tabs tabs" role="tablist" aria-label="İnceleme panelleri">
        <button
          type="button"
          className={mobileReviewTab === "clauses" ? "active" : ""}
          onClick={() => setMobileReviewTab("clauses")}
        >
          Maddeler
        </button>
        <button
          type="button"
          className={mobileReviewTab === "pdf" ? "active" : ""}
          onClick={() => setMobileReviewTab("pdf")}
        >
          PDF
        </button>
        <button
          type="button"
          className={mobileReviewTab === "detail" ? "active" : ""}
          onClick={() => setMobileReviewTab("detail")}
        >
          Detay
        </button>
      </div>
      <div className={`review-grid review-grid--${mobileReviewTab}`}>
        <aside className="clause-panel">
          <label><Search /><input value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Maddelerde ara" /></label>
          <div className="clause-tree">
            <ClauseTree clauses={clauses} parentId={undefined} selectedId={selected?.id}
              onSelect={(clause) => {
                setSelected(clause);
                setPage(clause.pageStart);
                setMobileReviewTab("pdf");
              }} />
          </div>
        </aside>
        <main className="pdf-panel">
          <div className="pdf-toolbar mobile-toolbar">
            <button onClick={() => setPage((value) => Math.max(1, value - 1))}
              aria-label="Önceki sayfa"><ChevronLeft /></button>
            <span>Sayfa {page}</span>
            <button onClick={() => setPage((value) => value + 1)}
              aria-label="Sonraki sayfa"><ChevronRight /></button>
            <button onClick={() => setZoom((value) => Math.max(.6, value - .15))}
              aria-label="Uzaklaştır"><ZoomOut /></button>
            <button onClick={() => setZoom((value) => Math.min(2.5, value + .15))}
              aria-label="Yakınlaştır"><ZoomIn /></button>
          </div>
          {pdfUrl
            ? <PdfCanvas url={pdfUrl} pageNumber={page} zoom={zoom}
                highlights={selectedBoxes} />
            : <div className="pdf-placeholder"><FileText />
                <p>PDF önizlemesi yalnız PDF dokümanlarında gösterilir.</p></div>}
        </main>
        <aside className="clause-detail">
          {selected ? <>
            <p className="eyebrow">SEÇİLİ MADDE</p>
            <h3>{selected.number || "—"} {selected.title}</h3>
            <dl>
              <div><dt>Sayfa</dt><dd>{selected.pageStart}–{selected.pageEnd}</dd></div>
              <div><dt>Tür</dt><dd>{selected.clauseType || "Belirtilmedi"}</dd></div>
              <div><dt>Parser</dt><dd>{activeJob?.provider || "—"}</dd></div>
              <div><dt>Hash</dt><dd className="hash">{selected.contentHash}</dd></div>
            </dl>
            <h4>Ham metin</h4><p>{selected.rawText}</p>
            <h4>Normalize metin</h4>
            <p className="clause-source-highlight">{selected.normalizedText}</p>
            <h4>Kaynak koordinatları</h4>
            <pre>{JSON.stringify(selected.boundingBoxes, null, 2)}</pre>
            {canWrite && (
              <div className="requirement-actions" style={{ marginTop: 12 }}>
                <p className="eyebrow">İNCELEME</p>
                <p className="text-sm">Madde hiyerarşisi ve kaynak vurgusu otomatik çıkarılır;
                  gereksinim onay/red işlemleri Gereksinimler matrisinden yapılır.</p>
              </div>
            )}
            {!!activeJob?.warnings.length && <>
              <h4>Parser uyarıları</h4>
              {activeJob.warnings.map((warning) =>
                <p className="warning-box" key={warning.id}>
                  <AlertTriangle />{warning.warningCode}: {warning.message}</p>)}
            </>}
          </> : <Empty text="İncelemek için bir madde seçin." />}
          <details className="job-history"><summary>Processing geçmişi</summary>
            {jobs.map((job) => <div key={job.id}><b>{statusLabels[job.status]}</b>
              <small>{job.provider || "—"} · %{job.progress} ·
                {" "}{new Date(job.createdAt).toLocaleString("tr-TR")}</small></div>)}
          </details>
        </aside>
      </div>
    </section>
  </div>;
}
