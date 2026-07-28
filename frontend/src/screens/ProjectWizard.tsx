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




export function ProjectWizard({ step, draft, busy, onDraft, onStep, onClose, onSubmit }: {
  step: number;
  draft: TenderDraft;
  busy: boolean;
  onDraft: (draft: TenderDraft) => void;
  onStep: (step: number) => void;
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
}) {
  const change = (field: keyof TenderDraft, value: string) =>
    onDraft({ ...draft, [field]: value });
  return <div className="modal-backdrop">
    <form className="modal wizard" onSubmit={onSubmit}>
      <div className="modal-head"><div><p className="eyebrow">YENİ PROJE · ADIM {step}/4</p>
        <h2>{["Temel bilgiler", "Tarihler", "Firma ve ürün", "Ekip"][step - 1]}</h2></div>
        <button type="button" onClick={onClose} aria-label="Kapat"><X /></button></div>
      <div className="stepper">{[1, 2, 3, 4].map((item) =>
        <span key={item} className={item <= step ? "active" : ""}>{item}</span>)}</div>
      {step === 1 && <div className="form-grid">
        <label className="wide">Proje adı<input value={draft.name}
          onChange={(event) => change("name", event.target.value)} required maxLength={200} /></label>
        <label>Kurum<input value={draft.institutionName}
          onChange={(event) => change("institutionName", event.target.value)} required /></label>
        <label>İhale kayıt no<input value={draft.tenderRegistrationNumber}
          onChange={(event) => change("tenderRegistrationNumber", event.target.value)} /></label>
        <label>İhale türü<input value={draft.tenderType}
          onChange={(event) => change("tenderType", event.target.value)} /></label>
        <label>İş türü<input value={draft.businessType}
          onChange={(event) => change("businessType", event.target.value)} /></label>
        <label>Sektör<input value={draft.sector}
          onChange={(event) => change("sector", event.target.value)} /></label>
        <label>Öncelik<select value={draft.priority}
          onChange={(event) => change("priority", event.target.value)}>
          <option value="LOW">Düşük</option><option value="NORMAL">Normal</option>
          <option value="HIGH">Yüksek</option><option value="CRITICAL">Kritik</option>
        </select></label>
      </div>}
      {step === 2 && <div className="form-grid">
        <label>Son teklif tarihi<input type="date" value={draft.bidDeadline}
          onChange={(event) => change("bidDeadline", event.target.value)} /></label>
        <label>Soru sorma sonu<input type="date" value={draft.clarificationDeadline}
          onChange={(event) => change("clarificationDeadline", event.target.value)} /></label>
        <label>Para birimi<input value={draft.currency}
          onChange={(event) => change("currency", event.target.value.toUpperCase())}
          pattern="[A-Z]{3}" maxLength={3} /></label>
        <label className="wide">Açıklama<textarea rows={5} value={draft.description}
          onChange={(event) => change("description", event.target.value)} /></label>
      </div>}
      {step === 3 && <div className="phase-note"><Building2 />
        <h3>Firma ve ürün eşleştirme sonraki fazda</h3>
        <p>Bu modül henüz mevcut olmadığı için sahte seçenek gösterilmiyor.
          Proje oluşturulduktan sonra daha sonra eklenebilecek.</p></div>}
      {step === 4 && <div className="phase-note"><Users />
        <h3>Proje sahibi otomatik eklenir</h3>
        <p>Oturum açan kullanıcı OWNER rolü ve tam proje yetkileriyle üye yapılır.
          Diğer ekip üyelerini proje ayarlarından ekleyebilirsiniz.</p></div>}
      <div className="wizard-actions">
        {step > 1 && <button type="button" className="secondary"
          onClick={() => onStep(step - 1)}>Geri</button>}
        <button className="primary" disabled={busy}>
          {busy ? <LoaderCircle className="spin" /> : step === 4 ? <Plus /> : <ChevronRight />}
          {step === 4 ? "Projeyi oluştur" : "Devam"}
        </button>
      </div>
    </form>
  </div>;
}
