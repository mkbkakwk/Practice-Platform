import type { ReactNode } from "react";

/** Management tables keep every column, with keyboard-accessible local scrolling. */
export function AdminTable({ label, children }: { label: string; children: ReactNode }) {
  return <div role="region" aria-label={label} tabIndex={0} className="admin-table-scroll">
    <p className="px-4 py-2 text-xs text-muted-foreground lg:hidden">横向滚动查看完整列与操作</p>
    {children}
  </div>;
}
