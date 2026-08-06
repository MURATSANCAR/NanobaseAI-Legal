import type {
  AnalysisStep,
  FindingsFilter,
  ProjectHub,
  ProjectSubNav,
  ProjectTab,
} from "@/src/lib/portal-utils";
import type { Sprint7Tab, Sprint9Tab } from "@/src/screens/_shared";

export type PortalRoute =
  | { screen: "dashboard" }
  | { screen: "projects" }
  | {
      screen: "project";
      projectId: string;
      expertMode: false;
      hub: ProjectHub;
      analysisStep: AnalysisStep;
      findingsFilter: FindingsFilter;
      projectSubNav: ProjectSubNav;
    }
  | {
      screen: "project";
      projectId: string;
      expertMode: true;
      tab: ProjectTab;
    }
  | { screen: "workflows"; tab: Sprint7Tab }
  | { screen: "pilot-quality"; tab: Sprint9Tab }
  | { screen: "operations" };

const ANALYSIS_STEPS: AnalysisStep[] = [
  "documents",
  "requirements",
  "knowledge",
  "compliance",
  "risks",
];
const HUBS: ProjectHub[] = ["analysis", "findings", "changes", "project"];
const FILTERS: FindingsFilter[] = ["risks", "conflicts", "ambiguities"];
const SUBNAVS: ProjectSubNav[] = ["overview", "activity", "settings"];
const PROJECT_TABS: ProjectTab[] = [
  "overview",
  "documents",
  "requirements",
  "knowledge",
  "company-fit",
  "compliance",
  "risks",
  "conflicts",
  "ambiguities",
  "changes",
  "activity",
  "settings",
];
const SPRINT7_TABS: Sprint7Tab[] = [
  "tasks",
  "approvals",
  "clarifications",
  "workflow-designer",
  "report-designer",
  "decision-support",
];
const SPRINT9_TABS: Sprint9Tab[] = [
  "quality",
  "errors",
  "improvements",
  "release",
];

function includes<T extends string>(list: T[], value: string): value is T {
  return list.includes(value as T);
}

/** Parse location.hash into a portal route. Returns null if empty/invalid. */
export function parsePortalHash(hash: string): PortalRoute | null {
  const raw = hash.replace(/^#/, "").replace(/^\//, "").trim();
  if (!raw) return null;
  const parts = raw.split("/").filter(Boolean);
  const head = parts[0];

  if (head === "dashboard") return { screen: "dashboard" };
  if (head === "projects") return { screen: "projects" };
  if (head === "operations") return { screen: "operations" };

  if (head === "workflows") {
    const tab = parts[1];
    return {
      screen: "workflows",
      tab: tab && includes(SPRINT7_TABS, tab) ? tab : "tasks",
    };
  }

  if (head === "pilot-quality") {
    const tab = parts[1];
    return {
      screen: "pilot-quality",
      tab: tab && includes(SPRINT9_TABS, tab) ? tab : "quality",
    };
  }

  if (head === "project" && parts[1]) {
    const projectId = parts[1];
    const mode = parts[2];

    if (mode === "expert") {
      const tab = parts[3];
      return {
        screen: "project",
        projectId,
        expertMode: true,
        tab: tab && includes(PROJECT_TABS, tab) ? tab : "documents",
      };
    }

    const hub =
      mode && includes(HUBS, mode)
        ? mode
        : ("analysis" as ProjectHub);
    const detail = parts[3];

    return {
      screen: "project",
      projectId,
      expertMode: false,
      hub,
      analysisStep:
        hub === "analysis" && detail && includes(ANALYSIS_STEPS, detail)
          ? detail
          : "documents",
      findingsFilter:
        hub === "findings" && detail && includes(FILTERS, detail)
          ? detail
          : "risks",
      projectSubNav:
        hub === "project" && detail && includes(SUBNAVS, detail)
          ? detail
          : "overview",
    };
  }

  return null;
}

export function buildPortalHash(route: PortalRoute): string {
  switch (route.screen) {
    case "dashboard":
      return "#/dashboard";
    case "projects":
      return "#/projects";
    case "operations":
      return "#/operations";
    case "workflows":
      return `#/workflows/${route.tab}`;
    case "pilot-quality":
      return `#/pilot-quality/${route.tab}`;
    case "project":
      if (route.expertMode) {
        return `#/project/${route.projectId}/expert/${route.tab}`;
      }
      if (route.hub === "analysis") {
        return `#/project/${route.projectId}/analysis/${route.analysisStep}`;
      }
      if (route.hub === "findings") {
        return `#/project/${route.projectId}/findings/${route.findingsFilter}`;
      }
      if (route.hub === "changes") {
        return `#/project/${route.projectId}/changes`;
      }
      return `#/project/${route.projectId}/project/${route.projectSubNav}`;
    default:
      return "#/dashboard";
  }
}
