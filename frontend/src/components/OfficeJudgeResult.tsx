import { useState } from "react";
import { ChevronDown, ChevronUp, Loader2 } from "lucide-react";
import type { StudentDocSubmission } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const STATUS_LABEL: Record<StudentDocSubmission["status"], string> = {
  PENDING: "等待判题",
  JUDGING: "判题中",
  COMPLETED: "判题完成",
  FAILED: "判题失败",
  AUTO_CHECKED: "自动检查通过",
  NEEDS_REVIEW: "待老师复核",
  REVIEWED: "已复核",
};

const STATUS_CLASS: Record<StudentDocSubmission["status"], string> = {
  PENDING: "bg-zinc-100 text-zinc-700",
  JUDGING: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
  AUTO_CHECKED: "bg-green-100 text-green-700",
  NEEDS_REVIEW: "bg-yellow-100 text-yellow-800",
  REVIEWED: "bg-blue-100 text-blue-700",
};

export function OfficeJudgeResult({ submission }: { submission: StudentDocSubmission }) {
  const [expanded, setExpanded] = useState(false);
  const pending = submission.status === "PENDING" || submission.status === "JUDGING";
  const detail = submission.resultDetail;
  const items = detail?.items ?? [];
  const visibleItems = expanded ? items : items.slice(0, 5);

  return (
    <Card className="mt-4 p-4" aria-live="polite">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {pending && <Loader2 className="h-4 w-4 animate-spin text-blue-600" />}
          <span className="font-semibold">DOCX Submission #{submission.id}</span>
        </div>
        <span className={cn("rounded px-2 py-1 text-xs font-semibold", STATUS_CLASS[submission.status])}>
          {STATUS_LABEL[submission.status]}
        </span>
      </div>
      {pending && <p className="mt-2 text-sm text-zinc-600">{submission.status === "PENDING" ? "正在排队..." : "正在判题..."}</p>}
      {submission.score != null && (
        <div className="mt-3 text-2xl font-bold text-blue-700">
          {detail?.earnedScore ?? submission.score} <span className="text-sm font-normal text-zinc-500">/ {detail?.totalScore ?? 100}</span>
        </div>
      )}
      {detail && (
        <div className="mt-3">
          <p className="text-sm text-zinc-600">
            {detail.totalErrorCount} 项差异 · Judge {detail.judgeVersion || submission.judgeVersion}
          </p>
          {detail.truncated && (
            <p className="mt-1 text-xs text-amber-700">错误项较多，仅显示服务端返回的部分结果。总错误数：{detail.totalErrorCount}</p>
          )}
          {visibleItems.length > 0 && (
            <ul className="mt-3 space-y-2">
              {visibleItems.map((item, index) => (
                <li key={`${item.ruleId}-${item.target}-${index}`} className="rounded border bg-zinc-50 p-3 text-sm">
                  <div className="font-medium text-zinc-900">{item.target} · {item.message}</div>
                  <div className="mt-1 grid gap-1 text-xs text-zinc-600 sm:grid-cols-2">
                    <span>Expected: {item.expected || "—"}</span>
                    <span>Actual: {item.actual || "—"}</span>
                  </div>
                  <div className="mt-1 text-xs text-zinc-500">得分 {item.earned} / {item.score}</div>
                </li>
              ))}
            </ul>
          )}
          {items.length > 5 && (
            <Button type="button" variant="ghost" size="sm" className="mt-2" onClick={() => setExpanded((value) => !value)}>
              {expanded ? <ChevronUp className="mr-1 h-4 w-4" /> : <ChevronDown className="mr-1 h-4 w-4" />}
              {expanded ? "收起" : `展开全部（${items.length}）`}
            </Button>
          )}
        </div>
      )}
      {submission.status === "FAILED" && (
        <p role="alert" className="mt-3 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          文档未能完成判题，请检查文件后重新提交。
        </p>
      )}
    </Card>
  );
}
