import type { Verdict } from "./api";
import { cn } from "./utils";

export const VERDICT_LABEL: Record<Verdict, string> = {
  PENDING: "评测中",
  JUDGING: "评测中",
  AC: "通过",
  WA: "答案错误",
  TLE: "超时",
  MLE: "内存超限",
  OLE: "输出超限",
  RE: "运行错误",
  CE: "编译错误",
  SE: "系统错误",
  JUDGE_FAILED: "评测失败",
};

// Semantic status colors work in both the existing light UI and graphite pilot.
export const VERDICT_CLASS: Record<Verdict, string> = {
  PENDING: "bg-info/10 text-info border-info/25",
  JUDGING: "bg-info/10 text-info border-info/25",
  AC: "bg-success/10 text-success border-success/25",
  WA: "bg-danger/10 text-danger border-danger/25",
  TLE: "bg-warning/10 text-warning border-warning/25",
  MLE: "bg-warning/10 text-warning border-warning/25",
  OLE: "bg-warning/10 text-warning border-warning/25",
  RE: "bg-rose/10 text-rose border-rose/25",
  CE: "bg-violet/10 text-violet border-violet/25",
  SE: "bg-elevated text-subtle border-border",
  JUDGE_FAILED: "bg-elevated text-subtle border-border",
};

export function VerdictBadge({ verdict, className }: { verdict: Verdict; className?: string }) {
  return (
    <span
      title={verdict}
      className={cn(
        "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
        VERDICT_CLASS[verdict],
        className,
      )}
    >
      {VERDICT_LABEL[verdict]}
    </span>
  );
}

export const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: "简单",
  MEDIUM: "中等",
  HARD: "困难",
};

export const DIFFICULTY_CLASS: Record<string, string> = {
  EASY: "bg-green-100 text-green-700 border-green-200",
  MEDIUM: "bg-yellow-100 text-yellow-700 border-yellow-200",
  HARD: "bg-red-100 text-red-700 border-red-200",
};
