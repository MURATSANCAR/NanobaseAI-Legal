"use client";

import type { LucideIcon } from "lucide-react";
import { cn } from "@/src/lib/cn";

export type WorkspaceRailItem = {
  id: string;
  label: string;
  icon: LucideIcon;
  group: string;
};

export function WorkspaceRail({
  items,
  activeId,
  onSelect,
  ariaLabel,
}: {
  items: WorkspaceRailItem[];
  activeId: string;
  onSelect: (id: string) => void;
  ariaLabel: string;
}) {
  const groups = Array.from(new Set(items.map((item) => item.group)));

  return (
    <nav className="workspace-rail" aria-label={ariaLabel}>
      {groups.map((group) => (
        <div key={group} className="workspace-rail-group">
          <p className="workspace-rail-group-label">{group}</p>
          {items
            .filter((item) => item.group === group)
            .map((item) => {
              const Icon = item.icon;
              const active = item.id === activeId;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={cn("workspace-rail-item", active && "active")}
                  aria-current={active ? "page" : undefined}
                  onClick={() => onSelect(item.id)}
                >
                  <Icon />
                  <span>{item.label}</span>
                </button>
              );
            })}
        </div>
      ))}
    </nav>
  );
}
