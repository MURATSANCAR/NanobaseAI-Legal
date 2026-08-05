"use client";

import {
  ArrowLeft,
  FolderKanban,
  LoaderCircle,
  SlidersHorizontal,
} from "lucide-react";
import {
  EXPERT_TABS,
  PROJECT_HUBS,
  isStepReachable,
  type StepCompletion,
} from "@/src/lib/analysis-pipeline";
import type {
  AnalysisStep,
  FindingsFilter,
  ProjectHub,
  ProjectSubNav,
  ProjectTab,
} from "@/src/lib/portal-utils";
import { PageShell } from "@/src/components/qa/PageShell";
import { StatusBadge } from "@/src/components/qa/StatusBadge";
import { AnalysisStepper } from "@/src/components/pipeline/AnalysisStepper";
import { PipelineNav } from "@/src/components/pipeline/PipelineNav";
import { type AuditEvent } from "@/src/modules/audit/api";
import { type ProjectDocument } from "@/src/modules/documents/api";
import {
  type ProjectMember,
  type TenderProject,
} from "@/src/modules/tenders/api";

import { Overview } from "./Overview";
import { DocumentCenter } from "./DocumentCenter";
import { RequirementsMatrix } from "./RequirementsMatrix";
import { KnowledgeCenter } from "./KnowledgeCenter";
import { ComplianceWorkspace } from "./ComplianceWorkspace";
import { RiskCenter } from "./RiskCenter";
import { ConflictWorkspace } from "./ConflictWorkspace";
import { AmbiguityWorkspace } from "./AmbiguityWorkspace";
import { ChangeImpactWorkspace } from "./ChangeImpactWorkspace";
import { ActivityHistory } from "./ActivityHistory";
import { ProjectSettings } from "./ProjectSettings";
import { FindingsWorkspace } from "./FindingsWorkspace";
import { TenderIntelligenceDashboard } from "./TenderIntelligenceDashboard";

const PROJECT_SUBNAVS: Array<{ id: ProjectSubNav; label: string }> = [
  { id: "overview", label: "Genel bakış" },
  { id: "activity", label: "Aktivite" },
  { id: "settings", label: "Ayarlar" },
];

