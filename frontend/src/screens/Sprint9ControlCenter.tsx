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


import { MetricCard, Empty, EmptyState, LoadingPanel, formatRatio, prettyJson, readUnknown, type Sprint9Tab } from "./_shared";
import { WorkspaceRail } from "@/src/components/pipeline/WorkspaceRail";

export function Sprint9ControlCenter({
  token,
  canOperate,
  onProblem,
  onNotify,
  tab,
  onTab,
}: {
  token: string;
  canOperate: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
  tab: Sprint9Tab;
  onTab: (tab: Sprint9Tab) => void;
}) {
  const [dashboard, setDashboard] = useState<PilotDashboard>();
  const [feedback, setFeedback] = useState<FeedbackCase[]>([]);
  const [selectedFeedback, setSelectedFeedback] = useState<FeedbackCase>();
  const [candidates, setCandidates] = useState<ImprovementCandidate[]>([]);
  const [selectedCandidate, setSelectedCandidate] = useState<ImprovementCandidate>();
  const [releases, setReleases] = useState<ReleaseRecord[]>([]);
  const [selectedRelease, setSelectedRelease] = useState<ReleaseRecord>();
  const [releasePackage, setReleasePackage] = useState<GoLivePackage>();
  const [systemVersion, setSystemVersion] = useState<SystemVersion>();
  const [rootCauses, setRootCauses] = useState<DynamicConcept[]>([]);
  const [reproducibility, setReproducibility] = useState<DynamicConcept[]>([]);
  const [rootCauseCode, setRootCauseCode] = useState("CONTEXT_SELECTION");
  const [reproducibilityCode, setReproducibilityCode] = useState("UNKNOWN");
  const [impactScore, setImpactScore] = useState(50);
  const [frequencyScore, setFrequencyScore] = useState(50);
  const [analysisSummary, setAnalysisSummary] = useState("");
  const [rollbackReference, setRollbackReference] = useState("");
  const [loadingSprint9, setLoadingSprint9] = useState(true);
  const [busySprint9, setBusySprint9] = useState(false);

  const loadReleasePackage = useCallback(async (release: ReleaseRecord) => {
    const nextPackage = await releaseApi.goLivePackage(token, release.id);
    setSelectedRelease(release);
    setReleasePackage(nextPackage);
  }, [token]);

  const load = useCallback(async () => {
    setLoadingSprint9(true);
    try {
      const [nextDashboard, nextFeedback, nextCandidates, causes, reproducibilityValues,
        version, nextReleases] = await Promise.all([
        pilotApi.dashboard(token),
        pilotApi.feedback(token),
        pilotApi.candidates(token),
        pilotApi.concepts(token, "ROOT_CAUSE"),
        pilotApi.concepts(token, "REPRODUCIBILITY"),
        releaseApi.version(token),
        canOperate ? releaseApi.releases(token) : Promise.resolve([]),
      ]);
      setDashboard(nextDashboard);
      setFeedback(nextFeedback);
      setCandidates(nextCandidates);
      setRootCauses(causes);
      setReproducibility(reproducibilityValues);
      setSystemVersion(version);
      setReleases(nextReleases);
      setRootCauseCode((current) => current || causes[0]?.code || "");
      setReproducibilityCode((current) =>
        current || reproducibilityValues[0]?.code || "");
      if (!selectedFeedback && nextFeedback[0]) {
        setSelectedFeedback(await pilotApi.feedbackCase(token, nextFeedback[0].id));
      }
      if (!selectedCandidate && nextCandidates[0]) {
        setSelectedCandidate(await pilotApi.candidate(token, nextCandidates[0].id));
      }
      if (canOperate && !selectedRelease && nextReleases[0]) {
        await loadReleasePackage(nextReleases[0]);
      }
    } catch (error) {
      onProblem(error);
    } finally {
      setLoadingSprint9(false);
    }
  }, [
    canOperate,
    loadReleasePackage,
    onProblem,
    selectedCandidate,
    selectedFeedback,
    selectedRelease,
    token,
  ]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 0);
    return () => window.clearTimeout(timer);
  }, [load]);

  async function inspectFeedback(item: FeedbackCase) {
    try {
      setSelectedFeedback(await pilotApi.feedbackCase(token, item.id));
    } catch (error) {
      onProblem(error);
    }
  }

  async function inspectCandidate(item: ImprovementCandidate) {
    try {
      setSelectedCandidate(await pilotApi.candidate(token, item.id));
    } catch (error) {
      onProblem(error);
    }
  }

  async function submitTriage(event: FormEvent) {
    event.preventDefault();
    if (!selectedFeedback) return;
    setBusySprint9(true);
    try {
      const updated = await pilotApi.triage(token, selectedFeedback.id, {
        rootCauseCode,
        secondaryCauseCodes: [],
        reproducibilityCode,
        affectedScope: {
          entityType: selectedFeedback.entity_type,
          projectId: selectedFeedback.project_id,
        },
        impactScore,
        frequencyScore,
        analysisSummary,
        analysisSignals: {},
      });
      setSelectedFeedback(updated);
      onNotify("İnsan onaylı triage kaydedildi");
      await load();
    } catch (error) {
      onProblem(error);
    } finally {
      setBusySprint9(false);
    }
  }

  async function candidateAction(action: "shadow" | "canary" | "activate" | "reject") {
    if (!selectedCandidate) return;
    setBusySprint9(true);
    try {
      if (action === "shadow") await pilotApi.shadow(token, selectedCandidate.id);
      if (action === "canary") await pilotApi.canary(token, selectedCandidate.id, 5);
      if (action === "activate") await pilotApi.activate(token, selectedCandidate.id);
      if (action === "reject") await pilotApi.reject(token, selectedCandidate.id);
      setSelectedCandidate(await pilotApi.candidate(token, selectedCandidate.id));
      onNotify(`${action.toUpperCase()} aksiyonu kaydedildi`);
      await load();
    } catch (error) {
      onProblem(error);
    } finally {
      setBusySprint9(false);
    }
  }

  async function recordGoLive(decision: "GO" | "NO_GO" | "REASSESS") {
    if (!selectedRelease || !rollbackReference.trim()) {
      onProblem(new Error("Go-live kararı için rollback planı referansı zorunludur."));
      return;
    }
    setBusySprint9(true);
    try {
      await releaseApi.goLiveDecision(token, selectedRelease.id, {
        decision,
        conditions: [],
        openRisks: releasePackage?.blockers ?? [],
        rollbackPlanReference: rollbackReference,
      });
      await loadReleasePackage(selectedRelease);
      onNotify(`${decision} kararı insan aktörüyle kaydedildi`);
    } catch (error) {
      onProblem(error);
    } finally {
      setBusySprint9(false);
    }
  }

  if (loadingSprint9 && !dashboard) return <LoadingPanel />;
  const maximumCause = Math.max(
    1,
    ...(dashboard?.rootCauseDistribution ?? []).map((item) => Number(item.count)),
  );
  const triage = selectedFeedback?.triage?.[0];
  const railItems = [
    { id: "quality" as const, label: "Pilot kalite", icon: Gauge, group: "Pilot" },
    { id: "errors" as const, label: "Hata analizi", icon: GitCompareArrows, group: "Pilot" },
    { id: "improvements" as const, label: "İyileştirmeler", icon: FlaskConical, group: "Pilot" },
    ...(canOperate
      ? [{ id: "release" as const, label: "RC ve go-live", icon: Rocket, group: "Release" }]
      : []),
  ];

  return (
    <PageShell
      title="Pilot kalite ve release merkezi"
      subtitle="Feedback’i kök nedenine ayırın, değişiklikleri deneyle kanıtlayın ve go-live kararını fail-closed yönetin."
      icon={<FlaskConical className="h-4 w-4" />}
      heroTrailing={
        <div className="title-actions flex items-center gap-2">
          <span className="rc-chip">{systemVersion?.applicationVersion ?? "development"}</span>
          <button className="btn-secondary" onClick={() => void load()}>
            <RefreshCw />
            Yenile
          </button>
        </div>
      }
      maxWidth="max-w-[1460px]"
    >
    <div className="workspace-layout">
      <WorkspaceRail
        ariaLabel="Pilot kalite bölümleri"
        activeId={tab}
        onSelect={(id) => onTab(id as Sprint9Tab)}
        items={railItems}
      />
      <div className="workspace-main">
    {tab === "quality" && <section className="sprint9-quality">
      <div className="sprint9-metric-grid">
        <MetricCard label="Toplam feedback" value={dashboard?.totalFeedback ?? 0}
          detail="Pilot kapsamındaki kayıt" />
        <MetricCard label="Açık feedback" value={dashboard?.openFeedback ?? 0}
          detail="Triage veya çözüm bekliyor" tone="amber" />
        <MetricCard label="Release blocker" value={dashboard?.releaseBlockers ?? 0}
          detail="Fail-closed üretim engeli" tone="red" />
        <MetricCard label="Regression" value={dashboard?.regressionCases ?? 0}
          detail="Kalıcı hata koruması" />
        <MetricCard label="Ort. çözüm" value={
          `${Number(dashboard?.meanResolutionHours ?? 0).toFixed(1)} sa`}
          detail="Feedback kapanış süresi" />
        <MetricCard label="Memnuniyet" value={
          dashboard?.satisfactionScore == null
            ? "Ölçülmedi" : dashboard.satisfactionScore.toFixed(1)}
          detail="Teknik kalite dışı pilot sinyali" />
      </div>
      <div className="sprint9-quality-grid">
        <article className="panel cause-card">
          <div className="panel-head"><div><b>Kök neden dağılımı</b>
            <span>Dinamik ROOT_CAUSE kataloğu</span></div><GitCompareArrows /></div>
          <div className="cause-bars">
            {(dashboard?.rootCauseDistribution.length
              ? dashboard.rootCauseDistribution
              : [{ cause: "TRIAGE_BEKLENİYOR", count: 0 }]).map((item) =>
                <div key={item.cause}>
                  <span>{item.cause.replaceAll("_", " ")}</span>
                  <i><em style={{ width: `${Number(item.count) / maximumCause * 100}%` }} /></i>
                  <b>{item.count}</b>
                </div>)}
          </div>
        </article>
        <article className="panel quality-proof">
          <div className="panel-head"><div><b>Kanıt durumu</b>
            <span>Eksik ölçüm başarı sayılmaz</span></div><ShieldCheck /></div>
          <dl>
            <div><dt>Manual review</dt><dd>
              {formatRatio(dashboard?.manualReviewRate)}</dd></div>
            <div><dt>Uzman düzeltmesi</dt><dd>
              {formatRatio(dashboard?.expertCorrectionRate)}</dd></div>
            <div><dt>UAT</dt><dd>{dashboard?.uatStatus ?? "NOT_AVAILABLE"}</dd></div>
            <div><dt>Build</dt><dd>{systemVersion?.buildNumber ?? "NOT_AVAILABLE"}</dd></div>
            <div><dt>Migration</dt><dd>
              {systemVersion?.databaseMigrationVersion ?? "NOT_AVAILABLE"}</dd></div>
            <div><dt>Model profili</dt><dd>
              {systemVersion?.modelPublicProfileVersion ?? "NOT_AVAILABLE"}</dd></div>
          </dl>
        </article>
      </div>
    </section>}

    {tab === "errors" && <section className="error-analysis-workspace">
      <aside className="analysis-rail panel">
        <div className="workspace-panel-title"><span>01</span><div>
          <b>Feedback ve kaynak</b><small>{feedback.length} kayıt</small></div></div>
        <div className="feedback-list">
          {feedback.length ? feedback.map((item) =>
            <button key={item.id}
              className={selectedFeedback?.id === item.id ? "selected" : ""}
              onClick={() => void inspectFeedback(item)}>
              <span className={`severity-marker ${item.severity.toLowerCase()}`} />
              <div><b>{item.title}</b><small>{item.feedback_code} · {item.entity_type}</small></div>
              <em>{item.status}</em>
            </button>) : <Empty text="Pilot feedback kaydı bulunmuyor." />}
        </div>
      </aside>
      <article className="analysis-compare panel">
        <div className="workspace-panel-title"><span>02</span><div>
          <b>Beklenen / gerçek</b><small>Sanitize edilmiş karşılaştırma</small></div></div>
        {selectedFeedback ? <>
          <div className="analysis-context">
            <span>{selectedFeedback.classification}</span>
            <span>{selectedFeedback.feedbackType}</span>
            <span>{selectedFeedback.severity}</span>
          </div>
          <h2>{selectedFeedback.title}</h2>
          <p>{selectedFeedback.description}</p>
          <div className="expected-actual">
            <div><small>BEKLENEN</small>
              <p>{selectedFeedback.expected_behavior || "Beklenen davranış girilmemiş."}</p></div>
            <div><small>GERÇEK</small>
              <p>{selectedFeedback.actual_behavior || "Gerçek davranış girilmemiş."}</p></div>
          </div>
          <div className="evidence-summary">
            <b>Güvenli evidence referansları</b>
            <span>{selectedFeedback.evidence?.length ?? 0} sanitize snapshot</span>
          </div>
        </> : <Empty text="İncelemek için bir feedback seçin." />}
      </article>
      <aside className="analysis-action panel">
        <div className="workspace-panel-title"><span>03</span><div>
          <b>Kök neden ve aksiyon</b><small>Final triage insan onaylıdır</small></div></div>
        {triage ? <div className="triage-result">
          <span className="decision-tag">TRIAGED</span>
          <h3>{String(readUnknown(triage, "rootCause", "rootcause"))
            .replaceAll("_", " ")}</h3>
          <p>{String(readUnknown(triage, "analysis_summary") || "")}</p>
          <dl>
            <div><dt>Öncelik</dt><dd>{String(readUnknown(triage, "priority_score"))}</dd></div>
            <div><dt>Blocker</dt><dd>
              {readUnknown(triage, "release_blocker") ? "EVET" : "HAYIR"}</dd></div>
            <div><dt>Onaylayan</dt><dd>{String(readUnknown(triage, "triaged_by"))}</dd></div>
          </dl>
        </div> : selectedFeedback && canOperate ? <form className="triage-form" onSubmit={submitTriage}>
          <label>Kök neden
            <select value={rootCauseCode}
              onChange={(event) => setRootCauseCode(event.target.value)}>
              {rootCauses.map((concept) =>
                <option key={concept.id} value={concept.code}>{concept.name}</option>)}
            </select>
          </label>
          <label>Tekrarlanabilirlik
            <select value={reproducibilityCode}
              onChange={(event) => setReproducibilityCode(event.target.value)}>
              {reproducibility.map((concept) =>
                <option key={concept.id} value={concept.code}>{concept.name}</option>)}
            </select>
          </label>
          <div className="score-inputs">
            <label>Etki <input type="number" min="0" max="100" value={impactScore}
              onChange={(event) => setImpactScore(Number(event.target.value))} /></label>
            <label>Sıklık <input type="number" min="0" max="100" value={frequencyScore}
              onChange={(event) => setFrequencyScore(Number(event.target.value))} /></label>
          </div>
          <label>Analiz özeti
            <textarea required value={analysisSummary}
              onChange={(event) => setAnalysisSummary(event.target.value)}
              placeholder="Sinyalleri ve insan kararının gerekçesini yazın." />
          </label>
          <button className="primary" disabled={busySprint9}>
            {busySprint9 ? <LoaderCircle className="spin" /> : <ShieldCheck />}
            Triage’ı kaydet
          </button>
        </form> : <Empty text={
          selectedFeedback
            ? "Triage için yetkiniz yok."
            : "Feedback seçimi bekleniyor."
        } />}
      </aside>
    </section>}

    {tab === "improvements" && <section className="improvement-workspace">
      <aside className="candidate-list panel">
        <div className="workspace-panel-title"><span>A</span><div>
          <b>İyileştirme adayları</b><small>Aktif config değişmez</small></div></div>
        {candidates.length ? candidates.map((candidate) =>
          <button key={candidate.id}
            className={selectedCandidate?.id === candidate.id ? "selected" : ""}
            onClick={() => void inspectCandidate(candidate)}>
            <span>{candidate.candidate_code}</span><b>{candidate.title}</b>
            <small>{candidate.candidateType} · {candidate.status}</small>
          </button>) : <Empty text="Henüz iyileştirme adayı yok." />}
      </aside>
      <article className="candidate-comparison panel">
        <div className="workspace-panel-title"><span>B</span><div>
          <b>Snapshot karşılaştırması</b><small>Reproducible baseline / candidate</small></div></div>
        {selectedCandidate ? <>
          <div className="candidate-heading">
            <div><span>{selectedCandidate.candidateType}</span>
              <h2>{selectedCandidate.title}</h2>
              <p>{selectedCandidate.description}</p></div>
            <em>{selectedCandidate.status}</em>
          </div>
          <div className="snapshot-compare">
            <div><small>BASELINE SNAPSHOT</small>
              <code>{selectedCandidate.baseline_configuration_snapshot_id}</code></div>
            <GitCompareArrows />
            <div><small>CANDIDATE SNAPSHOT</small>
              <code>{selectedCandidate.candidate_configuration_snapshot_id}</code></div>
          </div>
          <div className="candidate-json">
            <div><b>Beklenen iyileşme</b>
              <pre>{prettyJson(selectedCandidate.expected_improvement_json)}</pre></div>
            <div><b>Risk değerlendirmesi</b>
              <pre>{prettyJson(selectedCandidate.risk_assessment_json)}</pre></div>
          </div>
          <div className="experiment-strip">
            <b>Deney zinciri</b>
            <span>{selectedCandidate.experiments?.length ?? 0} deney</span>
            <span>Offline → Shadow → Canary → Aktivasyon</span>
          </div>
        </> : <Empty text="Karşılaştırmak için bir candidate seçin." />}
      </article>
      <aside className="candidate-actions panel">
        <div className="workspace-panel-title"><span>C</span><div>
          <b>Kontrollü geçiş</b><small>Her aşama önceki kanıtı ister</small></div></div>
        {!canOperate ? <Empty text="Geçiş aksiyonları için yetkiniz yok." /> : <>
        <button disabled={!selectedCandidate || busySprint9}
          onClick={() => void candidateAction("shadow")}>
          <Eye /><div><b>Shadow başlat</b><small>Production davranışını değiştirmez</small></div>
        </button>
        <button disabled={!selectedCandidate || busySprint9}
          onClick={() => void candidateAction("canary")}>
          <FlaskConical /><div><b>%5 canary</b><small>Geçmiş shadow sonucu zorunlu</small></div>
        </button>
        <button className="positive" disabled={!selectedCandidate || busySprint9}
          onClick={() => void candidateAction("activate")}>
          <CheckCircle2 /><div><b>Aktivasyonu onayla</b>
            <small>Offline + shadow + canary kanıtı</small></div>
        </button>
        <button className="danger" disabled={!selectedCandidate || busySprint9}
          onClick={() => void candidateAction("reject")}>
          <X /><div><b>Candidate’ı reddet</b><small>Aktif sürüm korunur</small></div>
        </button>
        </>}
      </aside>
    </section>}

    {tab === "release" && canOperate && <section className="release-workspace">
      <aside className="release-rail panel">
        <div className="workspace-panel-title"><span>RC</span><div>
          <b>Release kayıtları</b><small>{releases.length} sürüm</small></div></div>
        {releases.length ? releases.map((release) =>
          <button key={release.id}
            className={selectedRelease?.id === release.id ? "selected" : ""}
            onClick={() => void loadReleasePackage(release)}>
            <span>{release.release_code}</span><b>v{release.semantic_version}</b>
            <small>{release.releaseType} · {release.status}</small>
          </button>) : <Empty text="Release kaydı bulunmuyor." />}
        <button className="diagnostic-action" onClick={async () => {
          try {
            const bundle = await releaseApi.diagnosticBundle(token);
            const blob = new Blob([JSON.stringify(bundle, null, 2)], {
              type: "application/json",
            });
            const href = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = href;
            anchor.download = `diagnostic-bundle-${Date.now()}.json`;
            anchor.click();
            URL.revokeObjectURL(href);
            onNotify("Diagnostic bundle indirildi");
          } catch (error) {
            onProblem(error);
          }
        }}><ServerCog />Diagnostic bundle indir</button>
      </aside>
      <article className="release-evidence panel">
        <div className="workspace-panel-title"><span>01</span><div>
          <b>Release kanıt paketi</b><small>Gate’ler backend’den gelir</small></div></div>
        {releasePackage ? <>
          <div className="release-hero">
            <div><p className="eyebrow">{releasePackage.release.releaseType}</p>
              <h2>{releasePackage.release.release_code}</h2>
              <p>Build {releasePackage.release.build_number} · commit
                {" "}{releasePackage.release.source_commit}</p></div>
            <span className={releasePackage.eligibleForGoLive ? "eligible" : "blocked"}>
              {releasePackage.eligibleForGoLive ? "GO-LIVE ELIGIBLE" : "KANIT EKSİK"}
            </span>
          </div>
          <div className="gate-matrix">
            {releasePackage.gates.map((gate) =>
              <div key={gate.gateCode}>
                <span className={`gate-state ${(gate.status ?? "NOT_RUN").toLowerCase()}`}>
                  {gate.status ?? "NOT_RUN"}</span>
                <b>{gate.name}</b>
                <small>{gate.summary || "Kanıt kaydı bulunmuyor."}</small>
              </div>)}
          </div>
        </> : <Empty text="Kanıt paketini görmek için release seçin." />}
      </article>
      <aside className="go-live-panel panel">
        <div className="workspace-panel-title"><span>02</span><div>
          <b>İnsan go-live kararı</b><small>AI karar veremez</small></div></div>
        <div className="release-counts">
          <div><span>Açık blocker</span><b>{releasePackage?.blockers.length ?? 0}</b></div>
          <div><span>Quality debt</span><b>{releasePackage?.qualityDebt.length ?? 0}</b></div>
          <div><span>Dry run</span><b>{releasePackage?.dryRuns.length ?? 0}</b></div>
          <div><span>Onay</span><b>{releasePackage?.approvals.length ?? 0}</b></div>
        </div>
        <label className="rollback-field">Rollback planı referansı
          <input value={rollbackReference}
            onChange={(event) => setRollbackReference(event.target.value)}
            placeholder="runbook://release/rollback-v1" />
        </label>
        <div className="missing-evidence">
          <b>Eksik kanıt</b>
          {releasePackage?.missingEvidence.length
            ? releasePackage.missingEvidence.map((item) => <span key={item}>{item}</span>)
            : <span className="clear">Zorunlu kanıtlar tamamlandı</span>}
        </div>
        <div className="go-live-actions">
          <button disabled={busySprint9} onClick={() => void recordGoLive("NO_GO")}>
            NO-GO</button>
          <button disabled={busySprint9} onClick={() => void recordGoLive("REASSESS")}>
            REASSESS</button>
          <button className="positive" disabled={
            busySprint9 || !releasePackage?.eligibleForGoLive}
            onClick={() => void recordGoLive("GO")}>GO</button>
        </div>
      </aside>
    </section>}
      </div>
    </div>
  </PageShell>
  );
}
