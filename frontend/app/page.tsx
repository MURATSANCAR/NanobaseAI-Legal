"use client";

import {
  Building2,
  CheckCircle2,
  ClipboardCheck,
  Eye,
  FlaskConical,
  FolderKanban,
  Gauge,
  LoaderCircle,
  LogIn,
  ServerCog,
  ShieldCheck,
  X,
} from "lucide-react";
import type { AuthSession } from "@/src/modules/auth/auth";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/src/components/layout/AppShell";
import type { SidebarNavItem } from "@/src/components/layout/Sidebar";
import { PRODUCT_BRAND, PRODUCT_BRAND_SHORT } from "@/src/config/brand";
import {
  compactDraft,
  emptyDraft,
  initials,
  processingStatuses,
  type ProjectTab,
  type Screen,
} from "@/src/lib/portal-utils";
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
  type ProjectDocument,
} from "@/src/modules/documents/api";
import {
  tenderApi,
  type ProjectMember,
  type TenderDraft,
  type TenderProject,
} from "@/src/modules/tenders/api";
import { isApiError, type ApiProblem } from "@/src/shared/api";
import {
  Dashboard,
  LoadingScreen,
  OperationsCenter,
  ProblemBanner,
  ProjectDetail,
  ProjectList,
  ProjectWizard,
  Sprint7Workspace,
  Sprint9ControlCenter,
} from "@/src/screens";

