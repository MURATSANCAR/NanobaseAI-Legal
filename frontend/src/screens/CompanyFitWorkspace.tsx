"use client";

import { Building2, ClipboardCheck, LoaderCircle, RefreshCw, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { MetricCard as QaMetricCard } from "@/src/components/qa/MetricCard";
import { ScrollTable } from "@/src/components/qa/ScrollTable";
import { StatusBadge } from "@/src/components/qa/StatusBadge";
import {
  companyFitApi,
  type CompanyCapability,
  type CompanyFitReport,
} from "@/src/modules/company-fit/api";
import {
  documentApi,
  documentTypeLabels,
  type ProjectDocument,
} from "@/src/modules/documents/api";
import { isApiError } from "@/src/shared/api";

const EVIDENCE_TYPES = new Set([
  "CERTIFICATE",
  "PRODUCT_CATALOG",
  "OTHER",
  "FINANCIAL_FORM",
]);

const TENDER_TYPES = new Set([
  "TECHNICAL_SPECIFICATION",
  "ADMINISTRATIVE_SPECIFICATION",
  "DRAFT_CONTRACT",
  "ADDENDUM",
  "AMENDMENT",
  "ANNEX",
]);

function overallTone(overall?: string) {
  switch (overall) {
    case "FIT":
      return "ok";
    case "CONDITIONAL":
      return "warn";
    case "NOT_FIT":
      return "fail";
    default:
      return "run";
  }
}

function rowTone(status?: string) {
  switch (status) {
    case "MET":
      return "ok";
    case "PARTIAL":
      return "warn";
    case "MISSING":
      return "fail";
    default:
      return "run";
  }
}

async function collectDocumentText(token: string, documentId: string): Promise<string> {
  const chunks: string[] = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages && page < 40) {
    const batch = await documentApi.pages(token, documentId, page, 50);
    totalPages = Math.max(batch.totalPages || 1, 1);
    for (const item of batch.content) {
      const text = (item.normalizedText || item.rawText || "").trim();
      if (text) chunks.push(text);
    }
    page += 1;
  }
  if (chunks.join("\n").trim().length >= 80) {
    return chunks.join("\n\n");
  }
  const clauses = await documentApi.clauses(token, documentId);
  return clauses.content
    .map((c) => (c.normalizedText || c.rawText || "").trim())
    .filter(Boolean)
    .join("\n\n");
}

