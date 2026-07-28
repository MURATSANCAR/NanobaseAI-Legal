"use client";

import type { ReactNode } from "react";
import { Menu, RefreshCw, Search } from "lucide-react";
import { AiAmbientShow } from "@/src/components/qa/AiAmbientShow";
import { Sidebar, type SidebarNavItem } from "@/src/components/layout/Sidebar";

type AppShellProps = {
  sidebarOpen: boolean;
  onSidebarOpen: () => void;
  onSidebarClose: () => void;
  navItems: SidebarNavItem[];
  brandTitle: string;
  brandSubtitle: string;
  profileName: string;
  profileRole: string;
  profileInitials: string;
  onProfileClick: () => void;
  searchQuery: string;
  onSearchChange: (value: string) => void;
  onRefresh: () => void;
  children: ReactNode;
};

export function AppShell({
  sidebarOpen,
  onSidebarOpen,
  onSidebarClose,
  navItems,
  brandTitle,
  brandSubtitle,
  profileName,
  profileRole,
  profileInitials,
  onProfileClick,
  searchQuery,
  onSearchChange,
  onRefresh,
  children,
}: AppShellProps) {
  return (
    <div className="relative h-[100dvh] min-h-0 overflow-hidden bg-gradient-to-br from-violet-100 via-white to-sky-100 text-slate-800">
      <AiAmbientShow />

      <div className="relative z-10 flex h-full min-h-0 p-1.5 pb-[max(0.375rem,env(safe-area-inset-bottom))] sm:p-2.5 lg:p-3">
        <Sidebar
          open={sidebarOpen}
          onClose={onSidebarClose}
          navItems={navItems}
          brandTitle={brandTitle}
          brandSubtitle={brandSubtitle}
          profileName={profileName}
          profileRole={profileRole}
          profileInitials={profileInitials}
          onProfileClick={onProfileClick}
        />

        <div className="ai-module-shell relative flex min-h-0 min-w-0 flex-1 flex-col rounded-2xl border border-white/80 bg-gradient-to-br from-teal-50/30 via-white/40 to-violet-50/30 shadow-2xl backdrop-blur-xl sm:rounded-3xl">
          <button
            type="button"
            className="absolute left-3 top-3 z-30 rounded-2xl border border-white/70 bg-white/80 p-2 text-slate-600 shadow-sm backdrop-blur transition hover:bg-white lg:hidden"
            onClick={onSidebarOpen}
            aria-label="Menüyü aç"
          >
            <Menu className="h-5 w-5" />
          </button>

          <div className="absolute right-3 top-3 z-30 flex items-center gap-2">
            <div className="hidden min-w-0 items-center gap-2 rounded-2xl border border-white/70 bg-white/80 px-3 py-2 text-sm text-slate-600 shadow-sm backdrop-blur sm:flex">
              <Search className="h-4 w-4 shrink-0" />
              <input
                value={searchQuery}
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder="Proje kodu, adı veya kurum ara"
                aria-label="Projelerde ara"
                className="min-w-0 flex-1 border-0 bg-transparent outline-none placeholder:text-slate-400"
              />
            </div>
            <button
              type="button"
              className="inline-flex items-center gap-2 rounded-2xl border border-white/70 bg-white/80 px-3 py-2 text-sm font-medium text-slate-600 shadow-sm backdrop-blur transition hover:bg-white"
              onClick={onRefresh}
            >
              <RefreshCw className="h-4 w-4" />
              <span className="hidden sm:inline">Yenile</span>
            </button>
          </div>

          <main className="relative flex min-h-0 flex-1 flex-col overflow-x-hidden overflow-y-auto p-2 pt-14 sm:p-4 sm:pt-14 lg:p-5 lg:pt-14">
            <div className="mx-auto flex w-full min-h-0 min-w-0 max-w-[1460px] flex-1 flex-col">
              <div className="mb-3 flex min-w-0 items-center gap-2 rounded-2xl border border-white/70 bg-white/80 px-3 py-2 text-sm text-slate-600 shadow-sm backdrop-blur sm:hidden">
                <Search className="h-4 w-4 shrink-0" />
                <input
                  value={searchQuery}
                  onChange={(event) => onSearchChange(event.target.value)}
                  placeholder="Proje ara"
                  aria-label="Projelerde ara"
                  className="min-w-0 flex-1 border-0 bg-transparent outline-none placeholder:text-slate-400"
                />
              </div>
              {children}
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
