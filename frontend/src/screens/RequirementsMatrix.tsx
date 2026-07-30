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


import { Empty, LoadingPanel, formatRequirementValue } from "./_shared";

export function RequirementsMatrix({ project, documents, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [requirements, setRequirements] = useState<Requirement[]>([]);
  const [columns, setColumns] = useState<RequirementColumn[]>([]);
  const [loading, setLoading] = useState(true);
  const [job, setJob] = useState<ExtractionJob>();
  const [documentId, setDocumentId] = useState(
    documents.find((document) => document.status === "READY" && document.includedInAnalysis)?.id ?? "",
  );
  const [explanation, setExplanation] = useState<Record<string, unknown>>();
  const [selectedDetail, setSelectedDetail] = useState<Requirement>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, grid] = await Promise.all([
        requirementApi.list(token, project.id),
        requirementApi.grid(token),
      ]);
      setRequirements(page.content);
      setColumns(grid.columns.filter((column) => column.visible));
    } catch (error) {
      onProblem(error);
    } finally {
      setLoading(false);
    }
  }, [onProblem, project.id, token]);

  useEffect(() => {
    const initialLoad = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(initialLoad);
  }, [load]);

  useEffect(() => {
    if (!job || ["COMPLETED", "FAILED", "CANCELLED"].includes(job.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const current = await requirementApi.job(token, job.id);
        setJob(current);
        if (current.status === "COMPLETED") {
          window.clearInterval(timer);
          await load();
          onNotify(`${current.extractedRequirementCount} gereksinim çıkarıldı`);
        }
      } catch (error) {
        window.clearInterval(timer);
        onProblem(error);
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [job, load, onNotify, onProblem, token]);

  async function startExtraction() {
    if (!documentId) return;
    try {
      const created = await requirementApi.start(token, documentId);
      setJob(created);
      onNotify("Dinamik analiz profili oluşturuldu ve iş kuyruğa alındı");
    } catch (error) {
      onProblem(error);
    }
  }

  async function showExplanation(requirement: Requirement) {
    try {
      setExplanation(await requirementApi.explanation(token, requirement.id));
    } catch (error) {
      onProblem(error);
    }
  }

  async function reviewRequirement(reviewStatus: string) {
    if (!selectedDetail) return;
    try {
      const updated = await requirementApi.review(token, selectedDetail.id, {
        reviewStatus,
        reason: `UI review: ${reviewStatus}`,
        approvedForLearning: reviewStatus === "APPROVED",
      });
      setSelectedDetail(updated);
      await load();
      onNotify(`Gereksinim ${reviewStatus === "APPROVED" ? "onaylandı" : "incelendi"}`);
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="panel requirement-matrix card-static">
    <div className="panel-head mobile-toolbar"><div><b>Dinamik gereksinim matrisi</b>
      <span>Kolonlar aktif ontology ve attributes şemasından yüklenir</span></div>
      {canWrite && <div className="requirement-actions mobile-toolbar">
        <select value={documentId} onChange={(event) => setDocumentId(event.target.value)}>
          <option value="">Hazır doküman seçin</option>
          {documents.filter((document) => document.status === "READY" &&
            document.includedInAnalysis).map((document) =>
            <option key={document.id} value={document.id}>{document.logicalName}</option>)}
        </select>
        <button className="btn-primary" disabled={!documentId ||
          (job && !["COMPLETED", "FAILED", "CANCELLED"].includes(job.status))}
          onClick={startExtraction}><Activity />Analizi başlat</button>
      </div>}
    </div>
    {job && <div className="extraction-progress">
      <div><b>{job.status.replaceAll("_", " ")}</b>
        <span>{job.processedClauseCount}/{job.totalClauseCount} madde ·
          {" "}{job.extractedRequirementCount} gereksinim ·
          {" "}{job.manualReviewCount} inceleme</span></div>
      <progress max={Math.max(job.totalClauseCount, 1)} value={job.processedClauseCount} />
    </div>}
    {loading ? <div className="processing"><LoaderCircle className="spin" />Yükleniyor…</div>
      : <ScrollTable className="requirement-table-wrap"><table className="requirement-table">
        <thead><tr>{columns.map((column) =>
          <th key={column.key}>{column.label}</th>)}<th>Açıklama</th></tr></thead>
        <tbody>{requirements.map((requirement) => <tr key={requirement.id}>
          {columns.map((column) => <td key={column.key}>
            {formatRequirementValue(requirement, column)}</td>)}
          <td>
            <button className="icon-button" aria-label="Açıklamayı göster"
              onClick={() => showExplanation(requirement)}><Eye /></button>
            <button className="icon-button" aria-label="Detayı göster"
              onClick={() => setSelectedDetail(requirement)}>Detay</button>
          </td>
        </tr>)}</tbody>
      </table>{!requirements.length &&
        <Empty text="Henüz çıkarılmış gereksinim bulunmuyor." />}</ScrollTable>}
    {explanation && <div className="explanation-drawer">
      <div><b>Açıklanabilirlik</b><button onClick={() => setExplanation(undefined)}
        aria-label="Açıklamayı kapat"><X /></button></div>
      <pre>{JSON.stringify(explanation, null, 2)}</pre>
    </div>}
    {selectedDetail && (
      <div className="explanation-drawer">
        <div>
          <b>Requirement detay</b>
          <button type="button" onClick={() => setSelectedDetail(undefined)}
            aria-label="Detayı kapat"><X /></button>
        </div>
        <dl className="grid gap-2 text-sm">
          <div><dt>Obligation</dt><dd>{selectedDetail.obligationLevel || "—"}</dd></div>
          <div><dt>Lifecycle</dt><dd>{selectedDetail.lifecycleStage || "—"}</dd></div>
          <div><dt>Criticality</dt><dd>{selectedDetail.criticality || "—"}</dd></div>
          <div><dt>Evaluation method</dt><dd>{selectedDetail.evaluationMethod || "—"}</dd></div>
          <div><dt>Classification</dt><dd>{selectedDetail.classificationStatus || "—"}</dd></div>
          <div><dt>Review</dt><dd>{selectedDetail.reviewStatus}</dd></div>
          <div><dt>Grounding</dt><dd>{selectedDetail.groundingStatus}</dd></div>
          <div><dt>Kaynak metin</dt><dd className="whitespace-pre-wrap">
            {selectedDetail.requirementText}</dd></div>
        </dl>
        {canWrite && (
          <div className="requirement-actions mobile-toolbar" style={{ marginTop: 12 }}>
            <button type="button" className="btn-primary"
              onClick={() => void reviewRequirement("APPROVED")}>Onayla</button>
            <button type="button"
              onClick={() => void reviewRequirement("NEEDS_EDIT")}>Düzenleme gerekli</button>
            <button type="button" className="danger"
              onClick={() => void reviewRequirement("REJECTED")}>Reddet</button>
          </div>
        )}
      </div>
    )}
  </section>;
}
