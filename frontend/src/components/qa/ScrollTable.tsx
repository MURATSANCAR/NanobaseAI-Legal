import type { ReactNode } from "react";

type ScrollTableProps = {
  children: ReactNode;
  className?: string;
  bleed?: boolean;
};

export function ScrollTable({ children, className = "", bleed = true }: ScrollTableProps) {
  return (
    <div
      className={[
        bleed ? "-mx-1 max-sm:px-1 sm:mx-0" : "",
        "mobile-table-scroll overflow-x-auto",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {children}
    </div>
  );
}
