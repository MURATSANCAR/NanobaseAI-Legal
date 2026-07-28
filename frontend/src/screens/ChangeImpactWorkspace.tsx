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


import { Empty, LoadingPanel, JsonList, Score } from "./_shared";

export function ChangeImpactWorkspace({ documents, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [documentId, setDocumentId] = useState("");
  const [versions, setVersions] = useState<Array<{ id: string; versionNumber: number }>>([]);
  const [baseVersionId, setBaseVersionId] = useState("");
  const [targetVersionId, setTargetVersionId] = useState("");
  const [changeSet, setChangeSet] = useState<ChangeSet>();
  const [impact, setImpact] = useState<ImpactAnalysis>();
  const [selectedItem, setSelectedItem] = useState<ChangeItem>();
  const [baseClauseId, setBaseClauseId] = useState("");
  const [targetClauseId, setTargetClauseId] = useState("");

  useEffect(() => {
    if (!documentId) {
      const timer = window.setTimeout(() => setVersions([]), 0);
      return () => window.clearTimeout(timer);
    }
    documentApi.versions(token, documentId).then((items) => {
      setVersions(items);
      setTargetVersionId(items[0]?.id ?? "");
      setBaseVersionId(items[1]?.id ?? "");
    }).catch(onProblem);
  }, [documentId, onProblem, token]);

  async function create() {
    try {
      const created = await riskApi.createChangeSet(
        token, documentId, baseVersionId, targetVersionId);
      setChangeSet(created);
      setImpact(undefined);
      onNotify(`${created.items.length} yapısal değişiklik eşleştirildi`);
    } catch (error) {
      onProblem(error);
    }
  }

  async function analyze() {
    if (!changeSet) return;
    try {
      const result = await riskApi.impact(token, changeSet.id);
      setImpact(result);
      onNotify(`${result.affectedEntities.length} seçici etki kaydedildi`);
    } catch (error) {
      onProblem(error);
    }
  }

  async function correct() {
    if (!changeSet || !selectedItem) return;
    try {
      const updated = await riskApi.correctChange(token, changeSet.id, selectedItem,
        baseClauseId, targetClauseId);
      setChangeSet(updated);
      setSelectedItem(undefined);
      onNotify("Değişiklik eşleşmesi uzman kararıyla güncellendi");
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="workspace-stack">
    <article className="panel change-controls">
      <div className="panel-head"><div><b>Doküman değişikliği ve etki</b>
        <span>Yapısal diff → requirement graph → seçici re-analysis</span></div></div>
      <div className="change-form">
        <label>Doküman<select value={documentId}
          onChange={(event) => setDocumentId(event.target.value)}>
          <option value="">Doküman seçin</option>
          {documents.map((document) => <option key={document.id} value={document.id}>
            {document.logicalName}</option>)}
        </select></label>
        <label>Eski versiyon<select value={baseVersionId}
          onChange={(event) => setBaseVersionId(event.target.value)}>
          <option value="">Versiyon seçin</option>{versions.map((version) =>
            <option key={version.id} value={version.id}>v{version.versionNumber}</option>)}
        </select></label>
        <label>Yeni versiyon<select value={targetVersionId}
          onChange={(event) => setTargetVersionId(event.target.value)}>
          <option value="">Versiyon seçin</option>{versions.map((version) =>
            <option key={version.id} value={version.id}>v{version.versionNumber}</option>)}
        </select></label>
        {canWrite && <button className="primary" onClick={create}
          disabled={!documentId || !baseVersionId || !targetVersionId ||
            baseVersionId === targetVersionId}><FileClock />Değişikliği analiz et</button>}
      </div>
    </article>
    {changeSet && <div className="change-grid">
      <article className="panel change-items">
        <div className="panel-head"><div><b>Change set</b>
          <span>{changeSet.items.length} madde · {changeSet.status}</span></div>
          {canWrite && <button className="primary" onClick={analyze}>
            <Activity />Etki analizi</button>}</div>
        {changeSet.items.map((item) => <button key={item.id}
          className={selectedItem?.id === item.id ? "change-item active" : "change-item"}
          onClick={() => {
            setSelectedItem(item);
            setBaseClauseId(item.baseClauseId ?? "");
            setTargetClauseId(item.targetClauseId ?? "");
          }}>
          <span className={`change-badge ${item.changeType.toLowerCase()}`}>
            {item.changeType}</span>
          <b>{item.baseClauseId || "∅"} → {item.targetClauseId || "∅"}</b>
          <small>%{Math.round(item.similarityScore * 100)} benzerlik ·
            {" "}{item.reviewStatus}</small>
        </button>)}
      </article>
      <article className="panel impact-results">
        <div className="panel-head"><div><b>Etkilenen sonuçlar</b>
          <span>Eski sonuçlar silinmez; stale işaretlenir</span></div></div>
        {impact?.affectedEntities.map((entity) => <div className="impact-row" key={entity.id}>
          <span>{entity.entityType}</span><b>{entity.impactConcept}</b>
          <small>%{Math.round(entity.confidence * 100)} güven</small>
        </div>)}
        {!impact && <Empty text="Etki analizi henüz çalıştırılmadı." />}
      </article>
    </div>}
    {selectedItem && <div className="modal-backdrop">
      <section className="modal change-correction">
        <div className="modal-head"><div><p className="eyebrow">UZMAN EŞLEŞME DÜZELTMESİ</p>
          <h2>{selectedItem.changeType}</h2></div>
          <button onClick={() => setSelectedItem(undefined)} aria-label="Kapat"><X /></button></div>
        <label>Eski clause ID<input value={baseClauseId}
          onChange={(event) => setBaseClauseId(event.target.value)} /></label>
        <label>Yeni clause ID<input value={targetClauseId}
          onChange={(event) => setTargetClauseId(event.target.value)} /></label>
        <p>Boş taraf silinen veya eklenen maddeyi temsil eder.</p>
        <button className="primary" onClick={correct}><CheckCircle2 />Eşleşmeyi kaydet</button>
      </section>
    </div>}
  </section>;
}
