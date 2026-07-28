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


import { Overview } from "./Overview";
import { DocumentCenter } from "./DocumentCenter";
import { RequirementsMatrix } from "./RequirementsMatrix";
import { KnowledgeCenter } from "./KnowledgeCenter";
import { ComplianceWorkspace } from "./ComplianceWorkspace";
import { RiskCenter } from "./RiskCenter";
import { ConflictWorkspace } from "./ConflictWorkspace";
import { AmbiguityWorkspace } from "./AmbiguityWorkspace";
import { ChangeImpactWorkspace } from "./ChangeImpactWorkspace";
import { ActivityHistory } from "./ActivityHistory";
import { ProjectSettings } from "./ProjectSettings";

export function ProjectDetail({ project, tab, onTab, documents, members, auditEvents, token,
  canWrite, canAnalyze, loading, onBack, onDocuments, onMembers, onProblem, onNotify,
  onArchive, busy }: {
  project: TenderProject;
  tab: ProjectTab;
  onTab: (tab: ProjectTab) => void;
  documents: ProjectDocument[];
  members: ProjectMember[];
  auditEvents: AuditEvent[];
  token: string;
  canWrite: boolean;
  canAnalyze: boolean;
  loading: boolean;
  onBack: () => void;
  onDocuments: (documents: ProjectDocument[]) => void;
  onMembers: (members: ProjectMember[]) => void;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
  onArchive: () => void;
  busy: boolean;
}) {
  return (
    <PageShell
      title={project.name}
      subtitle={`${project.projectCode} · ${project.institutionName} · ${project.tenderRegistrationNumber || "Kayıt numarası yok"}`}
      icon={<FolderKanban className="h-4 w-4" />}
      maxWidth="max-w-[1460px]"
      heroTrailing={
        <StatusBadge label={project.status.replaceAll("_", " ")} status={project.status} />
      }
      header={
        <div className="space-y-3">
          <button className="back btn-secondary" onClick={onBack}>
            <ArrowLeft />
            Projelere dön
          </button>
          <div className="module-hero-strip module-hero-strip--legal animate-fade-in">
            <div className="module-hero-strip-main">
              <div className="module-hero-strip-icon bg-gradient-to-br from-teal-500 via-emerald-600 to-violet-600">
                <FolderKanban className="h-4 w-4" />
              </div>
              <div className="module-hero-strip-text min-w-0 flex-1">
                <h1 className="module-hero-strip-title">{project.name}</h1>
                <p className="module-hero-strip-subtitle">
                  {project.projectCode} · {project.institutionName} ·{" "}
                  {project.tenderRegistrationNumber || "Kayıt numarası yok"}
                </p>
              </div>
              <div className="module-hero-strip-trailing">
                <StatusBadge
                  label={project.status.replaceAll("_", " ")}
                  status={project.status}
                />
              </div>
            </div>
          </div>
          <nav className="tabs project-tabs" aria-label="Proje detay sekmeleri">
            {(
              [
                ["overview", "Genel bakış"],
                ["documents", "Dokümanlar"],
                ["requirements", "Gereksinim matrisi"],
                ["knowledge", "Firma ve ürünler"],
                ["compliance", "Uygunluk"],
                ["risks", "Risk merkezi"],
                ["conflicts", "Çelişkiler"],
                ["ambiguities", "Belirsizlikler"],
                ["changes", "Değişiklik ve etki"],
                ["activity", "Aktivite geçmişi"],
                ["settings", "Ayarlar"],
              ] as const
            ).map(([id, label]) => (
              <button
                key={id}
                className={tab === id ? "active" : ""}
                onClick={() => onTab(id)}
              >
                {label}
              </button>
            ))}
          </nav>
        </div>
      }
    >
      {loading ? (
        <div className="processing">
          <LoaderCircle className="spin" />
          Yükleniyor…
        </div>
      ) : (
        <>
          {tab === "overview" && (
            <Overview project={project} documents={documents} members={members} />
          )}
          {tab === "documents" && (
            <DocumentCenter
              project={project}
              documents={documents}
              token={token}
              canWrite={canWrite}
              onDocuments={onDocuments}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "requirements" && (
            <RequirementsMatrix
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "knowledge" && (
            <KnowledgeCenter
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "compliance" && (
            <ComplianceWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "risks" && (
            <RiskCenter
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "conflicts" && (
            <ConflictWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "ambiguities" && (
            <AmbiguityWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "changes" && (
            <ChangeImpactWorkspace
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "activity" && <ActivityHistory events={auditEvents} />}
          {tab === "settings" && (
            <ProjectSettings
              project={project}
              members={members}
              token={token}
              canWrite={canWrite}
              onMembers={onMembers}
              onProblem={onProblem}
              onNotify={onNotify}
              onArchive={onArchive}
              busy={busy}
            />
          )}
        </>
      )}
    </PageShell>
  );
}
