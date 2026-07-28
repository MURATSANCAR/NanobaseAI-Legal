"use client";

import type { ReactNode } from "react";
import { LoaderCircle } from "lucide-react";
import { EmptyState } from "@/src/components/qa/EmptyState";

type AsyncStateProps = {
  loading?: boolean;
  error?: string | null;
  empty?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  onRetry?: () => void;
  children?: ReactNode;
};

export function AsyncState({
  loading,
  error,
  empty,
  emptyTitle = "Veri yok",
  emptyDescription = "Gösterilecek kayıt bulunamadı.",
  onRetry,
  children,
}: AsyncStateProps) {
  if (loading) {
    return (
      <div className="empty-state-card" aria-live="polite">
        <LoaderCircle className="spin mb-3 h-6 w-6 text-violet-600" />
        <p className="empty-state-desc">Veriler yükleniyor…</p>
      </div>
    );
  }

  if (error) {
    return (
      <EmptyState
        title="Bir sorun oluştu"
        description={error}
        ctaLabel={onRetry ? "Tekrar dene" : undefined}
        onCtaClick={onRetry}
      />
    );
  }

  if (empty) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return <>{children}</>;
}
