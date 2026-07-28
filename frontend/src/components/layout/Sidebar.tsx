"use client";

import type { LucideIcon } from "lucide-react";
import { LogOut, Sparkles, X } from "lucide-react";
import { AiOrb } from "@/src/components/qa/AiOrb";
import { cn } from "@/src/lib/cn";

export type SidebarNavItem = {
  id: string;
  label: string;
  icon: LucideIcon;
  active?: boolean;
  badge?: string | number;
  group: string;
  onClick: () => void;
};

type SidebarProps = {
  open: boolean;
  onClose: () => void;
  navItems: SidebarNavItem[];
  brandTitle: string;
  brandSubtitle: string;
  profileName: string;
  profileRole: string;
  profileInitials: string;
  onProfileClick: () => void;
};

export function Sidebar({
  open,
  onClose,
  navItems,
  brandTitle,
  brandSubtitle,
  profileName,
  profileRole,
  profileInitials,
  onProfileClick,
}: SidebarProps) {
  const groups = Array.from(new Set(navItems.map((item) => item.group)));

  return (
    <>
      <div
        className={cn(
          "fixed inset-0 z-40 bg-slate-900/20 backdrop-blur-sm transition-opacity lg:hidden",
          open ? "opacity-100" : "pointer-events-none opacity-0",
        )}
        onClick={onClose}
        aria-hidden={!open}
      />

      <aside
        className={cn(
          "fixed bottom-[max(0.5rem,env(safe-area-inset-bottom))] left-[max(0.5rem,env(safe-area-inset-left))] top-[max(0.5rem,env(safe-area-inset-top))] z-50 flex w-[min(100vw-1.25rem,19.5rem)] flex-col rounded-3xl border border-white/70 bg-white/55 shadow-xl backdrop-blur-2xl transition-transform duration-300 lg:static lg:inset-auto lg:z-auto lg:mr-3 lg:w-72 lg:shrink-0 lg:translate-x-0 xl:mr-5",
          open ? "translate-x-0" : "-translate-x-[calc(100%+1.5rem)] lg:translate-x-0",
        )}
        aria-label="Ana menü"
      >
        <div className="border-b border-white/60 px-4 py-4">
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 flex-1 items-center gap-3">
              <AiOrb size="lg" />
              <div className="min-w-0">
                <h1 className="flex items-center gap-1.5 truncate text-base font-bold leading-tight text-violet-900">
                  {brandTitle}
                  <Sparkles className="h-4 w-4 text-violet-500" />
                </h1>
                <p className="truncate text-xs text-slate-500">{brandSubtitle}</p>
              </div>
            </div>
            <button
              type="button"
              className="rounded-xl p-2 text-slate-500 transition hover:bg-white/60 lg:hidden"
              onClick={onClose}
              aria-label="Menüyü kapat"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        <nav className="flex-1 space-y-4 overflow-y-auto px-3 py-3">
          {groups.map((group) => (
            <div key={group} className="space-y-1.5">
              <p className="px-2 text-[11px] font-bold uppercase tracking-wider text-violet-700/90">
                {group}
              </p>
              <div className="space-y-1">
                {navItems
                  .filter((item) => item.group === group)
                  .map((item) => {
                    const Icon = item.icon;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => {
                          item.onClick();
                          onClose();
                        }}
                        className={cn(
                          "relative flex w-full items-center gap-3 rounded-2xl px-3.5 py-3 text-left text-[15px] font-semibold leading-tight transition-all duration-200",
                          item.active
                            ? "bg-violet-600 text-white shadow-lg shadow-violet-300/40"
                            : "text-slate-700 hover:bg-white/80 hover:text-violet-900",
                        )}
                      >
                        <Icon
                          className={cn(
                            "h-5 w-5 shrink-0",
                            item.active ? "text-white" : "text-violet-600",
                          )}
                          aria-hidden
                        />
                        <span className="min-w-0 flex-1 truncate">{item.label}</span>
                        {item.badge != null && item.badge !== "" ? (
                          <b
                            className={cn(
                              "rounded-full px-2 py-0.5 text-[11px] font-bold",
                              item.active
                                ? "bg-white/20 text-white"
                                : "bg-violet-100 text-violet-700",
                            )}
                          >
                            {item.badge}
                          </b>
                        ) : null}
                      </button>
                    );
                  })}
              </div>
            </div>
          ))}
        </nav>

        <div className="space-y-2 border-t border-white/60 p-3">
          <button
            type="button"
            className="flex w-full items-center gap-3 rounded-2xl bg-white/50 px-3 py-2.5 text-left transition hover:bg-white/80"
            onClick={onProfileClick}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-teal-500 text-xs font-bold text-white">
              {profileInitials}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-slate-800">{profileName}</p>
              <p className="truncate text-xs text-slate-500">{profileRole}</p>
            </div>
            <LogOut className="h-4 w-4 shrink-0 text-slate-400" aria-hidden />
          </button>
        </div>
      </aside>
    </>
  );
}
