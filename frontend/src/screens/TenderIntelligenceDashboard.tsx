"use client";

import { useEffect, useState } from "react";
import { MetricCard as QaMetricCard } from "@/src/components/qa/MetricCard";
import { StatusBadge } from "@/src/components/qa/StatusBadge";
import {
  tenderApi,
  type TenderAssessmentSummary,
  type TenderProject,
} from "@/src/modules/tenders/api";

type Props = {
  token: string;
  project: TenderProject;
};

function daysRemaining(deadline?: string): string {
  if (!deadline) return "—";
  const end = new Date(deadline);
  const now = new Date();
  const diff = Math.ceil((end.getTime() - now.getTime()) / 86_400_000);
  if (Number.isNaN(diff)) return "—";
  if (diff < 0) return `${Math.abs(diff)} gün geçti`;
  return `${diff} gün`;
}

export function TenderIntelligenceDashboard({ token, project }: Props) {
  const [summary, setSummary] = useState<TenderAssessmentSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    tenderApi
      .summary(token, project.id)
      .then((value) => {
        if (!cancelled) setSummary(value);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [token, project.id]);

  const decision =
    summary?.recommended_bid_decision ??
    (summary?.status === "NOT_GENERATED" ? "Özet yok" : "—");

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-semibold tracking-tight">{project.name}</h2>
          <p className="text-sm opacity-70">
            {project.institutionName} · Teklif: {project.bidDeadline || "—"} ·
            Kalan: {daysRemaining(project.bidDeadline)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge label={project.status.replaceAll("_", " ")} status={project.status} />
          <StatusBadge
            label={summary?.overall_compliance_status || "REVIEW_REQUIRED"}
            status={summary?.overall_compliance_status || "REVIEW_REQUIRED"}
          />
          <StatusBadge
            label={`Risk: ${summary?.overall_risk_level || "MEDIUM"}`}
            status={summary?.overall_risk_level || "MEDIUM"}
          />
          <StatusBadge label={String(decision)} status={String(decision)} />
        </div>
      </div>

      {error ? (
        <p className="text-sm opacity-70">
          Özet henüz üretilemedi veya özellik kapalı: {error}
        </p>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <QaMetricCard
          label="Mandatory"
          value={String(summary?.mandatory_requirements ?? "—")}
        />
        <QaMetricCard
          label="Hard blocker"
          value={String(summary?.hard_blocker_count ?? "—")}
        />
        <QaMetricCard
          label="Giderilebilir eksik"
          value={String(summary?.remediable_gap_count ?? "—")}
        />
        <QaMetricCard
          label="Kritik risk"
          value={String(summary?.critical_risk_count ?? "—")}
        />
        <QaMetricCard
          label="Belirsizlik / soru"
          value={String(summary?.clarification_count ?? "—")}
        />
        <QaMetricCard
          label="Uygun mandatory"
          value={String(summary?.compliant_mandatory ?? "—")}
        />
        <QaMetricCard
          label="Uygun olmayan mandatory"
          value={String(summary?.non_compliant_mandatory ?? "—")}
        />
        <QaMetricCard
          label="Bilinmeyen mandatory"
          value={String(summary?.unknown_mandatory ?? "—")}
        />
      </div>
    </section>
  );
}
