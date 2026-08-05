"use client";

import {
  Activity,
  CheckCircle2,
  FileClock,
  LoaderCircle,
  Upload,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import {
  documentApi,
  type DocumentVersion,
  type ProjectDocument,
} from "@/src/modules/documents/api";
import {
  riskApi,
  type ChangeItem,
  type ChangeSet,
  type ImpactAnalysis,
} from "@/src/modules/risks/api";
import { type TenderProject } from "@/src/modules/tenders/api";

import { Empty } from "./_shared";

export function ChangeImpactWorkspace({
  documents,
  token,
  canWrite,
  onProblem,
  onNotify,
  onDocuments,
}: {
  project: TenderProject;
  documents: ProjectDocument[];
  token: string;
  canWrite: boolean;
  onProblem: (error: unknown) => void;
  onNotify: (message: string) => void;
  onDocuments?: (documents: ProjectDocument[]) => void;
}) {
  const [documentId, setDocumentId] = useState("");
  const [versions, setVersions] = useState<DocumentVersion[]>([]);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [baseVersionId, setBaseVersionId] = useState("");
  const [targetVersionId, setTargetVersionId] = useState("");
  const [changeSet, setChangeSet] = useState<ChangeSet>();
  const [impact, setImpact] = useState<ImpactAnalysis>();
  const [selectedItem, setSelectedItem] = useState<ChangeItem>();
  const [baseClauseId, setBaseClauseId] = useState("");
  const [targetClauseId, setTargetClauseId] = useState("");
  const versionInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!documentId) {
      const timer = window.setTimeout(() => {
        setVersions([]);
        setBaseVersionId("");
        setTargetVersionId("");
        setChangeSet(undefined);
        setImpact(undefined);
      }, 0);
      return () => window.clearTimeout(timer);
    }
    let cancelled = false;
    setLoadingVersions(true);
    documentApi
      .versions(token, documentId)
      .then((items) => {
        if (cancelled) return;
        setVersions(items);
        setTargetVersionId(items[0]?.id ?? "");
        setBaseVersionId(items[1]?.id ?? "");
        setChangeSet(undefined);
        setImpact(undefined);
      })
      .catch(onProblem)
      .finally(() => {
        if (!cancelled) setLoadingVersions(false);
      });
    return () => {
      cancelled = true;
    };
  }, [documentId, onProblem, token]);

  const canAnalyze =
    !!documentId &&
    !!baseVersionId &&
    !!targetVersionId &&
    baseVersionId !== targetVersionId &&
    !loadingVersions &&
    !busy;

  const blockReason = (() => {
    if (!documents.length) {
      return "Bu projede henüz doküman yok. Önce Dokümanlar sekmesinden yükleyin.";
    }
    if (!documentId) return "Değişiklik analizi için önce bir doküman seçin.";
    if (loadingVersions) return "Versiyonlar yükleniyor…";
    if (versions.length < 2) {
      return "Aynı dokümanda en az iki versiyon gerekir. Mevcut şartnamede yalnızca v1 var — yeni sürümü buradan veya Dokümanlar sekmesinden yükleyin.";
    }
    if (!baseVersionId || !targetVersionId) {
      return "Eski ve yeni versiyonu seçin.";
    }
    if (baseVersionId === targetVersionId) {
      return "Eski ve yeni versiyon farklı olmalıdır.";
    }
    return null;
  })();

  async function refreshVersions(nextDocumentId = documentId) {
    if (!nextDocumentId) return;
    const items = await documentApi.versions(token, nextDocumentId);
    setVersions(items);
    setTargetVersionId(items[0]?.id ?? "");
    setBaseVersionId(items[1]?.id ?? "");
  }

  async function uploadNewVersion(file?: File) {
    if (!file?.size || !documentId || !canWrite) return;
    setUploading(true);
    try {
      const updated = await documentApi.uploadVersion(token, documentId, file);
      onDocuments?.(
        documents.map((item) => (item.id === updated.id ? updated : item)),
      );
      await refreshVersions(documentId);
      setChangeSet(undefined);
      setImpact(undefined);
      onNotify(`v${updated.currentVersionNumber} yüklendi — şimdi iki versiyonu karşılaştırabilirsiniz`);
    } catch (error) {
      onProblem(error);
    } finally {
      setUploading(false);
      if (versionInputRef.current) versionInputRef.current.value = "";
    }
  }

  async function create() {
    if (!canAnalyze) return;
    setBusy(true);
    try {
      const created = await riskApi.createChangeSet(
        token,
        documentId,
        baseVersionId,
        targetVersionId,
      );
      setChangeSet(created);
      setImpact(undefined);
      onNotify(`${created.items.length} yapısal değişiklik eşleştirildi`);
    } catch (error) {
      onProblem(error);
    } finally {
      setBusy(false);
    }
  }

  async function analyze() {
    if (!changeSet) return;
    setBusy(true);
    try {
      const result = await riskApi.impact(token, changeSet.id);
      setImpact(result);
      onNotify(`${result.affectedEntities.length} seçici etki kaydedildi`);
    } catch (error) {
      onProblem(error);
    } finally {
      setBusy(false);
    }
  }

  async function correct() {
    if (!changeSet || !selectedItem) return;
    setBusy(true);
    try {
      const updated = await riskApi.correctChange(
        token,
        changeSet.id,
        selectedItem,
        baseClauseId,
        targetClauseId,
      );
      setChangeSet(updated);
      setSelectedItem(undefined);
      onNotify("Değişiklik eşleşmesi uzman kararıyla güncellendi");
    } catch (error) {
      onProblem(error);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="workspace-stack">
      <article className="panel card-static change-controls">
        <div className="panel-head">
          <div>
            <b>Doküman değişikliği ve etki</b>
            <span>Yapısal diff → requirement graph → seçici re-analysis</span>
          </div>
        </div>
        <div className="change-form">
          <label>
            Doküman
            <select
              value={documentId}
              onChange={(event) => setDocumentId(event.target.value)}
              disabled={!documents.length}
            >
              <option value="">Doküman seçin</option>
              {documents.map((document) => (
                <option key={document.id} value={document.id}>
                  {document.logicalName}
                  {document.currentVersionNumber
                    ? ` (v${document.currentVersionNumber})`
                    : ""}
                </option>
              ))}
            </select>
          </label>
          <label>
            Eski versiyon
            <select
              value={baseVersionId}
              onChange={(event) => setBaseVersionId(event.target.value)}
              disabled={!documentId || loadingVersions || versions.length === 0}
            >
              <option value="">Versiyon seçin</option>
              {versions.map((version) => (
                <option key={version.id} value={version.id}>
                  v{version.versionNumber}
                </option>
              ))}
            </select>
          </label>
          <label>
            Yeni versiyon
            <select
              value={targetVersionId}
              onChange={(event) => setTargetVersionId(event.target.value)}
              disabled={!documentId || loadingVersions || versions.length === 0}
            >
              <option value="">Versiyon seçin</option>
              {versions.map((version) => (
                <option key={version.id} value={version.id}>
                  v{version.versionNumber}
                </option>
              ))}
            </select>
          </label>
          {canWrite && (
            <button
              className="primary"
              onClick={() => void create()}
              disabled={!canAnalyze}
              title={blockReason ?? "Yapısal değişiklik analizi başlat"}
            >
              {busy ? <LoaderCircle className="spin" /> : <FileClock />}
              Değişikliği analiz et
            </button>
          )}
        </div>
        {blockReason && (
          <p className="change-hint" role="status">
            {blockReason}
          </p>
        )}
        {canWrite && documentId && versions.length < 2 && !loadingVersions && (
          <div className="change-version-upload">
            <button
              type="button"
              className="secondary"
              disabled={uploading}
              onClick={() => versionInputRef.current?.click()}
            >
              {uploading ? <LoaderCircle className="spin" /> : <Upload />}
              Yeni versiyon yükle
            </button>
            <input
              ref={versionInputRef}
              type="file"
              accept=".pdf,.docx"
              className="sr-only"
              onChange={(event) =>
                void uploadNewVersion(event.target.files?.[0])
              }
            />
          </div>
        )}
      </article>
      {!documents.length && (
        <Empty text="Bu projeye henüz doküman yüklenmedi." />
      )}
      {changeSet && (
        <div className="change-grid">
          <article className="panel card-static change-items">
            <div className="panel-head">
              <div>
                <b>Change set</b>
                <span>
                  {changeSet.items.length} madde · {changeSet.status}
                </span>
              </div>
              {canWrite && (
                <button
                  className="primary"
                  onClick={() => void analyze()}
                  disabled={busy}
                >
                  {busy ? <LoaderCircle className="spin" /> : <Activity />}
                  Etki analizi
                </button>
              )}
            </div>
            {changeSet.items.map((item) => (
              <button
                key={item.id}
                className={
                  selectedItem?.id === item.id
                    ? "change-item active"
                    : "change-item"
                }
                onClick={() => {
                  if (!canWrite) return;
                  setSelectedItem(item);
                  setBaseClauseId(item.baseClauseId ?? "");
                  setTargetClauseId(item.targetClauseId ?? "");
                }}
              >
                <span
                  className={`change-badge ${item.changeType.toLowerCase()}`}
                >
                  {item.changeType}
                </span>
                <b>
                  {item.baseClauseId || "∅"} → {item.targetClauseId || "∅"}
                </b>
                <small>
                  %{Math.round(item.similarityScore * 100)} benzerlik ·{" "}
                  {item.reviewStatus}
                </small>
              </button>
            ))}
            {!changeSet.items.length && (
              <Empty text="İki versiyon arasında yapısal fark bulunamadı." />
            )}
          </article>
          <article className="panel card-static impact-results">
            <div className="panel-head">
              <div>
                <b>Etkilenen sonuçlar</b>
                <span>Eski sonuçlar silinmez; stale işaretlenir</span>
              </div>
            </div>
            {impact?.affectedEntities.map((entity) => (
              <div className="impact-row" key={entity.id}>
                <span>{entity.entityType}</span>
                <b>{entity.impactConcept}</b>
                <small>%{Math.round(entity.confidence * 100)} güven</small>
              </div>
            ))}
            {!impact && <Empty text="Etki analizi henüz çalıştırılmadı." />}
          </article>
        </div>
      )}
      {selectedItem && canWrite && (
        <div className="modal-backdrop">
          <section className="modal change-correction">
            <div className="modal-head">
              <div>
                <p className="eyebrow">UZMAN EŞLEŞME DÜZELTMESİ</p>
                <h2>{selectedItem.changeType}</h2>
              </div>
              <button
                onClick={() => setSelectedItem(undefined)}
                aria-label="Kapat"
              >
                <X />
              </button>
            </div>
            <label>
              Eski clause ID
              <input
                value={baseClauseId}
                onChange={(event) => setBaseClauseId(event.target.value)}
              />
            </label>
            <label>
              Yeni clause ID
              <input
                value={targetClauseId}
                onChange={(event) => setTargetClauseId(event.target.value)}
              />
            </label>
            <p>Boş taraf silinen veya eklenen maddeyi temsil eder.</p>
            <button
              className="primary"
              onClick={() => void correct()}
              disabled={busy}
            >
              <CheckCircle2 />
              Eşleşmeyi kaydet
            </button>
          </section>
        </div>
      )}
    </section>
  );
}
