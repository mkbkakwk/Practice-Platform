import type { Verdict } from "./api";
import { cn } from "./utils";

export const VERDICT_LABEL: Record<Verdict, string> = {
  PENDING: "评测中",
  AC: "通过",
  WA: "答案错误",
  TLE: "超时",
  RE: "运行错误",
  CE: "编译错误",
  SE: "系统错误",
};

export const VERDICT_CLASS: Record<Verdict, string> = {
  PENDING: "bg-blue-100 text-blue-700 border-blue-200",
  AC: "bg-green-100 text-green-700 border-green-200",
  WA: "bg-red-100 text-red-700 border-red-200",
  TLE: "bg-orange-100 text-orange-700 border-orange-200",
  RE: "bg-red-100 text-red-700 border-red-200",
  CE: "bg-zinc-200 text-zinc-700 border-zinc-300",
  SE: "bg-zinc-200 text-zinc-700 border-zinc-300",
};

export function VerdictBadge({ verdict, className }: { verdict: Verdict; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium",
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
