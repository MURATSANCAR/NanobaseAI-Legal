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


import { Empty, LoadingPanel, formatComplianceValue, conceptMetadata, EvidenceDocumentPane } from "./_shared";

export function ComplianceWorkspace({ project, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [evaluations, setEvaluations] = useState<ComplianceEvaluation[]>([]);
  const [columns, setColumns] = useState<ComplianceColumn[]>([]);
  const [decisions, setDecisions] = useState<ConceptOption[]>([]);
  const [changeTypes, setChangeTypes] = useState<ConceptOption[]>([]);
  const [selected, setSelected] = useState<ComplianceEvaluationDetail>();
  const [selectedEvidence, setSelectedEvidence] = useState<EvidenceFragment>();
  const [decisionId, setDecisionId] = useState("");
  const [reason, setReason] = useState("");
  const [job, setJob] = useState<ComplianceJob>();
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [next, matrix, nextDecisions, nextChangeTypes] = await Promise.all([
        complianceApi.evaluations(token, project.id),
        complianceApi.matrix(token),
        complianceApi.decisions(token),
        complianceApi.changeTypes(token),
      ]);
      setEvaluations(next);
      setColumns(matrix.columns);
      setDecisions(nextDecisions);
      setChangeTypes(nextChangeTypes);
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
    if (!job || ["COMPLETED", "FAILED", "CANCELLED"].includes(job.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const current = await complianceApi.job(token, job.id);
        setJob(current);
        if (current.status === "COMPLETED") {
          window.clearInterval(timer);
          await load();
          onNotify(`${current.completed_count} requirement değerlendirildi`);
        }
      } catch (error) {
        window.clearInterval(timer);
        onProblem(error);
      }
    }, 2200);
    return () => window.clearInterval(timer);
  }, [job, load, onNotify, onProblem, token]);

  async function selectEvaluation(evaluation: ComplianceEvaluation) {
    try {
      const detail = await complianceApi.evaluation(token, evaluation.id);
      setSelected(detail);
      setDecisionId(
        decisions.find((item) => item.code ===
          (detail.final_decision || detail.suggested_decision))?.id ?? "",
      );
      setSelectedEvidence(detail.evidence[0] as EvidenceFragment | undefined);
    } catch (error) {
      onProblem(error);
    }
  }

  async function start() {
    try {
      setJob(await complianceApi.start(token, project.id));
      onNotify("Knowledge snapshot alındı; compliance analizi kuyruğa girdi");
    } catch (error) {
      onProblem(error);
    }
  }

  async function review() {
    const changeType = changeTypes.find((item) =>
      conceptMetadata(item).changeProvider === "EVALUATION_REVIEWED");
    if (!selected || !decisionId || !reason.trim() || !changeType) return;
    try {
      const updated = await complianceApi.review(token, selected.id, {
        finalDecisionConceptId: decisionId,
        changeTypeConceptId: changeType.id,
        reason,
      });
      setSelected(updated);
      setReason("");
      await load();
      onNotify("Uzman kararı revision ve feedback olarak kaydedildi");
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="compliance-center card-static">
    <div className="panel knowledge-toolbar">
      <div><p className="eyebrow">EVIDENCE-FIRST</p><h2>Uygunluk çalışma alanı</h2>
        <span>Requirement, kanıt ve uzman kararı aynı bağlamda.</span></div>
      {canWrite && <button className="primary" onClick={start}
        disabled={Boolean(job && !["COMPLETED", "FAILED", "CANCELLED"].includes(job.status))}>
        <ShieldCheck />Analizi başlat</button>}
    </div>
    {job && <div className="extraction-progress">
      <div><b>{["QUEUED", "RUNNING"].includes(job.status)
        ? "Analiz devam ediyor" : job.status.replaceAll("_", " ")}</b>
        <span>İşlenen requirement: {job.processed_requirement_count} /
          {" "}{job.total_requirement_count}
          {" "}· {job.manual_review_count} uzman incelemesi · {job.failed_count} hata</span></div>
      <progress max={Math.max(job.total_requirement_count, 1)}
        value={job.processed_requirement_count} />
    </div>}
    <div className="compliance-layout">
      <aside className="panel requirement-rail">
        <p className="eyebrow">REQUIREMENT VE KOŞULLAR</p>
        {evaluations.map((evaluation) => <button key={evaluation.id}
          className={selected?.id === evaluation.id ? "evaluation-row active" : "evaluation-row"}
          onClick={() => selectEvaluation(evaluation)}>
          <span>{evaluation.requirementCode}</span>
          <b>{evaluation.requirementText}</b>
          <small>{evaluation.requirementConcept || "Concept yok"} ·
            {" "}%{Math.round(evaluation.combinedConfidence * 100)}</small>
          <em>{evaluation.finalDecision || evaluation.suggestedDecision}</em>
        </button>)}
        {!loading && !evaluations.length &&
          <Empty text="Henüz compliance değerlendirmesi yok." />}
      </aside>
      <main className="panel compliance-evidence-panel">
        <p className="eyebrow">ADAY VE SEÇİLMİŞ EVIDENCE</p>
        {selected ? <>
          <div className="evidence-chips">
            {selected.evidence.map((item) => <button key={item.id}
              className={item.contradiction_strength > 0 ? "contradiction" : "support"}
              onClick={() => setSelectedEvidence(item)}>
              <b>{item.relation_role}</b>
              <span>Sayfa {item.page_number ?? "—"}</span>
              <small>%{Math.round(item.relevance_score * 100)} relevance ·
                {" "}%{Math.round(item.validity_score * 100)} validity</small>
            </button>)}
          </div>
          <EvidenceDocumentPane evidence={selectedEvidence} token={token} />
        </> : <Empty text="Evidence’i görmek için bir requirement seçin." />}
      </main>
      <aside className="panel decision-panel">
        <p className="eyebrow">AI ÖN DEĞERLENDİRMESİ VE UZMAN KARARI</p>
        {selected ? <>
          <dl>
            <div><dt>Sonuç etiketi</dt><dd>AI Ön Değerlendirmesi</dd></div>
            <div><dt>Önerilen karar</dt><dd>{selected.suggested_decision}</dd></div>
            <div><dt>Final karar</dt><dd>{selected.final_decision || "Uzman incelemesi bekliyor"}</dd></div>
            <div><dt>Grounding</dt><dd>{selected.grounding_status}</dd></div>
            <div><dt>Confidence</dt><dd>
              %{Math.round(selected.combined_confidence * 100)}</dd></div>
            <div><dt>Review</dt><dd>{selected.review_status}</dd></div>
          </dl>
          <pre className="json-panel compact">{typeof selected.comparison_summary_json === "string"
            ? selected.comparison_summary_json
            : JSON.stringify(selected.comparison_summary_json, null, 2)}</pre>
          {canWrite && <div className="review-form">
            <label>Uzman kararı<select value={decisionId}
              onChange={(event) => setDecisionId(event.target.value)}>
              <option value="">Karar concept’i seçin</option>
              {decisions.map((decision) =>
                <option key={decision.id} value={decision.id}>{decision.name}</option>)}
            </select></label>
            <label>Gerekçe<textarea value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Karar gerekçesi ve eksik belge notu" /></label>
            {(!changeTypes.some((item) =>
              conceptMetadata(item).changeProvider === "EVALUATION_REVIEWED")) && (
              <p className="field-hint">Değerlendirme onay tipi yapılandırılmadığı için karar kaydı kapalı.</p>
            )}
            <button className="primary" onClick={review}
              disabled={
                !decisionId ||
                !reason.trim() ||
                !changeTypes.some((item) =>
                  conceptMetadata(item).changeProvider === "EVALUATION_REVIEWED")
              }><CheckCircle2 />Kararı kaydet</button>
          </div>}
        </> : <Empty text="Değerlendirme seçin." />}
      </aside>
    </div>
    <section className="panel compliance-matrix">
      <div className="panel-head"><div><b>Backend konfigürasyonlu uygunluk matrisi</b>
        <span>Yeni kolonlar frontend rebuild gerektirmeden render edilir.</span></div></div>
      <div className="table-wrap"><table><thead><tr>{columns.map((column) =>
        <th key={column.key}>{column.label}</th>)}</tr></thead>
        <tbody>{evaluations.map((evaluation) => <tr key={evaluation.id}>
          {columns.map((column) => <td key={column.key}>
            {formatComplianceValue(evaluation, column)}</td>)}</tr>)}</tbody>
      </table></div>
    </section>
  </section>;
}
