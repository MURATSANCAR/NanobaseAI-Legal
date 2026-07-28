export function AiAmbientShow() {
  return (
    <div className="pointer-events-none fixed inset-0 overflow-hidden" aria-hidden>
      <div className="absolute -left-20 -top-20 h-72 w-72 rounded-full bg-violet-400/30 blur-3xl animate-aurora" />
      <div className="absolute -right-16 top-1/4 h-64 w-64 rounded-full bg-sky-400/25 blur-3xl animate-aurora-reverse" />
      <div className="absolute bottom-0 left-1/3 h-56 w-56 rounded-full bg-fuchsia-400/20 blur-3xl animate-float-slow" />
      <div className="absolute right-[20%] top-[8%] h-40 w-40 rounded-full bg-teal-400/15 blur-3xl animate-float-delayed" />
    </div>
  );
}