export default function SpecAiPortal() {
  const [session, setSession] = useState<AuthSession | null>();
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
  const [loginEmail, setLoginEmail] = useState("admin@nanobase.local");
  const [loginPassword, setLoginPassword] = useState("");
  const [loginOpen, setLoginOpen] = useState(false);

  const directAccess = session === null;
  const token = session?.access_token ?? "";
  const roles = session ? realmRoles(session) : ["DIRECT_ACCESS_VIEWER"];
  const canWrite = Boolean(session) && roles.some((role) =>
    ["SYSTEM_ADMIN", "TENANT_ADMIN", "TENDER_MANAGER"].includes(role),
  );
  const canAnalyze = directAccess || canWrite || roles.includes("TECHNICAL_REVIEWER");
  const canOperate =
    directAccess ||
    (Boolean(session) &&
      roles.some((role) => ["SYSTEM_ADMIN", "TENANT_ADMIN"].includes(role)));

  const showProblem = useCallback((error: unknown) => {
    if (isApiError(error)) setProblem(error.problem);
    else
      setProblem({
        title: "Beklenmeyen hata",
        status: 500,
        detail: error instanceof Error ? error.message : "İşlem tamamlanamadı.",
      });
  }, []);

  const showSurfaceProblem = useCallback(
    (error: unknown) => {
      if (!directAccess) showProblem(error);
    },
    [directAccess, showProblem],
  );

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
          auditPage.content.filter(
            (event) =>
              event.entityId === project.id ||
              projectDocuments.some((document) => document.id === event.entityId),
          ),
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
    if (
      !selectedProject ||
      !token ||
      !documents.some((document) => processingStatuses.has(document.status))
    )
      return;
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
        current.map((project) => (project.id === archived.id ? archived : project)),
      );
      notify("Proje arşivlendi");
    } catch (error) {
      showProblem(error);
    } finally {
      setBusy(false);
    }
  }

  if (loading && session === undefined) return <LoadingScreen />;

  const navItems: SidebarNavItem[] = [
    {
      id: "dashboard",
      label: "Ana panel",
      icon: Gauge,
      group: "Çalışma alanı",
      active: screen === "dashboard",
      onClick: () => setScreen("dashboard"),
    },
    {
      id: "projects",
      label: "İhale projeleri",
      icon: FolderKanban,
      group: "Çalışma alanı",
      active: screen === "projects" || screen === "project",
      badge: projects.length,
      onClick: () => setScreen("projects"),
    },
    {
      id: "knowledge",
      label: "Firma ve ürünler",
      icon: Building2,
      group: "Çalışma alanı",
      active: screen === "project" && projectTab === "knowledge",
      onClick: () => {
        if (selectedProject) {
          setScreen("project");
          setProjectTab("knowledge");
        } else {
          setScreen("projects");
        }
      },
    },
    {
      id: "workflows",
      label: "Workflow merkezi",
      icon: ClipboardCheck,
      group: "Çalışma alanı",
      active: screen === "workflows",
      onClick: () => setScreen("workflows"),
    },
    ...(canAnalyze
      ? [
          {
            id: "pilot-quality",
            label: "Pilot kalite merkezi",
            icon: FlaskConical,
            group: "Çalışma alanı",
            active: screen === "pilot-quality",
            onClick: () => setScreen("pilot-quality"),
          } satisfies SidebarNavItem,
        ]
      : []),
    {
      id: "roles",
      label: "Yetkilerim",
      icon: ShieldCheck,
      group: "Güvenlik",
      onClick: () => notify(`Roller: ${roles.join(", ")}`),
    },
    ...(canOperate
      ? [
          {
            id: "operations",
            label: "Production kontrolü",
            icon: ServerCog,
            group: "Güvenlik",
            active: screen === "operations",
            onClick: () => setScreen("operations"),
          } satisfies SidebarNavItem,
        ]
      : []),
  ];

  return (
    <AppShell
      sidebarOpen={sidebarOpen}
      onSidebarOpen={() => setSidebarOpen(true)}
      onSidebarClose={() => setSidebarOpen(false)}
      navItems={navItems}
      brandTitle="NanobaseAI"
      brandSubtitle={PRODUCT_BRAND_SHORT}
      profileName={session ? displayName(session) : "Doğrudan erişim"}
      profileRole={session ? (roles[0] ?? "Kullanıcı") : "Salt okunur"}
      profileInitials={session ? initials(displayName(session)) : "DA"}
      onProfileClick={() => {
        if (session) {
          void signOut().then(() => {
            setSession(null);
            notify("Oturum kapatıldı");
          });
        } else notify("Doğrudan erişim modu · değişiklik işlemleri kapalı");
      }}
      searchQuery={query}
      onSearchChange={setQuery}
      onRefresh={() => loadProjects().catch(showProblem)}
    >
      {directAccess && !(process.env.NEXT_PUBLIC_AUTO_LOGIN ?? "").toLowerCase().includes("true") && (
        <section className="direct-access-banner">
          <div>
            <Eye />
            <span>
              <b>Doğrudan erişim açık</b>
              Tüm modülleri gezebilirsiniz. Veri değiştiren işlemler için güvenli oturum
              gerekir.
            </span>
          </div>
          {!loginOpen ? (
            <button
              className="secondary"
              disabled={busy}
              onClick={() => setLoginOpen(true)}
            >
              <LogIn />
              Canlı veriye bağlan
            </button>
          ) : (
            <form
              className="direct-access-login"
              onSubmit={async (event: FormEvent) => {
                event.preventDefault();
                setBusy(true);
                try {
                  const next = await signIn(loginEmail, loginPassword);
                  setSession(next);
                  setLoginOpen(false);
                  setLoginPassword("");
                  await loadProjects(next.access_token);
                  notify("Oturum açıldı");
                } catch (error) {
                  showProblem(error);
                } finally {
                  setBusy(false);
                }
              }}
            >
              <input
                type="email"
                autoComplete="username"
                value={loginEmail}
                onChange={(event) => setLoginEmail(event.target.value)}
                placeholder="E-posta"
                required
              />
              <input
                type="password"
                autoComplete="current-password"
                value={loginPassword}
                onChange={(event) => setLoginPassword(event.target.value)}
                placeholder="Parola"
                required
              />
              <button className="secondary" type="submit" disabled={busy}>
                {busy ? <LoaderCircle className="spin" /> : <LogIn />}
                Giriş yap
              </button>
              <button
                type="button"
                className="ghost"
                onClick={() => setLoginOpen(false)}
              >
                <X />
              </button>
            </form>
          )}
        </section>
      )}
      {problem && (
        <ProblemBanner
          problem={problem}
          onClose={() => setProblem(null)}
          onRetry={() => loadProjects().catch(showProblem)}
        />
      )}
      {screen === "dashboard" && (
        <Dashboard
          metrics={metrics}
          events={auditEvents}
          projects={projects}
          onProjects={() => setScreen("projects")}
        />
      )}
      {screen === "projects" && (
        <ProjectList
          projects={filteredProjects}
          statusFilter={statusFilter}
          institutionFilter={institutionFilter}
          canWrite={canWrite}
          onStatus={setStatusFilter}
          onInstitution={setInstitutionFilter}
          onSelect={loadProject}
          onCreate={() => setWizardOpen(true)}
        />
      )}
      {screen === "project" && selectedProject && (
        <ProjectDetail
          project={selectedProject}
          tab={projectTab}
          onTab={setProjectTab}
          documents={documents}
          members={members}
          auditEvents={auditEvents}
          token={token}
          canWrite={canWrite}
          canAnalyze={canAnalyze}
          loading={loading}
          onBack={() => setScreen("projects")}
          onDocuments={setDocuments}
          onMembers={setMembers}
          onProblem={showProblem}
          onNotify={notify}
          onArchive={archiveProject}
          busy={busy}
        />
      )}
      {screen === "operations" && canOperate && (
        <OperationsCenter
          token={token}
          projects={projects}
          documents={allDocuments}
          onProblem={showSurfaceProblem}
        />
      )}
      {screen === "workflows" && (
        <Sprint7Workspace
          token={token}
          project={selectedProject}
          canConfigure={!directAccess && canOperate}
          canWrite={!directAccess && canAnalyze}
          onProblem={showSurfaceProblem}
          onNotify={notify}
        />
      )}
      {screen === "pilot-quality" && canAnalyze && (
        <Sprint9ControlCenter
          token={token}
          canOperate={!directAccess && canOperate}
          onProblem={showSurfaceProblem}
          onNotify={notify}
        />
      )}

      {wizardOpen && (
        <ProjectWizard
          step={wizardStep}
          draft={draft}
          busy={busy}
          onDraft={setDraft}
          onStep={setWizardStep}
          onClose={() => {
            setWizardOpen(false);
            setWizardStep(1);
          }}
          onSubmit={createProject}
        />
      )}
      {toast && (
        <div className="toast">
          <CheckCircle2 />
          {toast}
          <button onClick={() => setToast("")} aria-label="Bildirimi kapat">
            <X />
          </button>
        </div>
      )}
      <span className="sr-only">{PRODUCT_BRAND}</span>
    </AppShell>
  );
}
