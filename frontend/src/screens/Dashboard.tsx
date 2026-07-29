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


import { Empty } from "./_shared";

export function Dashboard({
  metrics,
  projects,
  onProjects,
  onContinueAnalysis,
}: {
  metrics: ReturnType<typeof dashboardMetrics>;
  events: AuditEvent[];
  projects: TenderProject[];
  onProjects: () => void;
  onContinueAnalysis: (project: TenderProject) => void;
}) {
  const cards = [
    ["Aktif projeler", metrics.activeProjects, FolderKanban, "text-violet-600"],
    ["Hazır dokümanlar", metrics.processedDocuments, CheckCircle2, "text-emerald-600"],
    ["Başarısız dokümanlar", metrics.failedDocuments, AlertTriangle, "text-rose-600"],
    ["Yaklaşan tarihler", metrics.upcomingDeadlines.length, CalendarDays, "text-teal-600"],
  ] as const;
  return (
    <PageShell
      heroSize="dashboard"
      title="Ana panel"
      subtitle="Veriler doğrudan tenant kapsamlı platform API’sinden alınır."
      icon={<Gauge className="h-4 w-4" />}
      heroTrailing={
        <button className="btn-primary" onClick={onProjects}>
          Projeleri aç <ChevronRight />
        </button>
      }
      maxWidth="max-w-[1460px]"
    >
      <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map(([label, value, Icon, tone], index) => (
          <QaMetricCard
            key={label}
            label={label}
            value={value}
            icon={Icon}
            tone={tone}
            delay={index * 60}
          />
        ))}
      </section>
      <section className="dashboard-grid">
        <article className="panel card-static">
          <div className="panel-head">
            <div>
              <b>Yaklaşan son teklif tarihleri</b>
              <span>En yakın dört aktif proje</span>
            </div>
            <CalendarDays />
          </div>
          {metrics.upcomingDeadlines.length
            ? metrics.upcomingDeadlines.map((project) => (
                <div className="deadline-row" key={project.id}>
                  <span>{formatDate(project.bidDeadline)}</span>
                  <div>
                    <b>{project.name}</b>
                    <small>{project.institutionName}</small>
                  </div>
                  <button
                    type="button"
                    className="continue-analysis-btn"
                    onClick={() => onContinueAnalysis(project)}
                  >
                    Analize devam <ChevronRight className="h-3.5 w-3.5" />
                  </button>
                </div>
              ))
            : <Empty text="Yaklaşan son teklif tarihi bulunmuyor." />}
        </article>
        <article className="panel card-static">
          <div className="panel-head">
            <div>
              <b>Proje durumları</b>
              <span>Gerçek proje portföyü</span>
            </div>
            <Activity />
          </div>
          {projects.length
            ? projects.slice(0, 6).map((project) => (
                <div className="activity-row" key={project.id}>
                  <span className={`status-dot ${project.status.toLowerCase()}`} />
                  <div>
                    <b>{project.name}</b>
                    <small>{project.status.replaceAll("_", " ")}</small>
                  </div>
                  <button
                    type="button"
                    className="continue-analysis-btn"
                    onClick={() => onContinueAnalysis(project)}
                  >
                    Analize devam
                  </button>
                </div>
              ))
            : <Empty text="Henüz proje oluşturulmadı." />}
        </article>
      </section>
    </PageShell>
  );
}
