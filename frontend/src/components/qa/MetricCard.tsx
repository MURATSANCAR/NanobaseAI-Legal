import type { LucideIcon } from "lucide-react";
import { cn } from "@/src/lib/cn";

type MetricCardProps = {
  label: string;
  value: number | string;
  icon?: LucideIcon;
  detail?: string;
  tone?: string;
  delay?: number;
  onClick?: () => void;
};

export function MetricCard({
  label,
  value,
  icon: Icon,
  detail,
  tone = "text-violet-600",
  delay = 0,
  onClick,
}: MetricCardProps) {
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
        {Icon ? (
          <div className={cn("rounded-xl bg-violet-50 p-2", tone)}>
            <Icon className="h-4 w-4" />
          </div>
        ) : null}
      </div>
      <div className="metric-value">{value}</div>
      {detail ? <p className="mt-2 text-xs text-slate-500">{detail}</p> : null}
    </div>
  );

  return card;
}
