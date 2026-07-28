import type { ReactNode } from "react";
import { ModuleAiHero } from "./ModuleAiHero";

type PageShellProps = {
  title: string;
  subtitle: string;
  maxWidth?: string;
  children?: ReactNode;
  header?: ReactNode;
  heroTrailing?: ReactNode;
  heroSize?: "dashboard" | "page";
  icon?: ReactNode;
};

export function PageShell({
  title,
  subtitle,
  maxWidth = "max-w-6xl",
  header,
  heroTrailing,
  heroSize = "page",
  icon,
  children,
}: PageShellProps) {
  const defaultHeader = (
    <ModuleAiHero
      size={heroSize}
      title={title}
      subtitle={subtitle}
      trailing={heroTrailing}
      icon={icon}
    />
  );

  return (
    <div
      className={[
        "mobile-page ai-page-shell mx-auto flex w-full min-w-0 flex-1 flex-col animate-fade-in gap-2 sm:gap-3 min-h-0",
        maxWidth,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <div className="shrink-0 space-y-2">{header ?? defaultHeader}</div>
      <div className="ai-page-content relative z-10 flex min-h-0 min-w-0 flex-1 flex-col gap-2 sm:gap-3 stagger-children">
        {children}
      </div>
    </div>
  );
}