export function CompanyFitWorkspace({
  documents,
  token,
  orgId,
  canWrite,
  onProblem,
  onNotify,
}: {
  documents: ProjectDocument[];
  token: string;
  orgId: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
}) {
  const evidenceDocs = useMemo(
    () =>
      documents.filter(
        (d) => d.status === "READY" && EVIDENCE_TYPES.has(d.documentType),
      ),
    [documents],
  );
  const tenderDocs = useMemo(
    () =>
      documents.filter(
        (d) =>
          d.status === "READY" &&
          d.includedInAnalysis &&
          TENDER_TYPES.has(d.documentType),
      ),
    [documents],
  );

  const [capabilities, setCapabilities] = useState<CompanyCapability[]>([]);
  const [selectedEvidence, setSelectedEvidence] = useState<string[]>([]);
  const [tenderDocumentId, setTenderDocumentId] = useState(
    tenderDocs[0]?.id ?? "",
  );
  const [report, setReport] = useState<CompanyFitReport | null>(null);
  const [busy, setBusy] = useState<"idle" | "load" | "ingest" | "fit">("idle");

  const loadCapabilities = useCallback(async () => {
    if (!orgId) return;
    setBusy("load");
    try {
      const next = await companyFitApi.listCapabilities(token, orgId);
      setCapabilities(next);
    } catch (error) {
      onProblem(error);
    } finally {
      setBusy("idle");
    }
  }, [orgId, onProblem, token]);

  useEffect(() => {
    void loadCapabilities();
  }, [loadCapabilities]);

  useEffect(() => {
    if (!tenderDocumentId && tenderDocs[0]?.id) {
      setTenderDocumentId(tenderDocs[0].id);
    }
  }, [tenderDocumentId, tenderDocs]);

  function toggleEvidence(id: string) {
    setSelectedEvidence((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  }

  async function ingestSelected() {
    if (!orgId) {
      onNotify("Organizasyon kimliği bulunamadı");
      return;
    }
    const targets = evidenceDocs.filter((d) => selectedEvidence.includes(d.id));
    if (targets.length === 0) {
      onNotify("Önce şirket evrakı seçin");
      return;
    }
    setBusy("ingest");
    try {
      const payload = [];
      for (const doc of targets) {
        const text = await collectDocumentText(token, doc.id);
        if (!text.trim()) {
          onNotify(`${doc.logicalName}: çıkarılabilir metin yok`);
          continue;
        }
        payload.push({
          documentId: doc.id,
          docType: doc.documentType,
          title: doc.logicalName,
          text,
        });
      }
      if (payload.length === 0) {
        onNotify("İnge edilecek metin bulunamadı");
        return;
      }
      const result = await companyFitApi.ingest(token, orgId, payload);
      setCapabilities(result.capabilities);
      onNotify(`${result.capabilityCount} yetenek çıkarıldı`);
      await loadCapabilities();
    } catch (error) {
      onProblem(error);
    } finally {
      setBusy("idle");
    }
  }

  async function runFit() {
    if (!orgId) {
      onNotify("Organizasyon kimliği bulunamadı");
      return;
    }
    if (!tenderDocumentId) {
      onNotify("Şartname dokümanı seçin");
      return;
    }
    setBusy("fit");
    try {
      const next = await companyFitApi.evaluate(token, tenderDocumentId, {
        organizationId: orgId,
      });
      setReport(next);
      onNotify(`Uygunluk: ${next.overall} (${next.mustMet}/${next.mustTotal})`);
    } catch (error) {
      if (isApiError(error) && error.problem.status === 403) {
        onNotify("Bu işlem için yetkiniz yok");
      }
      onProblem(error);
    } finally {
      setBusy("idle");
    }
  }

  return (
    <section className="panel space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="eyebrow">Firma uygunluk</p>
          <h2 className="text-xl font-semibold tracking-tight">
            Şirket evrakı × şartname fit
          </h2>
          <p className="mt-1 max-w-2xl text-sm text-[var(--nb-muted)]">
            Sertifika / katalog evraklarından yetenek çıkarın; şartname
            gereksinimleriyle otomatik uygunluk matrisi üretin.
          </p>
        </div>
        <button
          className="btn-secondary"
          disabled={busy !== "idle"}
          onClick={() => void loadCapabilities()}
        >
          {busy === "load" ? <LoaderCircle className="animate-spin" /> : <RefreshCw />}
          Yenile
        </button>
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        <QaMetricCard
          label="Şirket evrakı"
          value={String(evidenceDocs.length)}
          hint="READY sertifika / katalog"
          icon={<Building2 className="h-4 w-4" />}
        />
        <QaMetricCard
          label="Yetenek"
          value={String(capabilities.length)}
          hint="Çıkarılmış capability"
          icon={<ShieldCheck className="h-4 w-4" />}
        />
        <QaMetricCard
          label="Sonuç"
          value={report?.overall ?? "—"}
          hint={
            report
              ? `${report.mustMet}/${report.mustTotal} MUST · skor ${report.overallScore}`
              : "Henüz hesaplanmadı"
          }
          icon={<ClipboardCheck className="h-4 w-4" />}
        />
      </div>

      <div className="card-static space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-medium">1. Şirket evrakları</h3>
          <button
            className="btn-primary"
            disabled={!canWrite || busy !== "idle" || selectedEvidence.length === 0}
            onClick={() => void ingestSelected()}
          >
            {busy === "ingest" ? (
              <LoaderCircle className="animate-spin" />
            ) : (
              <ShieldCheck />
            )}
            Yetenek çıkar
          </button>
        </div>
        {evidenceDocs.length === 0 ? (
          <p className="text-sm text-[var(--nb-muted)]">
            READY durumda CERTIFICATE / PRODUCT_CATALOG / OTHER evrak yok. Önce
            Dokümanlar adımından yükleyin.
          </p>
        ) : (
          <ul className="space-y-2">
            {evidenceDocs.map((doc) => (
              <li key={doc.id} className="document-row">
                <label className="flex cursor-pointer items-start gap-3">
                  <input
                    type="checkbox"
                    checked={selectedEvidence.includes(doc.id)}
                    onChange={() => toggleEvidence(doc.id)}
                  />
                  <span>
                    <strong>{doc.logicalName}</strong>
                    <span className="ml-2 processing-badge">
                      {documentTypeLabels[doc.documentType] ?? doc.documentType}
                    </span>
                  </span>
                </label>
              </li>
            ))}
          </ul>
        )}
        {capabilities.length > 0 && (
          <div className="evidence-chips">
            {capabilities.map((cap) => (
              <span key={cap.capabilityId} className="processing-badge" title={cap.evidenceSnippet ?? ""}>
                {cap.label}
                <em className="ml-1 opacity-70">{cap.validityStatus}</em>
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="card-static space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="space-y-2">
            <h3 className="font-medium">2. Şartname uygunluk</h3>
            <label className="block text-sm">
              Şartname dokümanı
              <select
                className="mt-1 w-full min-w-[280px]"
                value={tenderDocumentId}
                onChange={(e) => setTenderDocumentId(e.target.value)}
              >
                <option value="">Seçin</option>
                {tenderDocs.map((doc) => (
                  <option key={doc.id} value={doc.id}>
                    {doc.logicalName} ({documentTypeLabels[doc.documentType] ?? doc.documentType})
                  </option>
                ))}
              </select>
            </label>
          </div>
          <button
            className="btn-primary"
            disabled={!canWrite || busy !== "idle" || !tenderDocumentId}
            onClick={() => void runFit()}
          >
            {busy === "fit" ? <LoaderCircle className="animate-spin" /> : <ClipboardCheck />}
            Uygunluk hesapla
          </button>
        </div>

        {report && (
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-3">
              <StatusBadge label={report.overall} status={overallTone(report.overall)} />
              <span className="text-sm text-[var(--nb-muted)]">
                MUST {report.mustMet}/{report.mustTotal} · skor {report.overallScore}
                {report.policyVersion ? ` · ${report.policyVersion}` : ""}
              </span>
            </div>
            <ScrollTable>
              <table className="compliance-matrix">
                <thead>
                  <tr>
                    <th>Durum</th>
                    <th>Kategori</th>
                    <th>Öncelik</th>
                    <th>Gereksinim</th>
                    <th>Skor</th>
                    <th>Öneri</th>
                  </tr>
                </thead>
                <tbody>
                  {report.rows.map((row) => (
                    <tr key={row.requirementId}>
                      <td>
                        <StatusBadge label={row.status} status={rowTone(row.status)} />
                      </td>
                      <td>{row.category || "—"}</td>
                      <td>{row.priority || "—"}</td>
                      <td>
                        <div className="max-w-xl text-sm">{row.textPreview}</div>
                        {row.evidence?.length > 0 && (
                          <div className="mt-1 text-xs text-[var(--nb-muted)]">
                            {row.evidence
                              .map((e) => e.snippet)
                              .filter(Boolean)
                              .slice(0, 2)
                              .join(" · ")}
                          </div>
                        )}
                      </td>
                      <td>{row.score.toFixed(2)}</td>
                      <td className="text-sm">{row.suggestedAction || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </ScrollTable>
          </div>
        )}
      </div>
    </section>
  );
}
