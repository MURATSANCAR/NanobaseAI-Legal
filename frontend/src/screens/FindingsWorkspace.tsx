"use client";

import { AlertTriangle, GitCompareArrows, ShieldAlert } from "lucide-react";
import type { FindingsFilter } from "@/src/lib/portal-utils";
import type { TenderProject } from "@/src/modules/tenders/api";
import { AmbiguityWorkspace } from "./AmbiguityWorkspace";
import { ConflictWorkspace } from "./ConflictWorkspace";
import { RiskCenter } from "./RiskCenter";

const FILTERS: Array<{
  id: FindingsFilter;
  label: string;
  icon: typeof ShieldAlert;
}> = [
  { id: "risks", label: "Riskler", icon: ShieldAlert },
  { id: "conflicts", label: "Çelişkiler", icon: GitCompareArrows },
  { id: "ambiguities", label: "Belirsizlikler", icon: AlertTriangle },
];

export function FindingsWorkspace({
  project,
  token,
  canWrite,
  filter,
  onFilter,
  onProblem,
  onNotify,
}: {
  project: TenderProject;
  token: string;
  canWrite: boolean;
  filter: FindingsFilter;
  onFilter: (filter: FindingsFilter) => void;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  return (
    <section className="findings-workspace">
      <div className="findings-filters" role="tablist" aria-label="Bulgu türü">
        {FILTERS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={filter === id}
            className={filter === id ? "active" : ""}
            onClick={() => onFilter(id)}
          >
            <Icon />
            {label}
          </button>
        ))}
      </div>
      {filter === "risks" && (
        <RiskCenter
          project={project}
          token={token}
          canWrite={canWrite}
          onProblem={onProblem}
          onNotify={onNotify}
        />
      )}
      {filter === "conflicts" && (
        <ConflictWorkspace
          project={project}
          token={token}
          canWrite={canWrite}
          onProblem={onProblem}
          onNotify={onNotify}
        />
      )}
      {filter === "ambiguities" && (
        <AmbiguityWorkspace
          project={project}
          token={token}
          canWrite={canWrite}
          onProblem={onProblem}
          onNotify={onNotify}
        />
      )}
    </section>
  );
}
