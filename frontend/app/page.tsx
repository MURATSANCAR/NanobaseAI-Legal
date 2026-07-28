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
  FolderKanban,
  Gauge,
  LoaderCircle,
  LogIn,
  LogOut,
  Menu,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Upload,
  UserPlus,
  Users,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import type { User } from "oidc-client-ts";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { auditApi, type AuditEvent } from "@/src/modules/audit/api";
import {
  displayName,
  realmRoles,
  restoreSession,
  signIn,
  signOut,
} from "@/src/modules/auth/auth";
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
import { isApiError, type ApiProblem } from "@/src/shared/api";

type Screen = "dashboard" | "projects" | "project";
type ProjectTab = "overview" | "documents" | "requirements" | "activity" | "settings";

const emptyDraft: TenderDraft = {
  name: "",
  institutionName: "",
  tenderRegistrationNumber: "",
  tenderType: "",
  businessType: "",
  sector: "",
  priority: "NORMAL",
  bidDeadline: "",
  clarificationDeadline: "",
  description: "",
  currency: "TRY",
};

const processingStatuses = new Set([
  "UPLOADED",
  "VIRUS_SCANNING",
  "CLASSIFYING",
  "QUEUED",
  "PARSING",
  "OCR_PROCESSING",
  "STRUCTURE_DETECTION",
  "INDEXING",
]);

