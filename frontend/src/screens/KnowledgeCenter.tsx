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


import { Empty, LoadingPanel, DynamicField, EvidenceDocumentPane } from "./_shared";

export function KnowledgeCenter({ documents, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [entities, setEntities] = useState<KnowledgeEntity[]>([]);
  const [entityTypes, setEntityTypes] = useState<ConceptOption[]>([]);
  const [selectedType, setSelectedType] = useState("");
  const [selected, setSelected] = useState<EntityDetail>();
  const [configuration, setConfiguration] = useState<EntityUiConfiguration>();
  const [activeTab, setActiveTab] = useState("overview");
  const [evidence, setEvidence] = useState<EvidenceFragment>();
  const [query, setQuery] = useState("");
  const [documentId, setDocumentId] = useState(
    documents.find((document) => document.status === "READY")?.id ?? "",
  );
  const [job, setJob] = useState<KnowledgeExtractionJob>();
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [draft, setDraft] = useState({
    entityCode: "",
    entityTypeConceptId: "",
    name: "",
    description: "",
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [nextEntities, types] = await Promise.all([
        knowledgeApi.entities(token, selectedType || undefined, query || undefined),
        knowledgeApi.entityTypes(token),
      ]);
      setEntities(nextEntities);
      setEntityTypes(types);
    } catch (error) {
      onProblem(error);
    } finally {
      setLoading(false);
    }
  }, [onProblem, query, selectedType, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => { void load(); }, 150);
    return () => window.clearTimeout(timer);
  }, [load]);

  useEffect(() => {
    if (!job || ["COMPLETED", "FAILED", "CANCELLED"].includes(job.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const current = await knowledgeApi.extractionJob(token, job.id);
        setJob(current);
        if (current.status === "COMPLETED") {
          window.clearInterval(timer);
          await load();
          onNotify(`${current.extracted_entity_count} entity bilgi tabanına eklendi`);
        }
      } catch (error) {
        window.clearInterval(timer);
        onProblem(error);
      }
    }, 2200);
    return () => window.clearInterval(timer);
  }, [job, load, onNotify, onProblem, token]);

  async function selectEntity(entity: KnowledgeEntity) {
    try {
      const [detail, config] = await Promise.all([
        knowledgeApi.entity(token, entity.id),
        knowledgeApi.entityConfiguration(token, entity.entityTypeConceptId),
      ]);
      setSelected(detail);
      setConfiguration(config);
      setActiveTab(config.profile?.tabs?.[0]?.key ?? "overview");
      setEvidence(undefined);
    } catch (error) {
      setSelected(undefined);
      setConfiguration(undefined);
      setEvidence(undefined);
      onProblem(error);
    }
  }

  async function selectEvidence(fragmentId: string) {
    try {
      setEvidence(await knowledgeApi.evidence(token, fragmentId));
    } catch (error) {
      onProblem(error);
    }
  }

  async function startExtraction() {
    if (!documentId) return;
    try {
      const created = await knowledgeApi.startExtraction(token, documentId);
      setJob(created);
      onNotify("Knowledge extraction snapshot oluşturuldu");
    } catch (error) {
      onProblem(error);
    }
  }

  async function createEntity(event: FormEvent) {
    event.preventDefault();
    try {
      const created = await knowledgeApi.create(token, {
        ...draft,
        status: "ACTIVE",
        attributes: {},
        sourceType: "MANUAL_REVIEW",
      });
      setCreateOpen(false);
      setDraft({ entityCode: "", entityTypeConceptId: "", name: "", description: "" });
      await load();
      await selectEntity(created);
      onNotify("Entity oluşturuldu");
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="knowledge-center card-static">
    <div className="panel knowledge-toolbar">
      <div><p className="eyebrow">DİNAMİK BİLGİ GRAFI</p>
        <h2>Firma ve ürün bilgi merkezi</h2>
        <span>Form, sekme ve alanlar aktif ontology konfigürasyonundan gelir.</span></div>
      <div className="knowledge-actions">
        <select value={documentId} onChange={(event) => setDocumentId(event.target.value)}
          aria-label="Bilgi çıkarılacak doküman">
          <option value="">Hazır doküman seçin</option>
          {documents.filter((document) => document.status === "READY").map((document) =>
            <option key={document.id} value={document.id}>{document.logicalName}</option>)}
        </select>
        {canWrite && <button className="secondary" onClick={() => setCreateOpen(true)}
          disabled={!entityTypes.length}><Plus />Yeni entity</button>}
        {canWrite && <button className="primary" onClick={startExtraction}
          disabled={!documentId || Boolean(job &&
            !["COMPLETED", "FAILED", "CANCELLED"].includes(job.status))}>
          <Activity />Bilgi çıkar</button>}
      </div>
    </div>
    {job && <div className="extraction-progress">
      <div><b>{job.status.replaceAll("_", " ")}</b>
        <span>{job.processed_fragment_count}/{job.total_fragment_count} fragman ·
          {" "}{job.extracted_entity_count} entity ·
          {" "}{job.manual_review_count} inceleme</span></div>
      {job.status === "FAILED" && (
        <p className="warning-box">KNOWLEDGE_EXTRACTION_FAILED
          {job.error_code ? `: ${job.error_code}` : ""}
          {job.current_stage_code ? ` (stage ${job.current_stage_code})` : ""}
          {job.error_message ? ` — ${job.error_message}` : ""}</p>
      )}
      {job.status === "COMPLETED" && job.existing_knowledge_used && (
        <p className="eyebrow">EXISTING_KNOWLEDGE_USED · purpose {job.document_purpose_code || "—"}</p>
      )}
      {job.status === "COMPLETED" && !job.existing_knowledge_used && job.document_purpose_code && (
        <p className="eyebrow">purpose {job.document_purpose_code}
          {job.current_stage_code ? ` · last stage ${job.current_stage_code}` : ""}</p>
      )}
      <progress max={Math.max(job.total_fragment_count, 1)}
        value={job.processed_fragment_count} />
    </div>}
    <div className="knowledge-layout">
      <aside className="panel entity-browser">
        <div className="entity-filters">
          <input value={query} onChange={(event) => setQuery(event.target.value)}
            placeholder="Entity ara" aria-label="Entity ara" />
          <select value={selectedType} onChange={(event) => setSelectedType(event.target.value)}
            aria-label="Entity türü">
            <option value="">Tüm ontology türleri</option>
            {entityTypes.map((type) =>
              <option key={type.id} value={type.id}>{type.name}</option>)}
          </select>
        </div>
        {loading ? <div className="processing"><LoaderCircle className="spin" /></div>
          : entities.map((entity) => <button key={entity.id}
            className={selected?.entity.id === entity.id ? "entity-row active" : "entity-row"}
            onClick={() => selectEntity(entity)}>
            <span>{initials(entity.name)}</span>
            <div><b>{entity.name}</b>
              <small>{entity.entityTypeCode} · {entity.entityCode}</small></div>
            <ChevronRight />
          </button>)}
        {!loading && !entities.length &&
          <Empty text="Bu kapsamda entity bulunmuyor. Önce bir bilgi dokümanı işleyin." />}
      </aside>
      <main className="panel entity-profile">
        {selected && configuration ? <>
          <div className="entity-title"><div><p className="eyebrow">
            {configuration.conceptCode}</p><h2>{selected.entity.name}</h2>
            <span>{selected.entity.description || "Açıklama eklenmemiş"}</span></div>
            <em>{selected.entity.status}</em></div>
          <nav className="subtabs" aria-label="Dinamik entity bölümleri">
            {configuration.profile.tabs.map((tab) =>
              <button key={tab.key} className={activeTab === tab.key ? "active" : ""}
                onClick={() => setActiveTab(tab.key)}>{tab.label}</button>)}
          </nav>
          {activeTab === "overview" && <div className="dynamic-field-grid">
            <DynamicField label="Entity kodu" value={selected.entity.entityCode} />
            <DynamicField label="Kaynak türü" value={selected.entity.sourceType} />
            {selected.attributes.map((attribute) =>
              <button className="dynamic-field" key={attribute.id}
                onClick={() => selectEvidence(attribute.sourceFragmentId)}>
                <span>{attribute.attributeConceptCode}</span>
                <b>{dynamicValueLabel(attribute.value)}</b>
                <small>%{Math.round(attribute.confidence * 100)} confidence ·
                  {" "}{attribute.reviewStatus}</small>
              </button>)}
          </div>}
          {activeTab === "capabilities" && <div className="card-list">
            {selected.capabilities.map((capability) => <article key={capability.id}>
              <b>{capability.name}</b><span>{capability.capabilityConceptCode}</span>
              <p>{capability.description || JSON.stringify(capability.attributes)}</p>
              <small>%{Math.round(capability.confidence * 100)} confidence ·
                {" "}{capability.evidence.length} evidence</small>
            </article>)}
            {!selected.capabilities.length && <Empty text="Capability bulunmuyor." />}
          </div>}
          {activeTab === "relations" && <div className="card-list">
            {selected.relations.map((relation) => <button key={relation.id}
              onClick={() => selectEvidence(relation.sourceFragmentId)}>
              <b>{relation.relationConceptCode}</b>
              <span>{relation.sourceEntityId.slice(0, 8)} →
                {" "}{relation.targetEntityId.slice(0, 8)}</span>
              <small>%{Math.round(relation.confidence * 100)} confidence</small>
            </button>)}
            {!selected.relations.length && <Empty text="İlişki bulunmuyor." />}
          </div>}
          {activeTab === "evidence" && <div className="card-list">
            {selected.evidence.map((item) => <button key={item.id}
              onClick={() => selectEvidence(item.id)}>
              <b>Sayfa {item.page_number ?? "—"}</b>
              <span>{item.validity_status ?? "Değerlendirilmemiş"}</span>
              <small>{(item.content_hash ?? "—").toString().slice(0, 12)} ·
                {" "}%{Math.round((item.validity_score ?? 0) * 100)} validity</small>
            </button>)}
            {!selected.evidence.length && <Empty text="Bağlı evidence bulunmuyor." />}
          </div>}
          {activeTab === "history" && <pre className="json-panel">
            {JSON.stringify(selected.revisions, null, 2)}</pre>}
        </> : <Empty text="Dinamik profili açmak için soldan bir entity seçin." />}
      </main>
      <aside className="panel evidence-inspector">
        <EvidenceDocumentPane evidence={evidence} token={token} />
      </aside>
    </div>
    {createOpen && <div className="modal-backdrop" onMouseDown={() => setCreateOpen(false)}>
      <form className="modal-card entity-create" onSubmit={createEntity}
        onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head"><div><p className="eyebrow">ONTOLOGY TABANLI</p>
          <h2>Yeni entity</h2></div><button type="button" onClick={() => setCreateOpen(false)}
            aria-label="Kapat"><X /></button></div>
        <label>Entity türü<select required value={draft.entityTypeConceptId}
          onChange={(event) => setDraft({ ...draft,
            entityTypeConceptId: event.target.value })}>
          <option value="">Ontology concept seçin</option>
          {entityTypes.map((type) => <option key={type.id} value={type.id}>{type.name}</option>)}
        </select></label>
        <label>Kod<input required value={draft.entityCode}
          onChange={(event) => setDraft({ ...draft, entityCode: event.target.value })} /></label>
        <label>Ad<input required value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></label>
        <label>Açıklama<textarea value={draft.description}
          onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></label>
        <button className="primary"><Plus />Oluştur</button>
      </form>
    </div>}
  </section>;
}