export function ProjectDetail({
  project,
  hub,
  onHub,
  analysisStep,
  onAnalysisStep,
  projectSubNav,
  onProjectSubNav,
  findingsFilter,
  onFindingsFilter,
  expertMode,
  onExpertMode,
  tab,
  onTab,
  completion,
  documents,
  members,
  auditEvents,
  token,
  canWrite,
  canAnalyze,
  loading,
  onBack,
  onDocuments,
  onMembers,
  onProblem,
  onNotify,
  onArchive,
  busy,
}: {
  project: TenderProject;
  hub: ProjectHub;
  onHub: (hub: ProjectHub) => void;
  analysisStep: AnalysisStep;
  onAnalysisStep: (step: AnalysisStep) => void;
  projectSubNav: ProjectSubNav;
  onProjectSubNav: (nav: ProjectSubNav) => void;
  findingsFilter: FindingsFilter;
  onFindingsFilter: (filter: FindingsFilter) => void;
  expertMode: boolean;
  onExpertMode: (value: boolean) => void;
  tab: ProjectTab;
  onTab: (tab: ProjectTab) => void;
  completion: StepCompletion;
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
  function selectStep(step: AnalysisStep) {
    if (!isStepReachable(step, completion) && !completion[step]) {
      onNotify("Önce önceki adımı tamamlayın");
      return;
    }
    onAnalysisStep(step);
  }

  return (
    <PageShell
      title={project.name}
      subtitle={`${project.projectCode} · ${project.institutionName} · ${project.tenderRegistrationNumber || "Kayıt numarası yok"}`}
      icon={<FolderKanban className="h-4 w-4" />}
      maxWidth="max-w-[1460px]"
      heroTrailing={
        <StatusBadge label={project.status.replaceAll("_", " ")} status={project.status} />
      }
      header={
        <div className="space-y-3">
          <button className="back btn-secondary" onClick={onBack}>
            <ArrowLeft />
            Projelere dön
          </button>
          <div className="module-hero-strip module-hero-strip--legal animate-fade-in">
            <div className="module-hero-strip-main">
              <div className="module-hero-strip-icon bg-gradient-to-br from-teal-500 via-emerald-600 to-violet-600">
                <FolderKanban className="h-4 w-4" />
              </div>
              <div className="module-hero-strip-text min-w-0 flex-1">
                <h1 className="module-hero-strip-title">{project.name}</h1>
                <p className="module-hero-strip-subtitle">
                  {project.projectCode} · {project.institutionName} ·{" "}
                  {project.tenderRegistrationNumber || "Kayıt numarası yok"}
                </p>
              </div>
              <div className="module-hero-strip-trailing">
                <StatusBadge
                  label={project.status.replaceAll("_", " ")}
                  status={project.status}
                />
              </div>
            </div>
          </div>
          <div className="project-hub-bar">
            {!expertMode ? (
              <nav className="project-hubs" aria-label="Proje alanları">
                {PROJECT_HUBS.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={hub === item.id ? "active" : ""}
                    onClick={() => onHub(item.id)}
                  >
                    {item.label}
                  </button>
                ))}
              </nav>
            ) : (
              <nav className="tabs project-tabs" aria-label="Proje detay sekmeleri">
                {EXPERT_TABS.map(([id, label]) => (
                  <button
                    key={id}
                    type="button"
                    className={tab === id ? "active" : ""}
                    onClick={() => onTab(id)}
                  >
                    {label}
                  </button>
                ))}
              </nav>
            )}
            <button
              type="button"
              className="expert-toggle"
              aria-pressed={expertMode}
              onClick={() => onExpertMode(!expertMode)}
            >
              <SlidersHorizontal className="h-3.5 w-3.5" />
              {expertMode ? "Rehberli görünüm" : "Uzman görünümü"}
            </button>
          </div>
        </div>
      }
    >
      {loading ? (
        <div className="processing">
          <LoaderCircle className="spin" />
          Yükleniyor…
        </div>
      ) : expertMode ? (
        <>
          {tab === "overview" && (
            <div className="space-y-6">
              <TenderIntelligenceDashboard token={token} project={project} />
              <Overview project={project} documents={documents} members={members} />
            </div>
          )}
          {tab === "documents" && (
            <DocumentCenter
              project={project}
              documents={documents}
              token={token}
              canWrite={canWrite}
              onDocuments={onDocuments}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "requirements" && (
            <RequirementsMatrix
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "knowledge" && (
            <KnowledgeCenter
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "compliance" && (
            <ComplianceWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "risks" && (
            <RiskCenter
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "conflicts" && (
            <ConflictWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "ambiguities" && (
            <AmbiguityWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {tab === "changes" && (
            <ChangeImpactWorkspace
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
              onDocuments={onDocuments}
            />
          )}
          {tab === "activity" && <ActivityHistory events={auditEvents} />}
          {tab === "settings" && (
            <ProjectSettings
              project={project}
              members={members}
              token={token}
              canWrite={canWrite}
              onMembers={onMembers}
              onProblem={onProblem}
              onNotify={onNotify}
              onArchive={onArchive}
              busy={busy}
            />
          )}
        </>
      ) : (
        <>
          {hub === "analysis" && (
            <section className="analysis-pipeline">
              <AnalysisStepper
                current={analysisStep}
                completion={completion}
                onSelect={selectStep}
              />
              {analysisStep === "documents" && (
                <DocumentCenter
                  project={project}
                  documents={documents}
                  token={token}
                  canWrite={canWrite}
                  onDocuments={onDocuments}
                  onProblem={onProblem}
                  onNotify={onNotify}
                />
              )}
              {analysisStep === "requirements" && (
                <RequirementsMatrix
                  project={project}
                  documents={documents}
                  token={token}
                  canWrite={canAnalyze}
                  onProblem={onProblem}
                  onNotify={onNotify}
                />
              )}
              {analysisStep === "knowledge" && (
                <KnowledgeCenter
                  project={project}
                  documents={documents}
                  token={token}
                  canWrite={canAnalyze}
                  onProblem={onProblem}
                  onNotify={onNotify}
                />
              )}
              {analysisStep === "compliance" && (
                <ComplianceWorkspace
                  project={project}
                  token={token}
                  canWrite={canAnalyze}
                  onProblem={onProblem}
                  onNotify={onNotify}
                />
              )}
              {analysisStep === "risks" && (
                <RiskCenter
                  project={project}
                  token={token}
                  canWrite={canAnalyze}
                  onProblem={onProblem}
                  onNotify={onNotify}
                />
              )}
              <PipelineNav
                step={analysisStep}
                completion={completion}
                onStep={selectStep}
                onGoFindings={() => onHub("findings")}
              />
            </section>
          )}
          {hub === "findings" && (
            <FindingsWorkspace
              project={project}
              token={token}
              canWrite={canAnalyze}
              filter={findingsFilter}
              onFilter={onFindingsFilter}
              onProblem={onProblem}
              onNotify={onNotify}
            />
          )}
          {hub === "changes" && (
            <ChangeImpactWorkspace
              project={project}
              documents={documents}
              token={token}
              canWrite={canAnalyze}
              onProblem={onProblem}
              onNotify={onNotify}
              onDocuments={onDocuments}
            />
          )}
          {hub === "project" && (
            <section>
              <nav className="project-subnav" aria-label="Proje bilgileri">
                {PROJECT_SUBNAVS.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={projectSubNav === item.id ? "active" : ""}
                    onClick={() => onProjectSubNav(item.id)}
                  >
                    {item.label}
                  </button>
                ))}
              </nav>
              {projectSubNav === "overview" && (
                <div className="space-y-6">
                  <TenderIntelligenceDashboard token={token} project={project} />
                  <Overview project={project} documents={documents} members={members} />
                </div>
              )}
              {projectSubNav === "activity" && (
                <ActivityHistory events={auditEvents} />
              )}
              {projectSubNav === "settings" && (
                <ProjectSettings
                  project={project}
                  members={members}
                  token={token}
                  canWrite={canWrite}
                  onMembers={onMembers}
                  onProblem={onProblem}
                  onNotify={onNotify}
                  onArchive={onArchive}
                  busy={busy}
                />
              )}
            </section>
          )}
        </>
      )}
    </PageShell>
  );
}
