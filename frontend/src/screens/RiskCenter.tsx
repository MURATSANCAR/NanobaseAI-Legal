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


import { Empty, LoadingPanel, AnalysisProgress, RiskDetail, formatRiskValue } from "./_shared";

export function RiskCenter({ project, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [risks, setRisks] = useState<RiskRecord[]>([]);
  const [columns, setColumns] = useState<DynamicColumn[]>([]);
  const [selected, setSelected] = useState<RiskRecord>();
  const [job, setJob] = useState<RiskAnalysisJob>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [records, grid] = await Promise.all([
        riskApi.list(token, project.id),
        riskApi.grid(token),
      ]);
      setRisks(records);
      setColumns(grid.columns.filter((column) => column.visible));
      setSelected((current) => current
        ? records.find((item) => item.id === current.id) ?? current
        : current);
    } catch (error) {
      onProblem(error);
    } finally {
      setLoading(false);
    }
  }, [onProblem, project.id, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  useEffect(() => {
    if (!job || ["COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED", "CANCELLED"]
      .includes(job.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const current = await riskApi.job(token, job.id);
        setJob(current);
        if (["COMPLETED", "COMPLETED_WITH_ERRORS"].includes(current.status)) {
          window.clearInterval(timer);
          await load();
          onNotify(`${current.riskCount} risk, ${current.conflictCount} çelişki üretildi`);
        }
      } catch (error) {
        window.clearInterval(timer);
        onProblem(error);
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [job, load, onNotify, onProblem, token]);

  async function start() {
    try {
      const created = await riskApi.start(token, project.id);
      setJob(created);
      onNotify("Sürümlü risk analizi kuyruğa alındı");
    } catch (error) {
      onProblem(error);
    }
  }

  async function openRisk(risk: RiskRecord) {
    try {
      setSelected(await riskApi.get(token, risk.id));
    } catch (error) {
      onProblem(error);
    }
  }

  async function review(reviewStatus: string) {
    if (!selected) return;
    try {
      const updated = await riskApi.review(token, selected.id, reviewStatus);
      setSelected(updated);
      await load();
      onNotify(reviewStatus === "APPROVED" ? "Risk onaylandı" : "Risk reddedildi");
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="intelligence-layout">
    <article className="panel intelligence-main">
      <div className="panel-head"><div><b>Dinamik risk merkezi</b>
        <span>Kolonlar aktif UI configuration ve ontology’den yüklenir</span></div>
        {canWrite && <button className="primary" onClick={start}
          disabled={!!job && !["COMPLETED", "COMPLETED_WITH_ERRORS", "FAILED", "CANCELLED"]
            .includes(job.status)}><Activity />Risk analizi</button>}
      </div>
      {job && <AnalysisProgress job={job} />}
      {loading ? <div className="processing"><LoaderCircle className="spin" />Yükleniyor…</div>
        : <div className="risk-table-wrap"><table className="risk-table">
          <thead><tr>{columns.map((column) =>
            <th key={column.key}>{column.label}</th>)}<th /></tr></thead>
          <tbody>{risks.map((risk) => <tr key={risk.id}
            className={selected?.id === risk.id ? "selected" : ""}>
            {columns.map((column) => <td key={column.key}>
              {formatRiskValue(risk, column)}</td>)}
            <td><button className="icon-button" onClick={() => openRisk(risk)}
              aria-label={`${risk.riskCode} risk detayını aç`}><ChevronRight /></button></td>
          </tr>)}</tbody>
        </table>{!risks.length && <Empty text="Henüz risk analizi sonucu bulunmuyor." />}</div>}
    </article>
    {selected && <RiskDetail risk={selected} token={token} canWrite={canWrite}
      onClose={() => setSelected(undefined)} onReview={review} onProblem={onProblem} />}
  </section>;
}
