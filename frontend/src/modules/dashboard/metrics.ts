import type { ProjectDocument } from "@/src/modules/documents/api";
import type { TenderProject } from "@/src/modules/tenders/api";

export function dashboardMetrics(
  projects: TenderProject[],
  documents: ProjectDocument[],
) {
  return {
    activeProjects: projects.filter((project) => project.status !== "ARCHIVED").length,
    processedDocuments: documents.filter((document) => document.status === "READY").length,
    failedDocuments: documents.filter((document) => document.status === "FAILED").length,
    upcomingDeadlines: projects
      .filter((project) => project.bidDeadline)
      .sort((left, right) =>
        (left.bidDeadline ?? "").localeCompare(right.bidDeadline ?? ""),
      )
      .slice(0, 4),
  };
}
