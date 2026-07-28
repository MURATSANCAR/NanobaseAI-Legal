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


import { Score, LoadingPanel } from "./_shared";

export function OperationsCenter({ token, projects, documents, onProblem }: {
  token: string;
  projects: TenderProject[];
  documents: ProjectDocument[];
  onProblem: (error: unknown) => void;
}) {
  const [snapshot, setSnapshot] = useState<OperationsSnapshot>();
  const [quality, setQuality] = useState<AiQualitySnapshot>();
  const [tab, setTab] = useState<"operations" | "quality" | "pilot">("operations");
  const [operationsLoading, setOperationsLoading] = useState(true);

  const load = useCallback(async () => {
    setOperationsLoading(true);
    try {
      const [nextSnapshot, nextQuality] = await Promise.all([
        operationsApi.readiness(token),
        operationsApi.aiQuality(token),
      ]);
      setSnapshot(nextSnapshot);
      setQuality(nextQuality);
    } catch (error) {
      onProblem(error);
    } finally {
      setOperationsLoading(false);
    }
  }, [onProblem, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  if (operationsLoading && !snapshot) return <LoadingPanel />;
  const readyServices = Object.values(snapshot?.services ?? {})
    .filter((service) => service.status === "UP").length;
  const totalServices = Object.keys(snapshot?.services ?? {}).length;
  const checklist = [
    ["Security", snapshot?.security.auditIntegrityValid ?? false],
    ["Backup", snapshot?.recovery.lastBackupStatus === "SUCCESS"],
    ["Restore", snapshot?.recovery.lastRestoreStatus === "SUCCESS"],
    ["Evaluation", Boolean(quality?.recentEvaluationRuns.length)],
    ["UAT", false],
    ["Offline package", false],
  ] as const;
  const readiness = Math.round(
    checklist.filter(([, complete]) => complete).length / checklist.length * 100,
  );

  return (
    <PageShell
      title="Production kontrol merkezi"
      subtitle="Servis, güvenlik, recovery, AI kalite ve pilot kanıtlarını tek yerde izleyin."
      icon={<ServerCog className="h-4 w-4" />}
      heroTrailing={
        <button className="btn-secondary" onClick={() => void load()}>
          <RefreshCw />
          Yenile
        </button>
      }
      maxWidth="max-w-[1460px]"
    >
    <div className="workspace-tabs operations-tabs tabs">
      <button className={tab === "operations" ? "active" : ""}
        onClick={() => setTab("operations")}><ServerCog />Operasyon</button>
      <button className={tab === "quality" ? "active" : ""}
        onClick={() => setTab("quality")}><BrainCircuit />AI kalite</button>
      <button className={tab === "pilot" ? "active" : ""}
        onClick={() => setTab("pilot")}><ClipboardCheck />Pilot ve kabul</button>
    </div>
    {tab === "operations" && <section className="operations-grid">
      <article className="panel ops-summary card-static">
        <div className="panel-head"><div><b>Servis readiness</b>
          <span>{snapshot?.environment} · {snapshot?.releaseVersion}</span></div>
          <strong>{readyServices}/{totalServices}</strong></div>
        <div className="service-grid">
          {Object.entries(snapshot?.services ?? {}).map(([name, service]) =>
            <div className="service-row" key={name}>
              <span className={`health-pill ${service.status.toLowerCase()}`}>
                {service.status}</span>
              <div><b>{name}</b><small>{service.detail}</small></div>
            </div>)}
        </div>
      </article>
      <article className="panel card-static">
        <div className="panel-head"><div><b>İş yükü ve güvenlik</b>
          <span>Tenant kapsamlı canlı göstergeler</span></div><ShieldCheck /></div>
        <div className="ops-kpis">
          <Score label="Aktif job" value={snapshot?.workload.activeJobs} />
          <Score label="Başarısız job" value={snapshot?.workload.failedJobs} />
          <Score label="Outbox backlog" value={snapshot?.workload.outboxPending} />
          <Score label="DLQ / dead" value={snapshot?.workload.outboxDead} />
        </div>
        <p className={snapshot?.security.auditIntegrityValid
          ? "integrity-ok" : "integrity-failed"}>
          Audit zinciri {snapshot?.security.auditIntegrityValid ? "doğrulandı" : "doğrulanamadı"}
        </p>
      </article>
      <article className="panel full-span">
        <div className="panel-head"><div><b>Go-live blocker’ları</b>
          <span>Kanıt tamamlanmadan production önerisi verilmez</span></div>
          <AlertTriangle /></div>
        <div className="blocker-list">
          {(snapshot?.blockers.length ? snapshot.blockers : ["BLOCKER_YOK"]).map((blocker) =>
            <span key={blocker}>{blocker.replaceAll("_", " ")}</span>)}
        </div>
      </article>
    </section>}
    {tab === "quality" && <section className="operations-grid">
      <article className="panel">
        <div className="panel-head"><div><b>AI yönetişimi</b>
          <span>Quality gate, shadow ve canary</span></div><BrainCircuit /></div>
        <div className="ops-kpis">
          <Score label="Evaluation run" value={quality?.recentEvaluationRuns.length} />
          <Score label="Prompt review" value={quality?.pendingPromptSecurityReviews} />
          <Score label="Shadow run" value={quality?.activeShadowRuns} />
          <Score label="Aktif canary" value={quality?.activeCanaries} />
        </div>
      </article>
      <article className="panel">
        <div className="panel-head"><div><b>Aktivasyon ilkesi</b>
          <span>Tek metrik yeterli değildir</span></div><ShieldCheck /></div>
        <ul className="governance-list">
          <li>Critical requirement recall ve grounding minimumları</li>
          <li>Numeric accuracy ve conflict false-positive sınırları</li>
          <li>Latency, manual review ve regression limitleri</li>
          <li>Onaylı gate olmadan model/prompt/policy aktivasyonu yok</li>
        </ul>
      </article>
    </section>}
    {tab === "pilot" && <section className="operations-grid">
      <article className="panel pilot-score">
        <div className="radial-score"><strong>{readiness}</strong><small>/ 100</small></div>
        <div><p className="eyebrow">AÇIKLANABİLİR SKOR</p><h2>Pilot readiness</h2>
          <p>Skor yalnız kanıtı bulunan checklist maddelerinden hesaplanır.</p></div>
      </article>
      <article className="panel">
        <div className="panel-head"><div><b>Pilot kapsamı</b>
          <span>Mevcut tenant verisi</span></div><ClipboardCheck /></div>
        <div className="ops-kpis">
          <Score label="Projeler" value={projects.length} />
          <Score label="Belgeler" value={documents.length} />
          <Score label="Hazır belge" value={
            documents.filter((document) => document.status === "READY").length} />
          <Score label="Açık blocker" value={snapshot?.blockers.length} />
        </div>
      </article>
      <article className="panel full-span">
        <div className="acceptance-checklist">
          {checklist.map(([label, complete]) => <div key={label}>
            {complete ? <CheckCircle2 /> : <FileClock />}
            <span>{label}</span><b>{complete ? "KANITLI" : "BEKLİYOR"}</b>
          </div>)}
        </div>
      </article>
    </section>}
  </PageShell>
  );
}
