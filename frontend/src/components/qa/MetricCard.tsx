import type { LucideIcon } from "lucide-react";
import { isValidElement, type ReactNode } from "react";
import { cn } from "@/src/lib/cn";

type MetricCardProps = {
  label: string;
  value: number | string;
  /** Lucide component or a pre-built icon element. */
  icon?: LucideIcon | ReactNode;
  detail?: string;
  /** Alias used by some workspaces; treated as detail. */
  hint?: string;
  tone?: string;
  delay?: number;
  onClick?: () => void;
};

function renderIcon(icon: LucideIcon | ReactNode) {
  if (isValidElement(icon)) return icon;
  if (typeof icon === "function") {
    const Icon = icon as LucideIcon;
    return <Icon className="h-4 w-4" />;
  }
  return null;
}

export function MetricCard({
  label,
  value,
  icon,
  detail,
  hint,
  tone = "text-violet-600",
  delay = 0,
  onClick,
}: MetricCardProps) {
  const caption = detail ?? hint;
  const iconNode = icon ? renderIcon(icon) : null;
  const card = (
    <div
      className={cn(
        "card p-4 animate-fade-in sm:p-5",
        onClick && "cursor-pointer transition hover:border-violet-300 hover:shadow-md",
      )}
      style={{ animationDelay: `${delay}ms` }}
      onClick={onClick}
      onKeyDown={
        onClick
          ? (event) => {
              if (event.key === "Enter" || event.key === " ") onClick();
            }
          : undefined
      }
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <div className="mb-3 flex items-center justify-between gap-2">
        <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
          {label}
        </span>
        {iconNode ? (
          <div className={cn("rounded-xl bg-violet-50 p-2", tone)}>{iconNode}</div>
        ) : null}
      </div>
      <div className="metric-value">{value}</div>
      {caption ? <p className="mt-2 text-xs text-slate-500">{caption}</p> : null}
    </div>
  );

  return card;
}
