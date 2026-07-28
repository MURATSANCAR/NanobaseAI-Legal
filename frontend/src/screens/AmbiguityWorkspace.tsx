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


import { Empty, SourceCard, JsonList } from "./_shared";

export function AmbiguityWorkspace({ project, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [findings, setFindings] = useState<AmbiguityFinding[]>([]);
  const [selected, setSelected] = useState<AmbiguityFinding>();
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => {
    setLoading(true);
    try {
      setFindings(await riskApi.ambiguities(token, project.id));
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

  async function select(item: AmbiguityFinding) {
    try {
      setSelected(await riskApi.ambiguity(token, item.id));
    } catch (error) {
      onProblem(error);
    }
  }

  async function review(status: string) {
    if (!selected) return;
    try {
      setSelected(await riskApi.reviewAmbiguity(token, selected.id, status));
      await load();
      onNotify("Belirsizlik uzman kararı kaydedildi");
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="intelligence-layout">
    <article className="panel finding-list intelligence-main">
      <div className="panel-head"><div><b>Belirsizlik çalışma alanı</b>
        <span>Yapısal eksikler, semantic sinyaller ve muhtemel yorumlar</span></div></div>
      {loading ? <div className="processing"><LoaderCircle className="spin" />Yükleniyor…</div>
        : findings.map((finding) => <button key={finding.id}
          className={selected?.id === finding.id ? "finding active" : "finding"}
          onClick={() => select(finding)}>
          <b>{finding.concept}</b><span>{finding.description}</span>
          <small>%{Math.round(finding.confidence * 100)} güven ·
            {" "}{finding.reviewStatus}</small>
        </button>)}
      {!loading && !findings.length && <Empty text="Belirsizlik adayı bulunmuyor." />}
    </article>
    {selected && <aside className="panel intelligence-detail">
      <div className="detail-title"><div><p className="eyebrow">BELİRSİZLİK</p>
        <h2>{selected.concept}</h2></div>
        <button onClick={() => setSelected(undefined)} aria-label="Detayı kapat"><X /></button></div>
      <Score label="Güven" value={selected.confidence} />
      <h3>Eksik alanlar ve etki</h3><p>{selected.description}</p>
      <h3>Kaynak clause</h3>
      {selected.sources?.map((source) => <SourceCard key={source.id}
        source={source} token={token} onProblem={onProblem} />)}
      <h3>Muhtemel yorumlar</h3>
      <JsonList values={selected.interpretations} empty="Henüz uzman yorumu eklenmedi." />
      <h3>Clarification candidate</h3>
      <p>Aday soru oluşturulabilir; insan onayı olmadan dışarı gönderilemez.</p>
      {canWrite && <div className="review-actions">
        <button className="primary" onClick={() => review("APPROVED")}>
          <CheckCircle2 />Onayla</button>
        <button className="danger" onClick={() => review("REJECTED")}><X />Reddet</button>
      </div>}
    </aside>}
  </section>;
}