export default function SpecAiPortal() {
  const [session, setSession] = useState<User | null>();
  const [screen, setScreen] = useState<Screen>("dashboard");
  const [projectTab, setProjectTab] = useState<ProjectTab>("overview");
  const [projects, setProjects] = useState<TenderProject[]>([]);
  const [allDocuments, setAllDocuments] = useState<ProjectDocument[]>([]);
  const [documents, setDocuments] = useState<ProjectDocument[]>([]);
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [selectedProject, setSelectedProject] = useState<TenderProject | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<ApiProblem | null>(null);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [institutionFilter, setInstitutionFilter] = useState("");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState(1);
  const [draft, setDraft] = useState<TenderDraft>(emptyDraft);
  const [toast, setToast] = useState("");

  const token = session?.access_token ?? "";
  const roles = session ? realmRoles(session) : [];
  const canWrite = roles.some((role) =>
    ["SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER"].includes(role),
  );
  const canAnalyze = canWrite || roles.includes("TECHNICAL_REVIEWER");

  const showProblem = useCallback((error: unknown) => {
    if (isApiError(error)) setProblem(error.problem);
    else
      setProblem({
        title: "Beklenmeyen hata",
        status: 500,
        detail: error instanceof Error ? error.message : "İşlem tamamlanamadı.",
      });
  }, []);

  const notify = useCallback((message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  }, []);

  const loadProjects = useCallback(
    async (accessToken = token) => {
      if (!accessToken) return;
      setProblem(null);
      const page = await tenderApi.list(accessToken);
      setProjects(page.content);
      const documentGroups = await Promise.all(
        page.content.map(async (project) => {
          try {
            return await documentApi.list(accessToken, project.id);
          } catch {
            return [];
          }
        }),
      );
      setAllDocuments(documentGroups.flat());
    },
    [token],
  );

  const loadProject = useCallback(
    async (project: TenderProject, accessToken = token) => {
      if (!accessToken) return;
      setSelectedProject(project);
      setScreen("project");
      setLoading(true);
      setProblem(null);
      try {
        const [projectDocuments, projectMembers, auditPage] = await Promise.all([
          documentApi.list(accessToken, project.id),
          tenderApi.members(accessToken, project.id),
          auditApi.list(accessToken),
        ]);
        setDocuments(projectDocuments);
        setMembers(projectMembers);
        setAuditEvents(
          auditPage.content.filter((event) => event.entityId === project.id ||
            projectDocuments.some((document) => document.id === event.entityId)),
        );
      } catch (error) {
        showProblem(error);
      } finally {
        setLoading(false);
      }
    },
    [showProblem, token],
  );

  useEffect(() => {
    let active = true;
    restoreSession()
      .then(async (user) => {
        if (!active) return;
        setSession(user);
        if (user) await loadProjects(user.access_token);
      })
      .catch(showProblem)
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [loadProjects, showProblem]);

  useEffect(() => {
    if (!selectedProject || !token ||
      !documents.some((document) => processingStatuses.has(document.status))) return;
    const timer = window.setInterval(async () => {
      try {
        const next = await documentApi.list(token, selectedProject.id);
        setDocuments(next);
      } catch {
        // The visible error from the original action remains; polling retries automatically.
      }
    }, 5000);
    return () => window.clearInterval(timer);
  }, [documents, selectedProject, token]);

  const filteredProjects = useMemo(() => {
    const normalized = query.toLocaleLowerCase("tr");
    return projects.filter((project) => {
      const matchesText = `${project.projectCode} ${project.name} ${project.institutionName}`
        .toLocaleLowerCase("tr")
        .includes(normalized);
      return (
        matchesText &&
        (!statusFilter || project.status === statusFilter) &&
        (!institutionFilter ||
          project.institutionName
            .toLocaleLowerCase("tr")
            .includes(institutionFilter.toLocaleLowerCase("tr")))
      );
    });
  }, [institutionFilter, projects, query, statusFilter]);

  const metrics = useMemo(
    () => dashboardMetrics(projects, allDocuments),
    [allDocuments, projects],
  );

  async function createProject(event: FormEvent) {
    event.preventDefault();
    if (wizardStep < 4) {
      setWizardStep((step) => step + 1);
      return;
    }
    setBusy(true);
    setProblem(null);
    try {
      const created = await tenderApi.create(token, compactDraft(draft));
      setProjects((current) => [created, ...current]);
      setWizardOpen(false);
      setWizardStep(1);
      setDraft(emptyDraft);
      notify(`${created.projectCode} oluşturuldu`);
      await loadProject(created);
    } catch (error) {
      showProblem(error);
    } finally {
      setBusy(false);
    }
  }

  async function archiveProject() {
    if (!selectedProject) return;
    setBusy(true);
    try {
      const archived = await tenderApi.archive(token, selectedProject.id);
      setSelectedProject(archived);
      setProjects((current) =>
        current.map((project) => project.id === archived.id ? archived : project),
      );
      notify("Proje arşivlendi");
    } catch (error) {
      showProblem(error);
    } finally {
      setBusy(false);
    }
  }

  if (loading && session === undefined) return <LoadingScreen />;
  if (!session) {
    return <LoginScreen loading={busy} problem={problem} onLogin={async () => {
      setBusy(true);
      try {
        await signIn();
      } catch (error) {
        showProblem(error);
        setBusy(false);
      }
    }} />;
  }

  return (
    <div className="app-shell">
      {sidebarOpen && (
        <button className="scrim" aria-label="Menüyü kapat"
          onClick={() => setSidebarOpen(false)} />
      )}
      <aside className={sidebarOpen ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <span className="brand-symbol small">N</span>
          <div><b>NANObaseAI</b><small>Şartname AI</small></div>
          <button className="close-mobile" onClick={() => setSidebarOpen(false)}
            aria-label="Menüyü kapat"><X /></button>
        </div>
        <p className="nav-label">ÇALIŞMA ALANI</p>
        <button className={screen === "dashboard" ? "nav active" : "nav"}
          onClick={() => { setScreen("dashboard"); setSidebarOpen(false); }}>
          <Gauge /><span>Ana panel</span>
        </button>
        <button className={screen === "projects" || screen === "project" ? "nav active" : "nav"}
          onClick={() => { setScreen("projects"); setSidebarOpen(false); }}>
          <FolderKanban /><span>İhale projeleri</span><b>{projects.length}</b>
        </button>
        <button className="nav" disabled title="Bir sonraki fazda etkinleştirilecek">
          <Building2 /><span>Firma ve ürünler</span>
        </button>
        <p className="nav-label">GÜVENLİK</p>
        <button className="nav" onClick={() => notify(`Roller: ${roles.join(", ")}`)}>
          <ShieldCheck /><span>Yetkilerim</span>
        </button>
        <div className="engine">
          <Activity />
          <div><b>Doküman işleme</b><small>Gerçek zamanlı durum takibi</small></div>
        </div>
        <button className="profile" onClick={() => signOut()}>
          <span>{initials(displayName(session))}</span>
          <div><b>{displayName(session)}</b><small>{roles[0] ?? "Kullanıcı"}</small></div>
          <LogOut />
        </button>
      </aside>

      <div className="main">
        <header>
          <button className="mobile-menu" onClick={() => setSidebarOpen(true)}
            aria-label="Menüyü aç"><Menu /></button>
          <div className="search">
            <Search />
            <input value={query} onChange={(event) => setQuery(event.target.value)}
              placeholder="Proje kodu, adı veya kurum ara" aria-label="Projelerde ara" />
          </div>
          <button className="refresh" onClick={() => loadProjects().catch(showProblem)}>
            <RefreshCw /> Yenile
          </button>
        </header>

        <main className="content">
          {problem && <ProblemBanner problem={problem} onClose={() => setProblem(null)}
            onRetry={() => loadProjects().catch(showProblem)} />}
          {screen === "dashboard" && (
            <Dashboard metrics={metrics} events={auditEvents} projects={projects}
              onProjects={() => setScreen("projects")} />
          )}
          {screen === "projects" && (
            <ProjectList projects={filteredProjects} statusFilter={statusFilter}
              institutionFilter={institutionFilter} canWrite={canWrite}
              onStatus={setStatusFilter} onInstitution={setInstitutionFilter}
              onSelect={loadProject} onCreate={() => setWizardOpen(true)} />
          )}
          {screen === "project" && selectedProject && (
            <ProjectDetail project={selectedProject} tab={projectTab}
              onTab={setProjectTab} documents={documents} members={members}
              auditEvents={auditEvents} token={token} canWrite={canWrite}
              canAnalyze={canAnalyze}
              loading={loading} onBack={() => setScreen("projects")}
              onDocuments={setDocuments} onMembers={setMembers}
              onProblem={showProblem} onNotify={notify}
              onArchive={archiveProject} busy={busy} />
          )}
        </main>
      </div>

      {wizardOpen && (
        <ProjectWizard step={wizardStep} draft={draft} busy={busy}
          onDraft={setDraft} onStep={setWizardStep}
          onClose={() => { setWizardOpen(false); setWizardStep(1); }}
          onSubmit={createProject} />
      )}
      {toast && <div className="toast"><CheckCircle2 />{toast}
        <button onClick={() => setToast("")} aria-label="Bildirimi kapat"><X /></button>
      </div>}
    </div>
  );
}

function LoginScreen({ loading, problem, onLogin }: {
  loading: boolean;
  problem: ApiProblem | null;
  onLogin: () => void;
}) {
  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="brand-symbol">N</span>
        <p className="eyebrow">NANOBASEAI · ŞARTNAME AI</p>
        <h1>İhale dokümanlarını güvenle yönetin.</h1>
        <p className="login-copy">
          Projelerinizi oluşturun, PDF veya DOCX şartnameleri yükleyin ve
          işleme durumunu tek merkezden takip edin.
        </p>
        <div className="security-note"><ShieldCheck />
          <span>E-posta ve parola güvenli Keycloak giriş ekranında alınır.</span>
        </div>
        {problem && <p className="error"><AlertTriangle />{problem.detail}</p>}
        <button className="primary large" onClick={onLogin} disabled={loading}>
          {loading ? <LoaderCircle className="spin" /> : <LogIn />}
          Güvenli girişe devam et
        </button>
      </section>
    </main>
  );
}

function LoadingScreen() {
  return <main className="loading-screen"><LoaderCircle className="spin" />
    <p>Güvenli oturum hazırlanıyor…</p></main>;
}

function Dashboard({ metrics, projects, onProjects }: {
  metrics: ReturnType<typeof dashboardMetrics>;
  events: AuditEvent[];
  projects: TenderProject[];
  onProjects: () => void;
}) {
  const cards = [
    ["Aktif projeler", metrics.activeProjects, FolderKanban],
    ["Hazır dokümanlar", metrics.processedDocuments, CheckCircle2],
    ["Başarısız dokümanlar", metrics.failedDocuments, AlertTriangle],
    ["Yaklaşan tarihler", metrics.upcomingDeadlines.length, CalendarDays],
  ] as const;
  return <>
    <div className="title-row">
      <div><p className="eyebrow">OPERASYON ÖZETİ</p><h1>Ana panel</h1>
        <p>Veriler doğrudan tenant kapsamlı platform API’sinden alınır.</p></div>
      <button className="primary" onClick={onProjects}>Projeleri aç <ChevronRight /></button>
    </div>
    <section className="metric-grid">
      {cards.map(([label, value, Icon]) => <article className="metric-card" key={label}>
        <span><Icon /></span><p>{label}</p><strong>{value}</strong>
      </article>)}
    </section>
    <section className="dashboard-grid">
      <article className="panel">
        <div className="panel-head"><div><b>Yaklaşan son teklif tarihleri</b>
          <span>En yakın dört aktif proje</span></div><CalendarDays /></div>
        {metrics.upcomingDeadlines.length
          ? metrics.upcomingDeadlines.map((project) => <div className="deadline-row" key={project.id}>
              <span>{formatDate(project.bidDeadline)}</span>
              <div><b>{project.name}</b><small>{project.institutionName}</small></div>
              <em>{project.projectCode}</em>
            </div>)
          : <Empty text="Yaklaşan son teklif tarihi bulunmuyor." />}
      </article>
      <article className="panel">
        <div className="panel-head"><div><b>Proje durumları</b>
          <span>Gerçek proje portföyü</span></div><Activity /></div>
        {projects.length
          ? projects.slice(0, 6).map((project) => <div className="activity-row" key={project.id}>
              <span className={`status-dot ${project.status.toLowerCase()}`} />
              <div><b>{project.name}</b><small>{project.status.replaceAll("_", " ")}</small></div>
              <time>{project.projectCode}</time>
            </div>)
          : <Empty text="Henüz proje oluşturulmadı." />}
      </article>
    </section>
  </>;
}

function ProjectList({ projects, statusFilter, institutionFilter, canWrite, onStatus,
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
  return <>
    <div className="title-row">
      <div><p className="eyebrow">PROJE PORTFÖYÜ</p><h1>İhale projeleri</h1>
        <p>Yalnız erişim yetkiniz bulunan organization verileri gösterilir.</p></div>
      {canWrite && <button className="primary" onClick={onCreate}><Plus />Yeni proje</button>}
    </div>
    <section className="panel">
      <div className="filter-bar">
        <select value={statusFilter} onChange={(event) => onStatus(event.target.value)}
          aria-label="Duruma göre filtrele">
          <option value="">Tüm durumlar</option>
          <option value="DRAFT">Taslak</option>
          <option value="DOCUMENTS_PENDING">Doküman bekliyor</option>
          <option value="ANALYSIS_IN_PROGRESS">İşleniyor</option>
          <option value="COMPLETED">Tamamlandı</option>
          <option value="ARCHIVED">Arşivlendi</option>
        </select>
        <input value={institutionFilter}
          onChange={(event) => onInstitution(event.target.value)}
          placeholder="Kuruma göre filtrele" />
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Proje kodu</th><th>Proje adı</th><th>Kurum</th>
            <th>Son teklif</th><th>Durum</th><th>Sorumlu</th><th /></tr></thead>
          <tbody>
            {projects.map((project) => <tr key={project.id}>
              <td><span className="project-code">{project.projectCode}</span></td>
              <td><b>{project.name}</b></td>
              <td>{project.institutionName}</td>
              <td>{formatDate(project.bidDeadline)}</td>
              <td><span className={`project-status ${project.status.toLowerCase()}`}>
                {project.status.replaceAll("_", " ")}</span></td>
              <td className="owner">{initials(project.ownerUserId)}</td>
              <td><button className="icon-button" onClick={() => onSelect(project)}
                aria-label={`${project.name} detayını aç`}><ChevronRight /></button></td>
            </tr>)}
          </tbody>
        </table>
        {!projects.length && <Empty text="Filtrelerle eşleşen proje bulunamadı." />}
      </div>
    </section>
  </>;
}

function ProjectDetail({ project, tab, onTab, documents, members, auditEvents, token,
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
  return <>
    <button className="back" onClick={onBack}><ArrowLeft />Projelere dön</button>
    <div className="project-hero">
      <div><span>{project.projectCode}</span><h1>{project.name}</h1>
        <p>{project.institutionName} · {project.tenderRegistrationNumber || "Kayıt numarası yok"}</p></div>
      <span className={`project-status ${project.status.toLowerCase()}`}>
        {project.status.replaceAll("_", " ")}</span>
    </div>
    <nav className="tabs" aria-label="Proje detay sekmeleri">
      {([["overview", "Genel bakış"], ["documents", "Dokümanlar"],
        ["requirements", "Gereksinim matrisi"],
        ["activity", "Aktivite geçmişi"], ["settings", "Ayarlar"]] as const)
        .map(([id, label]) => <button key={id} className={tab === id ? "active" : ""}
          onClick={() => onTab(id)}>{label}</button>)}
    </nav>
    {loading ? <div className="processing"><LoaderCircle className="spin" />Yükleniyor…</div> : <>
      {tab === "overview" && <Overview project={project} documents={documents} members={members} />}
      {tab === "documents" && <DocumentCenter project={project} documents={documents}
        token={token} canWrite={canWrite} onDocuments={onDocuments}
        onProblem={onProblem} onNotify={onNotify} />}
      {tab === "requirements" && <RequirementsMatrix project={project}
        documents={documents} token={token} canWrite={canAnalyze}
        onProblem={onProblem} onNotify={onNotify} />}
      {tab === "activity" && <ActivityHistory events={auditEvents} />}
      {tab === "settings" && <ProjectSettings project={project} members={members}
        token={token} canWrite={canWrite} onMembers={onMembers}
        onProblem={onProblem} onNotify={onNotify}
        onArchive={onArchive} busy={busy} />}
    </>}
  </>;
}

function Overview({ project, documents, members }: {
  project: TenderProject;
  documents: ProjectDocument[];
  members: ProjectMember[];
}) {
  return <section className="overview-grid">
    <article className="panel detail-card"><h2>Temel bilgiler</h2>
      <dl>
        <div><dt>Kurum</dt><dd>{project.institutionName}</dd></div>
        <div><dt>İhale türü</dt><dd>{project.tenderType || "Belirtilmedi"}</dd></div>
        <div><dt>İş türü</dt><dd>{project.businessType || "Belirtilmedi"}</dd></div>
        <div><dt>Sektör</dt><dd>{project.sector || "Belirtilmedi"}</dd></div>
        <div><dt>Öncelik</dt><dd>{project.priority}</dd></div>
      </dl>
    </article>
    <article className="panel detail-card"><h2>Tarihler ve kapsam</h2>
      <dl>
        <div><dt>Son teklif</dt><dd>{formatDate(project.bidDeadline)}</dd></div>
        <div><dt>Soru sorma sonu</dt><dd>{formatDate(project.clarificationDeadline)}</dd></div>
        <div><dt>Doküman</dt><dd>{documents.length}</dd></div>
        <div><dt>Ekip üyesi</dt><dd>{members.length}</dd></div>
      </dl>
    </article>
    <article className="panel detail-card wide"><h2>Açıklama</h2>
      <p>{project.description || "Bu proje için açıklama eklenmemiş."}</p></article>
  </section>;
}

function RequirementsMatrix({ project, documents, token, canWrite, onProblem, onNotify }: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [requirements, setRequirements] = useState<Requirement[]>([]);
  const [columns, setColumns] = useState<RequirementColumn[]>([]);
  const [loading, setLoading] = useState(true);
  const [job, setJob] = useState<ExtractionJob>();
  const [documentId, setDocumentId] = useState(
    documents.find((document) => document.status === "READY" && document.includedInAnalysis)?.id ?? "",
  );
  const [explanation, setExplanation] = useState<Record<string, unknown>>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [page, grid] = await Promise.all([
        requirementApi.list(token, project.id),
        requirementApi.grid(token),
      ]);
      setRequirements(page.content);
      setColumns(grid.columns.filter((column) => column.visible));
    } catch (error) {
      onProblem(error);
    } finally {
      setLoading(false);
    }
  }, [onProblem, project.id, token]);

  useEffect(() => {
    const initialLoad = window.setTimeout(() => { void load(); }, 0);
    return () => window.clearTimeout(initialLoad);
  }, [load]);

  useEffect(() => {
    if (!job || ["COMPLETED", "FAILED", "CANCELLED"].includes(job.status)) return;
    const timer = window.setInterval(async () => {
      try {
        const current = await requirementApi.job(token, job.id);
        setJob(current);
        if (current.status === "COMPLETED") {
          window.clearInterval(timer);
          await load();
          onNotify(`${current.extractedRequirementCount} gereksinim çıkarıldı`);
        }
      } catch (error) {
        window.clearInterval(timer);
        onProblem(error);
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [job, load, onNotify, onProblem, token]);

  async function startExtraction() {
    if (!documentId) return;
    try {
      const created = await requirementApi.start(token, documentId);
      setJob(created);
      onNotify("Dinamik analiz profili oluşturuldu ve iş kuyruğa alındı");
    } catch (error) {
      onProblem(error);
    }
  }

  async function showExplanation(requirement: Requirement) {
    try {
      setExplanation(await requirementApi.explanation(token, requirement.id));
    } catch (error) {
      onProblem(error);
    }
  }

  return <section className="panel requirement-matrix">
    <div className="panel-head"><div><b>Dinamik gereksinim matrisi</b>
      <span>Kolonlar aktif ontology ve attributes şemasından yüklenir</span></div>
      {canWrite && <div className="requirement-actions">
        <select value={documentId} onChange={(event) => setDocumentId(event.target.value)}>
          <option value="">Hazır doküman seçin</option>
          {documents.filter((document) => document.status === "READY" &&
            document.includedInAnalysis).map((document) =>
            <option key={document.id} value={document.id}>{document.logicalName}</option>)}
        </select>
        <button className="primary" disabled={!documentId ||
          (job && !["COMPLETED", "FAILED", "CANCELLED"].includes(job.status))}
          onClick={startExtraction}><Activity />Analizi başlat</button>
      </div>}
    </div>
    {job && <div className="extraction-progress">
      <div><b>{job.status.replaceAll("_", " ")}</b>
        <span>{job.processedClauseCount}/{job.totalClauseCount} madde ·
          {" "}{job.extractedRequirementCount} gereksinim ·
          {" "}{job.manualReviewCount} inceleme</span></div>
      <progress max={Math.max(job.totalClauseCount, 1)} value={job.processedClauseCount} />
    </div>}
    {loading ? <div className="processing"><LoaderCircle className="spin" />Yükleniyor…</div>
      : <div className="requirement-table-wrap"><table className="requirement-table">
        <thead><tr>{columns.map((column) =>
          <th key={column.key}>{column.label}</th>)}<th>Açıklama</th></tr></thead>
        <tbody>{requirements.map((requirement) => <tr key={requirement.id}>
          {columns.map((column) => <td key={column.key}>
            {formatRequirementValue(requirement, column)}</td>)}
          <td><button className="icon-button" aria-label="Açıklamayı göster"
            onClick={() => showExplanation(requirement)}><Eye /></button></td>
        </tr>)}</tbody>
      </table>{!requirements.length &&
        <Empty text="Henüz çıkarılmış gereksinim bulunmuyor." />}</div>}
    {explanation && <div className="explanation-drawer">
      <div><b>Açıklanabilirlik</b><button onClick={() => setExplanation(undefined)}
        aria-label="Açıklamayı kapat"><X /></button></div>
      <pre>{JSON.stringify(explanation, null, 2)}</pre>
    </div>}
  </section>;
}

function formatRequirementValue(requirement: Requirement, column: RequirementColumn) {
  const aliases: Record<string, unknown> = {
    sourceClause: requirement.sourceClauseId,
    primaryConcept: requirement.primaryConceptId,
  };
  const value = column.key in aliases ? aliases[column.key] : valueAt(requirement, column.key);
  if (value === undefined || value === null || value === "") return "—";
  if (column.type === "PERCENT" && typeof value === "number") {
    return `%${Math.round(value * 100)}`;
  }
  if (typeof value === "object") return JSON.stringify(value);
  return String(value).replaceAll("_", " ");
}

function valueAt(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, part) => {
    if (!current || typeof current !== "object") return undefined;
    return (current as Record<string, unknown>)[part];
  }, value);
}

function DocumentCenter({ project, documents, token, canWrite, onDocuments,
  onProblem, onNotify }: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onDocuments: (documents: ProjectDocument[]) => void;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const [uploadOpen, setUploadOpen] = useState(false);
  const [busyId, setBusyId] = useState("");
  const [versions, setVersions] = useState<Record<string, boolean>>({});
  const [reviewDocumentId, setReviewDocumentId] = useState("");
  const reviewDocument = documents.find((item) => item.id === reviewDocumentId);

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const file = form.get("file");
    if (!(file instanceof File) || !file.size) return;
    setBusyId("upload");
    try {
      const created = await documentApi.upload(token, project.id, {
        file,
        documentType: form.get("documentType") as DocumentType,
        logicalName: String(form.get("logicalName") ?? ""),
        includedInAnalysis: form.get("includedInAnalysis") === "on",
      });
      onDocuments([created, ...documents]);
      setUploadOpen(false);
      onNotify("Doküman yüklendi ve işleme kuyruğuna alındı");
    } catch (error) {
      onProblem(error);
    } finally {
      setBusyId("");
    }
  }

  async function reprocess(document: ProjectDocument) {
    setBusyId(document.id);
    try {
      const updated = await documentApi.reprocess(token, document.id);
      onDocuments(documents.map((item) => item.id === updated.id ? updated : item));
      onNotify("Doküman yeniden işleme kuyruğuna alındı");
    } catch (error) {
      onProblem(error);
    } finally {
      setBusyId("");
    }
  }

  async function uploadVersion(document: ProjectDocument, file?: File) {
    if (!file?.size) return;
    setBusyId(document.id);
    try {
      const updated = await documentApi.uploadVersion(token, document.id, file);
      onDocuments(documents.map((item) => item.id === updated.id ? updated : item));
      onNotify(`v${updated.currentVersionNumber} yüklendi`);
    } catch (error) {
      onProblem(error);
    } finally {
      setBusyId("");
    }
  }

  return <section className="panel document-center">
    <div className="panel-head"><div><b>Doküman merkezi</b>
      <span>PDF ve DOCX · durumlar backend enumlarıyla birebir</span></div>
      {canWrite && <button className="primary" onClick={() => setUploadOpen(true)}>
        <Upload />Doküman yükle</button>}
    </div>
    {uploadOpen && <form className="inline-upload" onSubmit={upload}>
      <label>Dosya<input name="file" type="file" accept=".pdf,.docx" required /></label>
      <label>Mantıksal ad<input name="logicalName" maxLength={255}
        placeholder="Örn. Teknik Şartname" required /></label>
      <label>Doküman türü<select name="documentType" defaultValue="TECHNICAL_SPECIFICATION">
        {Object.entries(documentTypeLabels).map(([value, label]) =>
          <option key={value} value={value}>{label}</option>)}
      </select></label>
      <label className="check"><input name="includedInAnalysis" type="checkbox"
        defaultChecked />Analize dahil et</label>
      <div className="form-actions"><button type="button" className="secondary"
        onClick={() => setUploadOpen(false)}>Vazgeç</button>
        <button className="primary" disabled={busyId === "upload"}>
          {busyId === "upload" ? <LoaderCircle className="spin" /> : <Upload />}Yükle
        </button></div>
    </form>}
    <div className="document-table">
      {documents.map((document) => <article className="document-row" key={document.id}>
        <span className="file-icon"><FileText /></span>
        <div className="document-name"><b>{document.logicalName}</b>
          <small>{documentTypeLabels[document.documentType]} · v{document.currentVersionNumber}
            {document.currentVersion && ` · ${formatBytes(document.currentVersion.fileSize)}`}
            {document.currentVersion?.pageCount
              ? ` · ${document.currentVersion.pageCount} sayfa` : ""}</small>
          <small>{document.currentJob?.provider || "Parser bekleniyor"}
            {document.currentVersion?.ocrRequired ? " · OCR" : " · Dijital metin"}
            {` · ${document.createdBy} · ${new Date(document.createdAt)
              .toLocaleString("tr-TR")}`}</small></div>
        <span className={`processing-badge ${document.status.toLowerCase()}`}>
          {processingStatuses.has(document.status) && <LoaderCircle className="spin" />}
          {statusLabels[document.status]}
          {document.currentJob && processingStatuses.has(document.status)
            ? ` · %${document.currentJob.progress}` : ""}</span>
        {(document.currentJob?.errorMessage || document.currentVersion?.errorMessage) &&
          <p className="document-error">{document.currentJob?.errorMessage
            || document.currentVersion?.errorMessage}</p>}
        {!!document.currentJob?.warnings.length &&
          <p className="document-warning">{document.currentJob.warnings.length}
            {" "}parser uyarısı</p>}
        <div className="document-actions">
          <button title="Dokümanı incele" onClick={() => setReviewDocumentId(document.id)}>
            <Eye />
          </button>
          <button title="Versiyonları göster" onClick={() =>
            setVersions((current) => ({ ...current, [document.id]: !current[document.id] }))}>
            <FileClock />
          </button>
          <button title="İndir" onClick={() => documentApi.download(token, document.id)
            .catch(onProblem)}><Download /></button>
          {canWrite && <>
            <label title="Yeni versiyon yükle"><Upload />
              <input type="file" accept=".pdf,.docx" onChange={(event) =>
                uploadVersion(document, event.target.files?.[0])} /></label>
            <button title="Yeniden işle" disabled={busyId === document.id}
              onClick={() => reprocess(document)}><RefreshCw /></button>
          </>}
        </div>
        {versions[document.id] && <VersionList token={token} document={document}
          onProblem={onProblem} />}
      </article>)}
      {!documents.length && <Empty text="Bu projeye henüz doküman yüklenmedi." />}
    </div>
    {reviewDocument && <DocumentReview document={reviewDocument} token={token}
      canWrite={canWrite} onClose={() => setReviewDocumentId("")}
      onProblem={onProblem} />}
  </section>;
}

function VersionList({ token, document, onProblem }: {
  token: string;
  document: ProjectDocument;
  onProblem: (error: unknown) => void;
}) {
  const [items, setItems] = useState<Awaited<ReturnType<typeof documentApi.versions>>>();
  useEffect(() => {
    documentApi.versions(token, document.id).then(setItems).catch(onProblem);
  }, [document.id, onProblem, token]);
  return <div className="version-list">
    {!items ? <LoaderCircle className="spin" /> : items.map((version) =>
      <div key={version.id}><b>v{version.versionNumber}</b>
        <span>{version.originalFileName}</span><small>{statusLabels[version.processingStatus]}
          · {new Date(version.uploadedAt).toLocaleString("tr-TR")}</small></div>)}
  </div>;
}

function DocumentReview({ document, token, canWrite, onClose, onProblem }: {
  document: ProjectDocument;
  token: string;
  canWrite: boolean;
  onClose: () => void;
  onProblem: (error: unknown) => void;
}) {
  const [clauses, setClauses] = useState<Clause[]>([]);
  const [selected, setSelected] = useState<Clause>();
  const [jobs, setJobs] = useState<ProcessingJob[]>([]);
  const [pdfUrl, setPdfUrl] = useState("");
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState(1.15);
  const [query, setQuery] = useState("");
  const [liveEvent, setLiveEvent] = useState<ProcessingEvent>();
  const [streamState, setStreamState] = useState<"connecting" | "live" | "polling">(
    "connecting",
  );

  const load = useCallback(async () => {
    const [clausePage, jobPage] = await Promise.all([
      documentApi.clauses(token, document.id, query),
      documentApi.jobs(token, document.id),
    ]);
    setClauses(clausePage.content);
    setJobs(jobPage.content);
    if (!selected && clausePage.content[0]) setSelected(clausePage.content[0]);
  }, [document.id, query, selected, token]);

  useEffect(() => {
    const timer = window.setTimeout(() => load().catch(onProblem), 200);
    return () => window.clearTimeout(timer);
  }, [load, onProblem]);

  useEffect(() => {
    if (document.currentVersion?.mimeType !== "application/pdf") return;
    documentApi.downloadUrl(token, document.id)
      .then((response) => setPdfUrl(response.url))
      .catch(onProblem);
  }, [document.currentVersion?.mimeType, document.id, onProblem, token]);

  useEffect(() => {
    if (!processingStatuses.has(document.status)) {
      return;
    }
    const controller = new AbortController();
    let lastEventId: string | undefined;
    async function connect() {
      while (!controller.signal.aborted) {
        try {
          await subscribeToProcessingEvents(token, document.id, (event) => {
            lastEventId = event.eventId;
            setLiveEvent(event);
            setStreamState("live");
            setJobs((current) => current.map((job) => job.id === event.jobId
              ? { ...job, status: event.stage, currentStage: event.stage,
                progress: event.progress, updatedAt: event.occurredAt }
              : job));
          }, controller.signal, lastEventId);
        } catch {
          if (!controller.signal.aborted) setStreamState("polling");
        }
        if (!controller.signal.aborted) {
          await new Promise((resolve) => window.setTimeout(resolve, 2000));
        }
      }
    }
    connect();
    return () => controller.abort();
  }, [document.id, document.status, token]);

  useEffect(() => {
    if (!processingStatuses.has(document.status) && streamState !== "polling") return;
    const timer = window.setInterval(() => load().catch(() => undefined), 5000);
    return () => window.clearInterval(timer);
  }, [document.status, load, streamState]);

  const activeJob = jobs[0] ?? document.currentJob;
  const selectedBoxes = (selected?.boundingBoxes ?? [])
    .filter((box) => box.page === page);

  async function cancel() {
    if (!activeJob) return;
    try {
      const cancelled = await documentApi.cancel(token, activeJob.id);
      setJobs((current) => current.map((job) =>
        job.id === cancelled.id ? cancelled : job));
    } catch (error) {
      onProblem(error);
    }
  }

  return <div className="review-backdrop">
    <section className="document-review" aria-label="Doküman inceleme">
      <header className="review-head">
        <div><p className="eyebrow">DOKÜMAN İNCELEME</p><h2>{document.logicalName}</h2>
          <span>{activeJob?.provider || "Parser bekleniyor"} ·
            {" "}{activeJob ? statusLabels[activeJob.status] : statusLabels[document.status]}
            {" "}· %{liveEvent?.progress ?? activeJob?.progress ?? 0} ·
            {" "}{!processingStatuses.has(document.status) ? "Tamamlandı"
              : streamState === "live" ? "Canlı" : streamState === "polling"
              ? "Polling fallback" : "Bağlanıyor"}</span></div>
        <div className="review-head-actions">
          {canWrite && activeJob && processingStatuses.has(activeJob.status) &&
            <button className="secondary" onClick={cancel}>İptal</button>}
          <button className="icon-button" onClick={onClose} aria-label="İncelemeyi kapat">
            <X />
          </button>
        </div>
      </header>
      <div className="review-grid">
        <aside className="clause-panel">
          <label><Search /><input value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Maddelerde ara" /></label>
          <div className="clause-tree">
            <ClauseTree clauses={clauses} parentId={undefined} selectedId={selected?.id}
              onSelect={(clause) => { setSelected(clause); setPage(clause.pageStart); }} />
          </div>
        </aside>
        <main className="pdf-panel">
          <div className="pdf-toolbar">
            <button onClick={() => setPage((value) => Math.max(1, value - 1))}
              aria-label="Önceki sayfa"><ChevronLeft /></button>
            <span>Sayfa {page}</span>
            <button onClick={() => setPage((value) => value + 1)}
              aria-label="Sonraki sayfa"><ChevronRight /></button>
            <button onClick={() => setZoom((value) => Math.max(.6, value - .15))}
              aria-label="Uzaklaştır"><ZoomOut /></button>
            <button onClick={() => setZoom((value) => Math.min(2.5, value + .15))}
              aria-label="Yakınlaştır"><ZoomIn /></button>
          </div>
          {pdfUrl
            ? <PdfCanvas url={pdfUrl} pageNumber={page} zoom={zoom}
                highlights={selectedBoxes} />
            : <div className="pdf-placeholder"><FileText />
                <p>PDF önizlemesi yalnız PDF dokümanlarında gösterilir.</p></div>}
        </main>
        <aside className="clause-detail">
          {selected ? <>
            <p className="eyebrow">SEÇİLİ MADDE</p>
            <h3>{selected.number || "—"} {selected.title}</h3>
            <dl>
              <div><dt>Sayfa</dt><dd>{selected.pageStart}–{selected.pageEnd}</dd></div>
              <div><dt>Tür</dt><dd>{selected.clauseType || "Belirtilmedi"}</dd></div>
              <div><dt>Parser</dt><dd>{activeJob?.provider || "—"}</dd></div>
              <div><dt>Hash</dt><dd className="hash">{selected.contentHash}</dd></div>
            </dl>
            <h4>Ham metin</h4><p>{selected.rawText}</p>
            <h4>Normalize metin</h4><p>{selected.normalizedText}</p>
            <h4>Kaynak koordinatları</h4>
            <pre>{JSON.stringify(selected.boundingBoxes, null, 2)}</pre>
            {!!activeJob?.warnings.length && <>
              <h4>Parser uyarıları</h4>
              {activeJob.warnings.map((warning) =>
                <p className="warning-box" key={warning.id}>
                  <AlertTriangle />{warning.warningCode}: {warning.message}</p>)}
            </>}
          </> : <Empty text="İncelemek için bir madde seçin." />}
          <details className="job-history"><summary>Processing geçmişi</summary>
            {jobs.map((job) => <div key={job.id}><b>{statusLabels[job.status]}</b>
              <small>{job.provider || "—"} · %{job.progress} ·
                {" "}{new Date(job.createdAt).toLocaleString("tr-TR")}</small></div>)}
          </details>
        </aside>
      </div>
    </section>
  </div>;
}

function ClauseTree({ clauses, parentId, selectedId, onSelect }: {
  clauses: Clause[];
  parentId?: string;
  selectedId?: string;
  onSelect: (clause: Clause) => void;
}) {
  const children = clauses.filter((clause) => clause.parentId === parentId
    || (!clause.parentId && !parentId));
  if (!children.length) return parentId ? null : <Empty text="Henüz madde çıkarılmadı." />;
  return <ul>{children.map((clause) => <li key={clause.id}>
    <button className={selectedId === clause.id ? "active" : ""}
      onClick={() => onSelect(clause)}>
      <b>{clause.number || "•"}</b><span>{clause.title || clause.normalizedText.slice(0, 80)}</span>
      <small>s. {clause.pageStart}</small>
    </button>
    <ClauseTree clauses={clauses} parentId={clause.id}
      selectedId={selectedId} onSelect={onSelect} />
  </li>)}</ul>;
}

function PdfCanvas({ url, pageNumber, zoom, highlights }: {
  url: string;
  pageNumber: number;
  zoom: number;
  highlights: BoundingBox[];
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [pdf, setPdf] = useState<PDFDocumentProxy>();
  const [renderError, setRenderError] = useState("");

  useEffect(() => {
    let active = true;
    let loaded: PDFDocumentProxy | undefined;
    import("pdfjs-dist").then((pdfjs) => {
      pdfjs.GlobalWorkerOptions.workerSrc =
        new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();
      return pdfjs.getDocument({ url }).promise;
    }).then((document) => {
      loaded = document;
      if (active) {
        setPdf(document);
      }
    }).catch(() => active && setRenderError("PDF güvenli biçimde görüntülenemedi"));
    return () => {
      active = false;
      loaded?.destroy();
    };
  }, [url]);

  useEffect(() => {
    if (!pdf || !canvasRef.current) return;
    let cancelled = false;
    pdf.getPage(Math.min(pageNumber, pdf.numPages)).then(async (pdfPage) => {
      if (cancelled || !canvasRef.current) return;
      const viewport = pdfPage.getViewport({ scale: zoom });
      const canvas = canvasRef.current;
      const context = canvas.getContext("2d");
      if (!context) return;
      canvas.width = Math.floor(viewport.width);
      canvas.height = Math.floor(viewport.height);
      await pdfPage.render({ canvas, canvasContext: context, viewport }).promise;
    }).catch(() => !cancelled && setRenderError("PDF sayfası görüntülenemedi"));
    return () => { cancelled = true; };
  }, [pageNumber, pdf, zoom]);

  if (renderError) return <div className="pdf-placeholder"><AlertTriangle />
    <p>{renderError}</p></div>;
  return <div className="pdf-scroll"><div className="pdf-page">
    <canvas ref={canvasRef} />
    <div className="source-highlights">{highlights.map((box, index) =>
      <span key={`${box.page}-${index}`} style={{
        left: `${box.x * 100}%`,
        top: `${box.y * 100}%`,
        width: `${box.width * 100}%`,
        height: `${box.height * 100}%`,
      }} />)}</div>
  </div></div>;
}

function ActivityHistory({ events }: { events: AuditEvent[] }) {
  return <section className="panel activity-history">
    <div className="panel-head"><div><b>Aktivite geçmişi</b>
      <span>Değiştirilemeyen tenant kapsamlı audit kayıtları</span></div><Activity /></div>
    {events.map((event) => <div className="audit-row" key={event.id}>
      <span><Activity /></span><div><b>{event.eventType.replaceAll("_", " ")}</b>
        <small>{event.userId} · Correlation: {event.correlationId}</small></div>
      <time>{new Date(event.createdAt).toLocaleString("tr-TR")}</time>
    </div>)}
    {!events.length && <Empty text="Bu proje için aktivite kaydı bulunmuyor." />}
  </section>;
}

function ProjectSettings({ project, members, token, canWrite, onMembers, onProblem,
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

function ProjectWizard({ step, draft, busy, onDraft, onStep, onClose, onSubmit }: {
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

function ProblemBanner({ problem, onClose, onRetry }: {
  problem: ApiProblem;
  onClose: () => void;
  onRetry: () => void;
}) {
  return <div className="problem-banner" role="alert"><AlertTriangle />
    <div><b>{problem.title}</b><p>{problem.detail}</p>
      {problem.correlationId && <small>Correlation ID: {problem.correlationId}</small>}
      {problem.fieldErrors?.map((error) =>
        <small key={error.field}>{error.field}: {error.message}</small>)}</div>
    {problem.status >= 500 && <button onClick={onRetry}>Tekrar dene</button>}
    <button onClick={onClose} aria-label="Hatayı kapat"><X /></button>
  </div>;
}

function Empty({ text }: { text: string }) {
  return <div className="empty"><FileText /><p>{text}</p></div>;
}

function compactDraft(draft: TenderDraft): TenderDraft {
  return Object.fromEntries(
    Object.entries(draft).filter(([, value]) => value !== ""),
  ) as TenderDraft;
}

function formatDate(value?: string) {
  if (!value) return "Belirtilmedi";
  return new Date(`${value}T00:00:00`).toLocaleDateString("tr-TR");
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function initials(value: string) {
  return value.split(/[\s@._-]+/).filter(Boolean).slice(0, 2)
    .map((part) => part[0]?.toUpperCase()).join("") || "K";
}
