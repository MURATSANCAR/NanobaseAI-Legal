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


import { Empty, EmptyState, LoadingPanel, type Sprint7Tab } from "./_shared";

export function Sprint7Workspace({ token, project, canConfigure, canWrite, onProblem, onNotify }: {
  token: string;
  project: TenderProject | null;
  canConfigure: boolean;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [tab, setTab] = useState<Sprint7Tab>("tasks");
  const [loading, setLoading] = useState(true);
  const [tasks, setTasks] = useState<TaskRecord[]>([]);
  const [taskColumns, setTaskColumns] = useState<Array<{
    key: string; label: string; visible: boolean;
  }>>([]);
  const [taskStatuses, setTaskStatuses] = useState<WorkflowConcept[]>([]);
  const [, setCommentVisibility] = useState<WorkflowConcept[]>([]);
  const [approvals, setApprovals] = useState<ApprovalRequest[]>([]);
  const [clarifications, setClarifications] = useState<ClarificationCenter>({
    requests: [],
    candidates: [],
  });
  const [clarificationStatuses, setClarificationStatuses] =
    useState<WorkflowConcept[]>([]);
  const [definitions, setDefinitions] = useState<WorkflowDefinition[]>([]);
  const [nodeTypes, setNodeTypes] = useState<WorkflowConcept[]>([]);
  const [transitionTypes, setTransitionTypes] = useState<WorkflowConcept[]>([]);
  const [workflowNodes, setWorkflowNodes] = useState<WorkflowNodeDraft[]>([]);
  const [workflowTransitions, setWorkflowTransitions] =
    useState<WorkflowTransitionDraft[]>([]);
  const [workflowCode, setWorkflowCode] = useState("TENANT_REVIEW");
  const [workflowName, setWorkflowName] = useState("Tenant inceleme akışı");
  const [selectedWorkflowId, setSelectedWorkflowId] = useState("");
  const [conditionText, setConditionText] = useState("{}");
  const [lastWorkflowVersionId, setLastWorkflowVersionId] = useState("");
  const [simulation, setSimulation] = useState<SimulationResult | null>(null);
  const [reportDefinitions, setReportDefinitions] = useState<ReportDefinition[]>([]);
  const [reportJobs, setReportJobs] = useState<ReportJob[]>([]);
  const [reportFormats, setReportFormats] = useState<WorkflowConcept[]>([]);
  const [sectionTypes, setSectionTypes] = useState<WorkflowConcept[]>([]);
  const [reportConfig, setReportConfig] = useState<{
    dataPolicies: Array<{ id: string; code: string; versionNumber: number }>;
    templates: Array<{
      id: string; code: string; versionNumber: number;
      formatConceptCode: string; formatConceptId: string; language: string;
    }>;
  }>({ dataPolicies: [], templates: [] });
  const [reportName, setReportName] = useState("Yönetici değerlendirme raporu");
  const [reportCode, setReportCode] = useState("EXECUTIVE_REVIEW");
  const [selectedReportId, setSelectedReportId] = useState("");
  const [lastReportVersionId, setLastReportVersionId] = useState("");
  const [preview, setPreview] = useState<Record<string, unknown> | null>(null);
  const [decisionCases, setDecisionCases] = useState<DecisionSupportCase[]>([]);
  const [decisionPolicies, setDecisionPolicies] = useState<Array<{
    id: string; code: string; versionNumber: number;
  }>>([]);
  const [decisionOptions, setDecisionOptions] = useState<WorkflowConcept[]>([]);
  const [dashboard, setDashboard] = useState<DynamicDashboard | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [
        loadedTasks,
        grid,
        loadedTaskStatuses,
        visibility,
        loadedApprovals,
        loadedDefinitions,
        loadedNodeTypes,
        loadedTransitionTypes,
        loadedClarificationStatuses,
        loadedReports,
        loadedFormats,
        loadedSections,
        designer,
        policies,
        decisions,
        configuredDashboard,
      ] = await Promise.all([
        workApi.tasks(token, project?.id),
        workApi.taskGrid(token),
        workflowApi.concepts(token, "TASK_STATUS"),
        workflowApi.concepts(token, "COMMENT_VISIBILITY"),
        workApi.approvals(token, project?.id),
        workflowApi.definitions(token),
        workflowApi.concepts(token, "WORKFLOW_NODE_TYPE"),
        workflowApi.concepts(token, "WORKFLOW_TRANSITION"),
        workflowApi.concepts(token, "CLARIFICATION_STATUS"),
        reportingApi.definitions(token),
        workflowApi.concepts(token, "REPORT_FORMAT"),
        workflowApi.concepts(token, "REPORT_SECTION_TYPE"),
        reportingApi.designerConfiguration(token),
        decisionApi.policies(token),
        workflowApi.concepts(token, "EXECUTIVE_DECISION"),
        workflowApi.dashboard(token),
      ]);
      setTasks(loadedTasks);
      setTaskColumns(grid.columns);
      setTaskStatuses(loadedTaskStatuses);
      setCommentVisibility(visibility);
      setApprovals(loadedApprovals);
      setDefinitions(loadedDefinitions);
      setSelectedWorkflowId((current) => current || loadedDefinitions[0]?.id || "");
      setNodeTypes(loadedNodeTypes);
      setTransitionTypes(loadedTransitionTypes);
      setClarificationStatuses(loadedClarificationStatuses);
      setReportDefinitions(loadedReports);
      setSelectedReportId((current) => current || loadedReports[0]?.id || "");
      setReportFormats(loadedFormats);
      setSectionTypes(loadedSections);
      setReportConfig(designer);
      setDecisionPolicies(policies);
      setDecisionOptions(decisions);
      setDashboard(configuredDashboard);
      if (project) {
        const [loadedClarifications, jobs, cases] = await Promise.all([
          workApi.clarifications(token, project.id),
          reportingApi.jobs(token, project.id),
          decisionApi.list(token, project.id),
        ]);
        setClarifications(loadedClarifications);
        setReportJobs(jobs);
        setDecisionCases(cases);
      } else {
        setClarifications({ requests: [], candidates: [] });
        setReportJobs([]);
        setDecisionCases([]);
      }
    } catch (error) {
      onProblem(error);
    } finally {
      setLoading(false);
    }
  }, [onProblem, project, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  const statusFor = useCallback((effect: string) =>
    taskStatuses.find((status) => status.metadata?.actionEffect === effect), [taskStatuses]);
  const clarificationStatusFor = useCallback((effect: string) =>
    clarificationStatuses.find((status) => status.metadata?.actionEffect === effect),
  [clarificationStatuses]);

  async function action(run: () => Promise<unknown>, message: string) {
    setBusy(true);
    try {
      await run();
      onNotify(message);
      await load();
    } catch (error) {
      onProblem(error);
    } finally {
      setBusy(false);
    }
  }

  function addWorkflowNode() {
    const type = nodeTypes[0];
    if (!type) return;
    const index = workflowNodes.length + 1;
    setWorkflowNodes((current) => [...current, {
      nodeCode: `NODE_${index}`,
      nodeTypeConceptId: type.id,
      name: `Adım ${index}`,
      configuration: {
        entry: index === 1,
        terminal: false,
        executionBehavior: "COMPLETE",
      },
      sortOrder: index,
    }]);
  }

  function connectWorkflowNodes() {
    const source = workflowNodes.at(-2);
    const target = workflowNodes.at(-1);
    const transition = transitionTypes[0];
    if (!source || !target || !transition) return;
    let condition: Record<string, unknown> = {};
    try {
      condition = JSON.parse(conditionText) as Record<string, unknown>;
    } catch {
      onProblem(new Error("Koşul geçerli JSON olmalıdır."));
      return;
    }
    setWorkflowTransitions((current) => [...current, {
      sourceNodeCode: source.nodeCode,
      targetNodeCode: target.nodeCode,
      transitionConceptId: transition.id,
      condition,
      actionConfiguration: {},
      priority: 0,
    }]);
  }

  async function saveWorkflowVersion() {
    let definitionId = selectedWorkflowId;
    if (!definitionId) {
      const created = await workflowApi.createDefinition(token, {
        workflowCode, name: workflowName, description: "UI ile oluşturulan dinamik akış",
        scope: "ORGANIZATION",
      });
      definitionId = created.id;
      setSelectedWorkflowId(created.id);
    }
    if (!workflowNodes.some((node) => node.configuration.terminal === true)) {
      throw new Error("En az bir node terminal olarak işaretlenmelidir.");
    }
    const version = await workflowApi.createVersion(
      token, definitionId, workflowNodes, workflowTransitions,
    );
    setLastWorkflowVersionId(version.id);
    const result = await workflowApi.simulate(token, version.id, {
      project: { criticalRiskCount: 0, pendingReviewCount: 0 },
    });
    setSimulation(result);
  }

  async function saveReportVersion() {
    let definitionId = selectedReportId;
    if (!definitionId) {
      const created = await reportingApi.createDefinition(token, {
        reportCode, name: reportName, description: "Dinamik snapshot raporu",
        scope: "ORGANIZATION",
      });
      definitionId = created.id;
      setSelectedReportId(created.id);
    }
    const policy = reportConfig.dataPolicies[0];
    const template = reportConfig.templates[0];
    const section = sectionTypes[0];
    if (!policy || !template || !section) {
      throw new Error("Aktif rapor policy, template ve section concept gereklidir.");
    }
    const version = await reportingApi.createVersion(token, definitionId, {
      sectionConfiguration: {},
      dataPolicyVersionId: policy.id,
      templateVersionId: template.id,
      sections: [{
        sectionCode: "DYNAMIC_SUMMARY",
        sectionTypeConceptId: section.id,
        titleTemplate: "Doğrulanmış proje özeti",
        dataQueryConfiguration: { snapshotJsonPointer: "/counts" },
        renderConfiguration: {},
        visibilityCondition: {},
        sortOrder: 1,
      }],
    });
    setLastReportVersionId(version.id);
    if (project) {
      setPreview(await reportingApi.preview(token, version.id, project.id));
    }
  }

  const centerTabs: Array<[Sprint7Tab, string]> = [
    ["tasks", "Görevler"],
    ["approvals", "Onaylar"],
    ["clarifications", "Açıklamalar"],
    ["workflow-designer", "Workflow tasarımı"],
    ["report-designer", "Raporlar"],
    ["decision-support", "Yönetici kararı"],
  ];

  return (
    <PageShell
      title="Workflow ve karar merkezi"
      subtitle={
        project
          ? `${project.projectCode} · ${project.name}`
          : "Proje özelindeki merkezler için önce bir proje açın."
      }
      icon={<ClipboardCheck className="h-4 w-4" />}
      heroTrailing={
        <button className="btn-secondary" onClick={() => void load()} disabled={loading}>
          <RefreshCw /> Yenile
        </button>
      }
      maxWidth="max-w-[1460px]"
    >
    {dashboard && <section className="dynamic-widget-strip"
      aria-label="Backend yapılandırmalı dashboard">
      {dashboard.widgets.map((widget) => <article key={widget.code}>
        <span>{widget.typeConceptCode}</span>
        <b>{widget.display.title ?? widget.code}</b>
        <small>{widget.dataSource.entity ?? "Dinamik veri kaynağı"}</small>
      </article>)}
    </section>}
    <nav className="tabs sprint7-tabs" aria-label="Workflow merkezi bölümleri">
      {centerTabs.map(([value, label]) => <button key={value}
        className={tab === value ? "active" : ""} onClick={() => setTab(value)}>
        {label}
      </button>)}
    </nav>
    {loading ? <LoadingPanel /> : <>
      {tab === "tasks" && <section className="panel sprint7-panel">
        <div className="panel-head"><div><b>Görev merkezi</b>
          <span>Kolonlar backend dashboard yapılandırmasından gelir.</span></div>
          <ClipboardCheck /></div>
        <div className="requirement-table-wrap mobile-table-scroll">
          <table><thead><tr>
            {taskColumns.filter((column) => column.visible).map((column) =>
              <th key={column.key}>{column.label}</th>)}
            <th>İşlem</th>
          </tr></thead><tbody>
            {tasks.map((task) => <tr key={task.id}>
              {taskColumns.filter((column) => column.visible).map((column) =>
                <td key={column.key}>{workflowCell(task, column.key)}</td>)}
              <td><div className="inline-actions">
                {!task.assignedUserId && <button className="secondary"
                  disabled={!canWrite || busy}
                  onClick={() => void action(() => workApi.claim(token, task.id),
                    "Görev devralındı")}>Devral</button>}
                {!task.completedAt && statusFor("complete") && <button className="primary"
                  disabled={!canWrite || busy}
                  onClick={() => void action(() => workApi.complete(
                    token, task.id, statusFor("complete")!.id,
                  ), "Görev tamamlandı")}>Tamamla</button>}
              </div></td>
            </tr>)}
          </tbody></table>
          {!tasks.length && <EmptyState icon={ClipboardCheck}
            title="Görev bulunmuyor" body="Workflow task node’u çalıştığında burada görünür." />}
        </div>
      </section>}

      {tab === "approvals" && <section className="sprint7-grid">
        {approvals.map((approval) => <article className="panel decision-card" key={approval.id}>
          <div className="panel-head"><div><b>{approval.subjectEntityType}</b>
            <span>{approval.statusConceptCode} · {formatDateTime(approval.requestedAt)}</span>
          </div><ShieldCheck /></div>
          <div className="decision-body">
            <p>Gerekli adımlar: {approval.steps.length} · Önceki kararlar: {
              approval.decisions.length}</p>
            <p className="muted">Talep eden: {approval.requestedBy}</p>
            <div className="decision-options">
              {approval.availableDecisions.map((option) => <button
                className="secondary" key={option.id} disabled={!canWrite || busy}
                onClick={() => void action(() => workApi.decide(
                  token, approval.id, String(approval.steps[0]?.id ?? ""),
                  option.id, `${option.name} kararı kullanıcı tarafından kaydedildi.`,
                ), "Onay kararı kaydedildi")}>{option.name}</button>)}
            </div>
          </div>
        </article>)}
        {!approvals.length && <EmptyState icon={ShieldCheck}
          title="Bekleyen onay yok" body="Karar seçenekleri her onayda backend’den yüklenir." />}
      </section>}

      {tab === "clarifications" && <section className="sprint7-grid">
        {clarifications.candidates.map((candidate) => <article className="panel decision-card"
          key={candidate.id}>
          <div className="panel-head"><div><b>Aday soru</b>
            <span>{candidate.sourceType} · {candidate.reviewStatus}</span></div>
            <AlertTriangle /></div>
          <div className="decision-body"><p>{candidate.question}</p>
            <small>{candidate.reason}</small>
            <button className="primary" disabled={!canWrite || busy}
              onClick={() => void action(async () => {
                await workApi.reviseClarification(token, candidate.id,
                  candidate.question, candidate.reason);
                const review = clarificationStatusFor("review");
                if (review) await workApi.clarificationStatus(
                  token, candidate.id, "submit-review", review.id,
                );
              }, "Açıklama incelemeye gönderildi")}>Sürece al</button>
          </div>
        </article>)}
        {clarifications.requests.map((request) => <article className="panel decision-card"
          key={request.id}>
          <div className="panel-head"><div><b>{request.questionCode}</b>
            <span>{request.statusConceptCode}</span></div><FileClock /></div>
          <div className="decision-body"><p>{request.questionText}</p>
            <small>{request.reason}</small>
            <div className="inline-actions">
              {clarificationStatusFor("approve") && <button className="secondary"
                disabled={!canWrite || busy}
                onClick={() => void action(() => workApi.clarificationStatus(
                  token, request.id, "approve", clarificationStatusFor("approve")!.id,
                ), "Açıklama onaylandı")}>Onayla</button>}
              {request.approvedVersionId && clarificationStatusFor("send") &&
                <button className="primary" disabled={!canWrite || busy}
                  onClick={() => void action(() => workApi.clarificationStatus(
                    token, request.id, "mark-sent", clarificationStatusFor("send")!.id,
                  ), "Açıklama gönderildi olarak işaretlendi")}>Gönderildi</button>}
            </div>
          </div>
        </article>)}
        {!project && <EmptyState icon={FileClock} title="Proje seçin"
          body="Açıklama adayları ve cevap etkileri proje kapsamında gösterilir." />}
      </section>}

      {tab === "workflow-designer" && <section className="designer-layout">
        <article className="panel designer-toolbox">
          <div className="panel-head"><div><b>Workflow tanımı</b>
            <span>Draft → simulate → activate</span></div><BrainCircuit /></div>
          <div className="form-stack">
            <label>Kayıtlı workflow<select value={selectedWorkflowId}
              onChange={(event) => setSelectedWorkflowId(event.target.value)}>
              <option value="">Yeni workflow</option>
              {definitions.map((definition) => <option key={definition.id}
                value={definition.id}>{definition.name}</option>)}
            </select></label>
            {!selectedWorkflowId && <>
              <label>Kod<input value={workflowCode}
                onChange={(event) => setWorkflowCode(event.target.value)} /></label>
              <label>Ad<input value={workflowName}
                onChange={(event) => setWorkflowName(event.target.value)} /></label>
            </>}
            <button className="secondary" onClick={addWorkflowNode}
              disabled={!canConfigure || !nodeTypes.length}><Plus /> Node ekle</button>
            <label>Son geçiş koşulu<textarea value={conditionText}
              onChange={(event) => setConditionText(event.target.value)} /></label>
            <button className="secondary" onClick={connectWorkflowNodes}
              disabled={workflowNodes.length < 2 || !transitionTypes.length}>
              Adımları bağla
            </button>
            <button className="primary" disabled={!canConfigure || busy || !workflowNodes.length}
              onClick={() => void action(saveWorkflowVersion,
                "Workflow draft kaydedildi ve simüle edildi")}>
              Validate ve simulate
            </button>
            {lastWorkflowVersionId && simulation?.result.valid && <button
              className="primary" disabled={!canConfigure || busy}
              onClick={() => void action(() => workflowApi.activate(
                token, lastWorkflowVersionId,
              ), "Workflow versiyonu aktive edildi")}>Aktive et</button>}
          </div>
        </article>
        <article className="workflow-canvas panel">
          <div className="panel-head"><div><b>Grafik</b>
            <span>{workflowNodes.length} node · {workflowTransitions.length} transition</span>
          </div><Activity /></div>
          <div className="workflow-node-list">
            {workflowNodes.map((node, index) => <div className="workflow-node" key={node.nodeCode}>
              <span>{index + 1}</span>
              <input value={node.name} onChange={(event) => setWorkflowNodes((current) =>
                current.map((item) => item.nodeCode === node.nodeCode
                  ? { ...item, name: event.target.value } : item))} />
              <select value={node.nodeTypeConceptId}
                onChange={(event) => setWorkflowNodes((current) => current.map((item) =>
                  item.nodeCode === node.nodeCode
                    ? { ...item, nodeTypeConceptId: event.target.value } : item))}>
                {nodeTypes.map((type) => <option value={type.id} key={type.id}>
                  {type.name}</option>)}
              </select>
              <label><input type="checkbox"
                checked={node.configuration.terminal === true}
                onChange={(event) => setWorkflowNodes((current) => current.map((item) =>
                  item.nodeCode === node.nodeCode ? {
                    ...item,
                    configuration: { ...item.configuration, terminal: event.target.checked },
                  } : item))} /> Final</label>
            </div>)}
          </div>
          {simulation && <div className={simulation.result.valid
            ? "simulation-result success" : "simulation-result error"}>
            <b>{simulation.result.valid ? "Simulation başarılı" : "Simulation engellendi"}</b>
            {simulation.result.findings.map((finding) =>
              <p key={`${finding.code}-${finding.nodeCode}`}>{finding.severity} · {
                finding.code}: {finding.message}</p>)}
          </div>}
        </article>
      </section>}

      {tab === "report-designer" && <section className="designer-layout">
        <article className="panel designer-toolbox">
          <div className="panel-head"><div><b>Rapor tasarımı</b>
            <span>Versiyonlu bölüm, policy ve template</span></div><FileText /></div>
          <div className="form-stack">
            <label>Rapor tanımı<select value={selectedReportId}
              onChange={(event) => setSelectedReportId(event.target.value)}>
              <option value="">Yeni rapor</option>
              {reportDefinitions.map((definition) => <option value={definition.id}
                key={definition.id}>{definition.name}</option>)}
            </select></label>
            {!selectedReportId && <>
              <label>Kod<input value={reportCode}
                onChange={(event) => setReportCode(event.target.value)} /></label>
              <label>Ad<input value={reportName}
                onChange={(event) => setReportName(event.target.value)} /></label>
            </>}
            <p className="muted">Section: {sectionTypes[0]?.name ?? "—"}<br />
              Policy: {reportConfig.dataPolicies[0]?.code ?? "—"}<br />
              Template: {reportConfig.templates[0]?.code ?? "—"}</p>
            <button className="primary" disabled={!canConfigure || busy}
              onClick={() => void action(saveReportVersion,
                "Rapor draft ve preview oluşturuldu")}>Draft ve preview</button>
            {lastReportVersionId && <button className="secondary"
              disabled={!canConfigure || busy}
              onClick={() => void action(() => reportingApi.activate(
                token, lastReportVersionId,
              ), "Rapor versiyonu aktive edildi")}>Versiyonu yayınla</button>}
          </div>
        </article>
        <article className="panel report-preview">
          <div className="panel-head"><div><b>Snapshot preview</b>
            <span>Canlı veri yerine sabitlenmiş yapılandırılmış görünüm</span></div><Eye /></div>
          {preview ? <pre>{JSON.stringify(preview, null, 2)}</pre>
            : <EmptyState icon={Eye} title="Preview bekleniyor"
              body="Aktif proje ile draft oluşturduğunuzda preview burada görünür." />}
          {project && reportDefinitions.filter((definition) => definition.activeVersionId)
            .map((definition) => <button className="primary report-generate"
              key={definition.id} disabled={!canWrite || busy || !reportFormats.length}
              onClick={() => void action(() => reportingApi.generate(
                token, project.id, definition.activeVersionId!,
                reportFormats.map((format) => format.id),
              ), "PDF, DOCX ve XLSX rapor üretimi tamamlandı")}>
              {definition.name} üret
            </button>)}
          {reportJobs.map((job) => <div className="artifact-job" key={job.id}>
            <b>{job.statusConceptCode} · %{job.progress}</b>
            {job.artifacts.map((artifact) => <button className="secondary" key={artifact.id}
              onClick={() => void reportingApi.downloadUrl(token, artifact.id)
                .then((value) => window.open(value.url, "_blank", "noopener,noreferrer"))
                .catch(onProblem)}><Download /> {artifact.formatConceptCode}</button>)}
          </div>)}
        </article>
      </section>}

      {tab === "decision-support" && <section className="sprint7-grid">
        {project && !decisionCases.length && <article className="panel decision-card">
          <div className="panel-head"><div><b>Yeni karar destek vakası</b>
            <span>Yalnız doğrulanmış snapshot verisi kullanılır.</span></div><Gauge /></div>
          <div className="decision-body"><p>Öneri nihai karar değildir; yönetici kararı
            ayrıca ve insan tarafından kaydedilir.</p>
            <button className="primary"
              disabled={!canWrite || busy || !decisionPolicies[0] || !reportJobs[0]}
              onClick={() => void action(() => decisionApi.create(
                token, project.id, decisionPolicies[0].id, reportJobs[0].dataSnapshotId,
              ), "Karar desteği oluşturuldu")}>Karar desteği oluştur</button>
          </div>
        </article>}
        {decisionCases.map((decision) => <article className="panel executive-card"
          key={decision.id}>
          <div className="panel-head"><div><b>{decision.caseCode}</b>
            <span>{decision.statusConceptCode}</span></div><BrainCircuit /></div>
          <div className="confidence-ring">
            <strong>%{Math.round(decision.confidence * 100)}</strong><span>confidence</span>
          </div>
          <div className="decision-body">
            <h3>{decision.recommendedDecisionConceptCode}</h3>
            <p>{Array.isArray(decision.explanation.summary)
              ? decision.explanation.summary.join(" · ")
              : decision.explanation.summary}</p>
            {decision.factors.map((factor) => <div className="factor-row"
              key={factor.factorConceptCode}>
              <b>{factor.factorConceptCode}</b><span>{factor.description}</span>
              <em>{(factor.effectScore * factor.weight).toFixed(2)}</em>
            </div>)}
            {!decision.finalDecisionConceptId && <div className="decision-options">
              {decisionOptions.map((option) => <button className="secondary"
                key={option.id} disabled={!canWrite || busy}
                onClick={() => void action(() => decisionApi.decide(
                  token, decision.id, option.id,
                  `${option.name}: yönetici tarafından kaynaklar incelenerek kaydedildi.`,
                ), "Yönetici kararı kaydedildi")}>{option.name}</button>)}
            </div>}
            {decision.finalDecisionConceptCode && <p className="final-decision">
              İnsan kararı: <b>{decision.finalDecisionConceptCode}</b></p>}
          </div>
        </article>)}
        {!project && <EmptyState icon={Gauge} title="Proje seçin"
          body="Karar faktörleri ve finalizasyon geçmişi proje kapsamında yüklenir." />}
      </section>}
    </>}
  </PageShell>
  );
}
