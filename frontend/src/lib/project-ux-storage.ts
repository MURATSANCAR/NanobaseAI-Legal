import type {
  AnalysisStep,
  ProjectHub,
  ProjectSubNav,
} from "@/src/lib/portal-utils";

const STORAGE_KEY = "nb.ai.projectUx";

export type ProjectUxPrefs = {
  lastHub: ProjectHub;
  lastStep: AnalysisStep;
  projectSubNav: ProjectSubNav;
};

export type ProjectUxStore = {
  expertMode: boolean;
  projects: Record<string, ProjectUxPrefs>;
};

const defaults: ProjectUxStore = {
  expertMode: false,
  projects: {},
};

function readStore(): ProjectUxStore {
  if (typeof window === "undefined") return defaults;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw) as Partial<ProjectUxStore>;
    return {
      expertMode: Boolean(parsed.expertMode),
      projects: parsed.projects ?? {},
    };
  } catch {
    return defaults;
  }
}

function writeStore(store: ProjectUxStore) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
}

export function loadExpertMode(): boolean {
  return readStore().expertMode;
}

export function saveExpertMode(expertMode: boolean) {
  const store = readStore();
  writeStore({ ...store, expertMode });
}

export function loadProjectUx(projectId: string): ProjectUxPrefs | null {
  return readStore().projects[projectId] ?? null;
}

export function saveProjectUx(projectId: string, prefs: ProjectUxPrefs) {
  const store = readStore();
  writeStore({
    ...store,
    projects: {
      ...store.projects,
      [projectId]: prefs,
    },
  });
}
