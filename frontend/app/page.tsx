"use client";

import {
  CheckCircle2,
  ClipboardCheck,
  Eye,
  FlaskConical,
  FolderKanban,
  Gauge,
  LoaderCircle,
  LogIn,
  ServerCog,
  X,
} from "lucide-react";
import type { AuthSession } from "@/src/modules/auth/auth";
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppShell } from "@/src/components/layout/AppShell";
import type { SidebarNavItem } from "@/src/components/layout/Sidebar";
import { PRODUCT_BRAND, PRODUCT_BRAND_SHORT } from "@/src/config/brand";
import { useProjectProgress } from "@/src/hooks/useProjectProgress";
import {
  buildPortalHash,
  parsePortalHash,
  type PortalRoute,
} from "@/src/lib/portal-route";
import {
  compactDraft,
  emptyDraft,
  initials,
  processingStatuses,
  type AnalysisStep,
  type FindingsFilter,
  type ProjectHub,
  type ProjectSubNav,
  type ProjectTab,
  type Screen,
} from "@/src/lib/portal-utils";
import {
  loadExpertMode,
  loadProjectUx,
  saveExpertMode,
  saveProjectUx,
} from "@/src/lib/project-ux-storage";
import type { Sprint7Tab, Sprint9Tab } from "@/src/screens/_shared";
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
  const [projectTab, setProjectTab] = useState<ProjectTab>("documents");
  const [projectHub, setProjectHub] = useState<ProjectHub>("analysis");
  const [analysisStep, setAnalysisStep] = useState<AnalysisStep>("documents");
  const [projectSubNav, setProjectSubNav] = useState<ProjectSubNav>("overview");
  const [findingsFilter, setFindingsFilter] = useState<FindingsFilter>("risks");
  const [expertMode, setExpertMode] = useState(false);
  const [uxHydrated, setUxHydrated] = useState(false);
  const [resumeRecommended, setResumeRecommended] = useState(false);
  const [workflowTab, setWorkflowTab] = useState<Sprint7Tab>("tasks");
  const [pilotTab, setPilotTab] = useState<Sprint9Tab>("quality");
  const [routeReady, setRouteReady] = useState(false);
  const [projectsLoaded, setProjectsLoaded] = useState(false);
  const applyingHash = useRef(false);
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
  const canAnalyze =
    Boolean(session) &&
    (canWrite || roles.includes("TECHNICAL_REVIEWER"));
  const canOperate =
    Boolean(session) &&
    roles.some((role) => ["SYSTEM_ADMIN", "TENANT_ADMIN"].includes(role));

  const progress = useProjectProgress(token, selectedProject?.id, documents);
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
      if (!accessToken) {
        setProjectsLoaded(true);
        return;
      }
      setProblem(null);
      try {
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
      } finally {
        setProjectsLoaded(true);
      }
    },
    [token],
  );

  const loadProject = useCallback(
    async (
      project: TenderProject,
      accessToken = token,
      route?: Extract<PortalRoute, { screen: "project" }>,
    ) => {
      if (!accessToken) return;
      setSelectedProject(project);
      setScreen("project");
      setLoading(true);
      setProblem(null);

      if (route) {
        setResumeRecommended(false);
        if (route.expertMode) {
          setExpertMode(true);
          setProjectTab(route.tab);
        } else {
          setExpertMode(false);
          setProjectHub(route.hub);
          setAnalysisStep(route.analysisStep);
          setFindingsFilter(route.findingsFilter);
          setProjectSubNav(route.projectSubNav);
          if (route.hub === "analysis") setProjectTab(route.analysisStep);
          else if (route.hub === "findings") setProjectTab(route.findingsFilter);
          else if (route.hub === "changes") setProjectTab("changes");
          else setProjectTab(route.projectSubNav);
        }
      } else {
        setExpertMode(loadExpertMode());
        const prefs = loadProjectUx(project.id);
        if (prefs) {
          setProjectHub(prefs.lastHub);
          setAnalysisStep(prefs.lastStep);
          setProjectSubNav(prefs.projectSubNav);
          setResumeRecommended(false);
          if (prefs.lastHub === "analysis") {
            setProjectTab(prefs.lastStep);
          } else if (prefs.lastHub === "findings") {
            setProjectTab("risks");
          } else if (prefs.lastHub === "changes") {
            setProjectTab("changes");
          } else {
            setProjectTab(prefs.projectSubNav);
          }
        } else {
          setProjectHub("analysis");
          setAnalysisStep("documents");
          setProjectSubNav("overview");
          setProjectTab("documents");
          setResumeRecommended(true);
        }
      }
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

  const applyRoute = useCallback(
    async (route: PortalRoute) => {
      applyingHash.current = true;
      try {
        if (route.screen === "dashboard") {
          setScreen("dashboard");
          return;
        }
        if (route.screen === "projects") {
          setScreen("projects");
          return;
        }
        if (route.screen === "operations") {
          if (canOperate) setScreen("operations");
          return;
        }
        if (route.screen === "workflows") {
          if (!canAnalyze) return;
          setWorkflowTab(route.tab);
          setScreen("workflows");
          return;
        }
        if (route.screen === "pilot-quality") {
          if (!canAnalyze) return;
          setPilotTab(route.tab);
          setScreen("pilot-quality");
          return;
        }
        if (route.screen === "project") {
          const project = projects.find((item) => item.id === route.projectId);
          if (!project) {
            setScreen("projects");
            return;
          }
          if (selectedProject?.id === project.id) {
            if (route.expertMode) {
              setExpertMode(true);
              setProjectTab(route.tab);
            } else {
              setExpertMode(false);
              setProjectHub(route.hub);
              setAnalysisStep(route.analysisStep);
              setFindingsFilter(route.findingsFilter);
              setProjectSubNav(route.projectSubNav);
            }
            setScreen("project");
            return;
          }
          await loadProject(project, token, route);
        }
      } finally {
        window.setTimeout(() => {
          applyingHash.current = false;
        }, 0);
      }
    },
    [
      canAnalyze,
      canOperate,
      loadProject,
      projects,
      selectedProject?.id,
      token,
    ],
  );

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const user = await restoreSession();
        if (!active) return;
        setSession(user);
        setLoading(false);
        if (user) {
          try {
            await loadProjects(user.access_token);
          } catch (error) {
            showProblem(error);
          }
        }
      } catch (error) {
        if (!active) return;
        setSession(null);
        setLoading(false);
        showProblem(error);
      }
    })();
    return () => {
      active = false;
    };
    // Mount-once bootstrap; loadProjects/showProblem are stable enough via token default arg.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  useEffect(() => {
    setExpertMode(loadExpertMode());
    setUxHydrated(true);
  }, []);

  useEffect(() => {
    if (session === null) setProjectsLoaded(true);
  }, [session]);

  useEffect(() => {
    if (!uxHydrated || !projectsLoaded || routeReady || session === undefined) return;
    const route = parsePortalHash(window.location.hash);
    if (!route) {
      setRouteReady(true);
      return;
    }
    void applyRoute(route).finally(() => setRouteReady(true));
  }, [applyRoute, projectsLoaded, routeReady, session, uxHydrated]);

  useEffect(() => {
    if (!uxHydrated || !routeReady) return;
    const onHashChange = () => {
      const route = parsePortalHash(window.location.hash);
      if (route) void applyRoute(route);
    };
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, [applyRoute, routeReady, uxHydrated]);

  useEffect(() => {
    if (!uxHydrated || !routeReady || applyingHash.current) return;
    let route: PortalRoute;
    if (screen === "project" && selectedProject) {
      route = expertMode
        ? {
            screen: "project",
            projectId: selectedProject.id,
            expertMode: true,
            tab: projectTab,
          }
        : {
            screen: "project",
            projectId: selectedProject.id,
            expertMode: false,
            hub: projectHub,
            analysisStep,
            findingsFilter,
            projectSubNav,
          };
    } else if (screen === "workflows") {
      route = { screen: "workflows", tab: workflowTab };
    } else if (screen === "pilot-quality") {
      route = { screen: "pilot-quality", tab: pilotTab };
    } else if (screen === "operations") {
      route = { screen: "operations" };
    } else if (screen === "projects") {
      route = { screen: "projects" };
    } else {
      route = { screen: "dashboard" };
    }
    const next = buildPortalHash(route);
    if (window.location.hash !== next) {
      window.history.replaceState(null, "", next);
    }
  }, [
    analysisStep,
    expertMode,
    findingsFilter,
    pilotTab,
    projectHub,
    projectSubNav,
    projectTab,
    routeReady,
    screen,
    selectedProject,
    uxHydrated,
    workflowTab,
  ]);

  useEffect(() => {
    if (!resumeRecommended || progress.loading) return;
    setAnalysisStep(progress.recommended);
    setProjectTab(progress.recommended);
    setResumeRecommended(false);
  }, [progress.loading, progress.recommended, resumeRecommended]);

  useEffect(() => {
    if (!uxHydrated) return;
    saveExpertMode(expertMode);
  }, [expertMode, uxHydrated]);

  useEffect(() => {
    if (!selectedProject) return;
    saveProjectUx(selectedProject.id, {
      lastHub: projectHub,
      lastStep: analysisStep,
      projectSubNav,
    });
  }, [analysisStep, projectHub, projectSubNav, selectedProject]);

  function applyExpertMode(next: boolean) {
    if (next) {
      if (projectHub === "analysis") setProjectTab(analysisStep);
      else if (projectHub === "findings") setProjectTab(findingsFilter);
      else if (projectHub === "changes") setProjectTab("changes");
      else setProjectTab(projectSubNav);
    } else if (
      projectTab === "documents" ||
      projectTab === "requirements" ||
      projectTab === "knowledge" ||
      projectTab === "compliance" ||
      projectTab === "risks"
    ) {
      setProjectHub("analysis");
      setAnalysisStep(projectTab);
    } else if (
      projectTab === "conflicts" ||
      projectTab === "ambiguities"
    ) {
      setProjectHub("findings");
      setFindingsFilter(projectTab);
    } else if (projectTab === "changes") {
      setProjectHub("changes");
    } else {
      setProjectHub("project");
      if (
        projectTab === "overview" ||
        projectTab === "activity" ||
        projectTab === "settings"
      ) {
        setProjectSubNav(projectTab);
      }
    }
    setExpertMode(next);
  }

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
    if (wizardStep < 3) {
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
    ...(canAnalyze
      ? [
          {
            id: "workflows",
            label: "Workflow",
            icon: ClipboardCheck,
            group: "Gelişmiş",
            active: screen === "workflows",
            onClick: () => setScreen("workflows"),
          } satisfies SidebarNavItem,
          {
            id: "pilot-quality",
            label: "Pilot kalite",
            icon: FlaskConical,
            group: "Gelişmiş",
            active: screen === "pilot-quality",
            onClick: () => setScreen("pilot-quality"),
          } satisfies SidebarNavItem,
        ]
      : []),
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
        if (!session) return;
        void signOut().then(() => {
          setSession(null);
          notify("Oturum kapatıldı");
        });
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
          onContinueAnalysis={(project) => {
            void loadProject(project);
          }}
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
          hub={projectHub}
          onHub={setProjectHub}
          analysisStep={analysisStep}
          onAnalysisStep={(step) => {
            setAnalysisStep(step);
            setProjectTab(step);
          }}
          projectSubNav={projectSubNav}
          onProjectSubNav={setProjectSubNav}
          findingsFilter={findingsFilter}
          onFindingsFilter={setFindingsFilter}
          expertMode={expertMode}
          onExpertMode={applyExpertMode}
          tab={projectTab}
          onTab={setProjectTab}
          completion={progress.completion}
          documents={documents}
          members={members}
          auditEvents={auditEvents}
          token={token}
          canWrite={canWrite}
          canAnalyze={canAnalyze}
          loading={loading}
          onBack={() => setScreen("projects")}
          onDocuments={(next) => {
            setDocuments(next);
            void progress.refresh();
          }}
          onMembers={setMembers}
          onProblem={showProblem}
          onNotify={(message) => {
            notify(message);
            void progress.refresh();
          }}
          onArchive={archiveProject}
          busy={busy}
        />
      )}
      {screen === "operations" && canOperate && (
        <OperationsCenter
          token={token}
          projects={projects}
          documents={allDocuments}
          onProblem={showProblem}
        />
      )}
      {screen === "workflows" && canAnalyze && (
        <Sprint7Workspace
          token={token}
          project={selectedProject}
          canConfigure={canOperate}
          canWrite={canAnalyze}
          onProblem={showProblem}
          onNotify={notify}
          tab={workflowTab}
          onTab={setWorkflowTab}
        />
      )}
      {screen === "pilot-quality" && canAnalyze && (
        <Sprint9ControlCenter
          token={token}
          canOperate={canOperate}
          onProblem={showProblem}
          onNotify={notify}
          tab={pilotTab}
          onTab={setPilotTab}
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
