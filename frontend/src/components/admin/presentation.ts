/** Presentation only: phase eligibility is still defined by the existing API. */
export function adminPhaseClass(phase: string): string {
  const styles: Record<string, string> = {
    DRAFT: "border-border bg-elevated text-muted-foreground",
    UPCOMING: "border-info/25 bg-info/10 text-info",
    RUNNING: "border-success/25 bg-success/10 text-success",
    ENDED: "border-border bg-elevated text-secondary-foreground",
    CANCELLED: "border-danger/25 bg-danger/10 text-danger",
  };
  return `border ${styles[phase] ?? styles.DRAFT}`;
}

// Same semantic treatment as the accepted document result UI, without motion.
export const ADMIN_DOC_STATUS_CLASS: Record<string, string> = {
  PENDING: "border-info/25 bg-info/10 text-info",
  JUDGING: "border-info/25 bg-info/10 text-info",
  COMPLETED: "border-success/25 bg-success/10 text-success",
  FAILED: "border-danger/25 bg-danger/10 text-danger",
  AUTO_CHECKED: "border-success/25 bg-success/10 text-success",
  NEEDS_REVIEW: "border-warning/25 bg-warning/10 text-warning",
  REVIEWED: "border-info/25 bg-info/10 text-info",
};
