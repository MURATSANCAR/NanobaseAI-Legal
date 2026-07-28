import { cn } from "@/src/lib/cn";

type Tone = "ok" | "warn" | "fail" | "run" | "draft" | "ended" | "neutral";

const toneMap: Record<Tone, string> = {
  ok: "bg-emerald-50 text-emerald-700 border-emerald-200",
  warn: "bg-amber-50 text-amber-700 border-amber-200",
  fail: "bg-red-50 text-red-700 border-red-200",
  run: "bg-violet-50 text-violet-700 border-violet-200",
  draft: "bg-sky-50 text-sky-800 border-sky-200",
  ended: "bg-slate-100 text-slate-700 border-slate-300",
  neutral: "bg-slate-50 text-slate-600 border-slate-200",
};

export function statusTone(status?: string): Tone {
  const s = (status || "").toLowerCase().trim();
  if (
    [
      "ok",
      "passed",
      "complete",
      "completed",
      "approved",
      "ready",
      "healthy",
      "delivered",
      "active",
      "provider_accepted",
    ].includes(s)
  ) {
    return "ok";
  }
  if (["running", "started", "in_progress", "generating", "processing"].includes(s)) {
    return "run";
  }
  if (["failed", "error", "rejected", "blocked", "cancelled"].includes(s)) {
    return "fail";
  }
  if (["draft", "uploaded", "queued"].includes(s)) {
    return "draft";
  }
  if (["ended", "archived", "dead"].includes(s)) {
    return "ended";
  }
  if (["interrupted", "at_risk", "degraded", "manual_review_required"].includes(s)) {
    return "warn";
  }
  if (["pending", "pending_approval", "open", "scheduled"].includes(s)) {
    return "warn";
  }
  return "neutral";
}

export function StatusBadge({ label, status }: { label: string; status?: string }) {
  const tone = statusTone(status);
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold",
        toneMap[tone],
      )}
    >
      {label}
    </span>
  );
}
