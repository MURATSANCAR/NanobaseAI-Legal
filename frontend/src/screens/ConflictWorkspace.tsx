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


import { Empty, SourcePanel } from "./_shared";

export function ConflictWorkspace({ project, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [conflicts, setConflicts] = useState<ConflictRecord[]>([]);
  const [selected, setSelected] = useState<ConflictRecord>();
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setConflicts(await riskApi.conflicts(token, project.id));
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

  async function select(item: ConflictRecord) {
    try {
      setSelected(await riskApi.conflict(token, item.id));
    } catch (error) {
      onProblem(error);
    }
  }

  async function review(status: string) {
    if (!selected) return;
    try {
      setSelected(await riskApi.reviewConflict(token, selected.id, status));
      await load();
      onNotify("Çelişki uzman kararı kaydedildi");
    } catch (error) {
      onProblem(error);
    }
  }

  if (loading) return <div className="processing"><LoaderCircle className="spin" />Yükleniyor…</div>;
  return <section className="workspace-stack">
    <article className="panel card-static finding-list">
      <div className="panel-head"><div><b>Çelişki adayları</b>
        <span>Aşamalı retrieval ve provider registry sonuçları</span></div></div>
      {conflicts.map((conflict) => <button key={conflict.id}
        className={selected?.id === conflict.id ? "finding active" : "finding"}
        onClick={() => select(conflict)}>
        <b>{conflict.conflictCode} · {conflict.concept}</b>
        <span>{conflict.description}</span>
        <small>%{Math.round(conflict.confidence * 100)} güven ·
          {" "}{conflict.reviewStatus}</small>
      </button>)}
      {!conflicts.length && <Empty text="Doğrulanmış kaynaklı çelişki adayı bulunmuyor." />}
    </article>
    {selected && <article className="conflict-workspace">
      <SourcePanel title="Kaynak A" source={selected.sources?.[0]}
        token={token} onProblem={onProblem} />
      <SourcePanel title="Kaynak B" source={selected.sources?.[1]}
        token={token} onProblem={onProblem} />
      <section className="panel card-static conflict-decision">
        <p className="eyebrow">ÇELİŞKİ ANALİZİ</p><h2>{selected.concept}</h2>
        <p>{selected.description}</p>
        <dl><div><dt>Strateji</dt><dd>{selected.comparisonStrategyCode}</dd></div>
          <div><dt>Güven</dt><dd>%{Math.round(selected.confidence * 100)}</dd></div>
          <div><dt>İnceleme</dt><dd>{selected.reviewStatus}</dd></div>
          <div><dt>Durum</dt><dd>{selected.status}</dd></div></dl>
        <h3>Authority ve öneri</h3>
        <p>Authority kararı aktif policy snapshot’ına bağlıdır; öneri final karar değildir.</p>
        {canWrite && <div className="review-actions">
          <button className="primary" onClick={() => review("APPROVED")}>
            <CheckCircle2 />Onayla</button>
          <button className="danger" onClick={() => review("REJECTED")}><X />Reddet</button>
        </div>}
      </section>
    </article>}
  </section>;
}
