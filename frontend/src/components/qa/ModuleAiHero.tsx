import { cn } from "@/src/lib/cn";
import { FileText } from "lucide-react";
import type { ReactNode } from "react";

type ModuleAiHeroProps = {
  size?: "dashboard" | "page";
  title: string;
  subtitle: string;
  trailing?: ReactNode;
  icon?: ReactNode;
};

export function ModuleAiHero({
  size = "page",
  title,
  subtitle,
  trailing,
  icon,
}: ModuleAiHeroProps) {
  const isDashboard = size === "dashboard";

  return (
    <header
      className={cn(
        "module-hero-strip animate-fade-in module-hero-strip--legal",
        isDashboard && "module-hero-strip--dashboard",
      )}
    >
      <div className="module-hero-strip-main">
        <div
          className="module-hero-strip-icon bg-gradient-to-br from-teal-500 via-emerald-600 to-violet-600"
          aria-hidden
        >
          {icon ?? <FileText className="h-4 w-4" />}
        </div>
        <div className="module-hero-strip-text min-w-0 flex-1">
          <h1 className="module-hero-strip-title">{title}</h1>
          <p className="module-hero-strip-subtitle">{subtitle}</p>
        </div>
        <div className="module-hero-strip-trailing">
          {trailing}
          {isDashboard ? (
            <span className="module-hero-strip-live" title="Canlı">
              <span className="module-hero-live-dot" aria-hidden />
              <span className="hidden sm:inline">Canlı</span>
            </span>
          ) : null}
        </div>
      </div>
    </header>
  );
}
