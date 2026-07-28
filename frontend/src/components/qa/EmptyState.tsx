import type { ReactNode } from "react";
import { ArrowRight, type LucideIcon } from "lucide-react";
import { cn } from "@/src/lib/cn";

type EmptyStateProps = {
  emoji?: string;
  icon?: LucideIcon;
  title: string;
  description?: string;
  body?: string;
  ctaLabel?: string;
  onCtaClick?: () => void;
  children?: ReactNode;
  className?: string;
};

export function EmptyState({
  emoji,
  icon: Icon,
  title,
  description,
  body,
  ctaLabel,
  onCtaClick,
  children,
  className,
}: EmptyStateProps) {
  const desc = description ?? body ?? "";
  return (
    <div className={cn("empty-state-card", className)}>
      {Icon ? (
        <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-100 text-violet-600">
          <Icon className="h-6 w-6" />
        </div>
      ) : (
        <div className="empty-state-emoji" aria-hidden>
          {emoji ?? "📭"}
        </div>
      )}
      <h3 className="empty-state-title">{title}</h3>
      {desc ? <p className="empty-state-desc">{desc}</p> : null}
      {children}
      {ctaLabel && onCtaClick ? (
        <button
          type="button"
          className="btn-primary mt-4 inline-flex items-center gap-2"
          onClick={onCtaClick}
        >
          {ctaLabel}
          <ArrowRight className="h-4 w-4" />
        </button>
      ) : null}
    </div>
  );
}
