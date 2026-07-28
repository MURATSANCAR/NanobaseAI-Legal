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




export function ProjectSettings({ project, members, token, canWrite, onMembers, onProblem,
  onNotify, onArchive, busy }: {
  project: TenderProject;
  members: ProjectMember[];
  token: string;
  canWrite: boolean;
  onMembers: (members: ProjectMember[]) => void;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
  onArchive: () => void;
  busy: boolean;
}) {
  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(event.currentTarget);
    try {
      const created = await tenderApi.addMember(token, project.id, {
        userId: String(form.get("userId")),
        projectRole: form.get("projectRole") as ProjectMember["projectRole"],
        canViewDocuments: true,
        canUploadDocuments: form.get("canUploadDocuments") === "on",
        canManageMembers: form.get("canManageMembers") === "on",
        canArchiveProject: false,
      });
      onMembers([...members, created]);
      formElement.reset();
      onNotify("Proje üyesi eklendi");
    } catch (error) {
      onProblem(error);
    }
  }

  async function removeMember(member: ProjectMember) {
    try {
      await tenderApi.removeMember(token, project.id, member.id);
      onMembers(members.filter((item) => item.id !== member.id));
      onNotify("Proje üyesi çıkarıldı");
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="settings-grid">
    <article className="panel">
      <div className="panel-head"><div><b>Proje ekibi</b>
        <span>Üyelik ve doküman yetkileri</span></div><Users /></div>
      {canWrite && <form className="member-form" onSubmit={addMember}>
        <label>Kullanıcı kimliği<input name="userId" required maxLength={255}
          placeholder="Keycloak subject / kullanıcı ID" /></label>
        <label>Proje rolü<select name="projectRole" defaultValue="REVIEWER">
          <option value="MANAGER">Yönetici</option><option value="REVIEWER">İnceleyen</option>
          <option value="VIEWER">Görüntüleyen</option></select></label>
        <label className="check"><input type="checkbox" name="canUploadDocuments" />
          Doküman yükleyebilir</label>
        <label className="check"><input type="checkbox" name="canManageMembers" />
          Üye yönetebilir</label>
        <button className="primary"><UserPlus />Üye ekle</button>
      </form>}
      {members.map((member) => <div className="member-row" key={member.id}>
        <span>{initials(member.userId)}</span><div><b>{member.userId}</b>
          <small>{member.projectRole} · {member.canUploadDocuments ? "Yükleme yetkili" : "Salt okunur"}</small></div>
        {canWrite && member.projectRole !== "OWNER" &&
          <button onClick={() => removeMember(member)} aria-label="Üyeyi çıkar"><X /></button>}
      </div>)}
    </article>
    <article className="panel danger-card">
      <div className="panel-head"><div><b>Proje yaşam döngüsü</b>
        <span>Arşivleme veriyi silmez</span></div><Archive /></div>
      <p>Arşivlenen proje salt okunur geçmiş olarak korunur.</p>
      {canWrite && project.status !== "ARCHIVED" &&
        <button className="danger" onClick={onArchive} disabled={busy}><Archive />Projeyi arşivle</button>}
    </article>
  </section>;
}
