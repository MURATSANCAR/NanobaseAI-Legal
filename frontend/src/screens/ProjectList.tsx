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

export function ProjectList({ projects, statusFilter, institutionFilter, canWrite, onStatus,
  onInstitution, onSelect, onCreate }: {
  projects: TenderProject[];
  statusFilter: string;
  institutionFilter: string;
  canWrite: boolean;
  onStatus: (value: string) => void;
  onInstitution: (value: string) => void;
  onSelect: (project: TenderProject) => void;
  onCreate: () => void;
}) {
  return (
    <PageShell
      title="İhale projeleri"
      subtitle="Yalnız erişim yetkiniz bulunan organization verileri gösterilir."
      icon={<FolderKanban className="h-4 w-4" />}
      heroTrailing={
        canWrite ? (
          <button className="btn-primary" onClick={onCreate}>
            <Plus />
            Yeni proje
          </button>
        ) : null
      }
      maxWidth="max-w-[1460px]"
    >
      <section className="panel card-static">
        <div className="filter-bar mobile-toolbar">
          <select
            value={statusFilter}
            onChange={(event) => onStatus(event.target.value)}
            aria-label="Duruma göre filtrele"
          >
            <option value="">Tüm durumlar</option>
            <option value="DRAFT">Taslak</option>
            <option value="DOCUMENTS_PENDING">Doküman bekliyor</option>
            <option value="ANALYSIS_IN_PROGRESS">İşleniyor</option>
            <option value="COMPLETED">Tamamlandı</option>
            <option value="ARCHIVED">Arşivlendi</option>
          </select>
          <input
            value={institutionFilter}
            onChange={(event) => onInstitution(event.target.value)}
            placeholder="Kuruma göre filtrele"
          />
        </div>
        <ScrollTable>
          <table>
            <thead>
              <tr>
                <th>Proje kodu</th>
                <th>Proje adı</th>
                <th>Kurum</th>
                <th>Son teklif</th>
                <th>Durum</th>
                <th>Sorumlu</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id}>
                  <td>
                    <span className="project-code">{project.projectCode}</span>
                  </td>
                  <td>
                    <b>{project.name}</b>
                  </td>
                  <td>{project.institutionName}</td>
                  <td>{formatDate(project.bidDeadline)}</td>
                  <td>
                    <StatusBadge
                      label={project.status.replaceAll("_", " ")}
                      status={project.status}
                    />
                  </td>
                  <td className="owner">{initials(project.ownerUserId)}</td>
                  <td>
                    <button
                      className="icon-button"
                      onClick={() => onSelect(project)}
                      aria-label={`${project.name} detayını aç`}
                    >
                      <ChevronRight />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!projects.length && <Empty text="Filtrelerle eşleşen proje bulunamadı." />}
        </ScrollTable>
      </section>
    </PageShell>
  );
}
