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
  PENDING: "border border-info/25 bg-info/10 text-info",
  JUDGING: "border border-info/25 bg-info/10 text-info",
  COMPLETED: "border border-success/25 bg-success/10 text-success",
  FAILED: "border border-danger/25 bg-danger/10 text-danger",
  AUTO_CHECKED: "border border-success/25 bg-success/10 text-success",
  NEEDS_REVIEW: "border border-warning/25 bg-warning/10 text-warning",
  REVIEWED: "border border-info/25 bg-info/10 text-info",
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
          {pending && <Loader2 className="h-4 w-4 animate-spin text-info" />}
          <span className="font-semibold">DOCX Submission #{submission.id}</span>
        </div>
        <span className={cn("rounded px-2 py-1 text-xs font-semibold", STATUS_CLASS[submission.status])}>
          {STATUS_LABEL[submission.status]}
        </span>
      </div>
      {pending && <p className="mt-2 text-sm text-subtle">{submission.status === "PENDING" ? "正在排队..." : "正在判题..."}</p>}
      {submission.score != null && (
        <div className="mt-3 text-2xl font-bold text-info">
          {detail?.earnedScore ?? submission.score} <span className="text-sm font-normal text-muted-foreground">/ {detail?.totalScore ?? 100}</span>
        </div>
      )}
      {detail && (
        <div className="mt-3">
          <p className="text-sm text-subtle">
            {detail.totalErrorCount} 项差异 · Judge {detail.judgeVersion || submission.judgeVersion}
          </p>
          {detail.truncated && (
            <p className="mt-1 text-xs text-warning">错误项较多，仅显示服务端返回的部分结果。总错误数：{detail.totalErrorCount}</p>
          )}
          {visibleItems.length > 0 && (
            <ul className="mt-3 space-y-2">
              {visibleItems.map((item, index) => (
                <li key={`${item.ruleId}-${item.target}-${index}`} className="rounded border bg-surface p-3 text-sm">
                  <div className="font-medium text-foreground">{item.target} · {item.message}</div>
                  <div className="mt-1 grid gap-1 text-xs text-subtle sm:grid-cols-2">
                    <span>Expected: {item.expected || "—"}</span>
                    <span>Actual: {item.actual || "—"}</span>
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">得分 {item.earned} / {item.score}</div>
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
        <p role="alert" className="mt-3 rounded border border-danger/25 bg-danger/10 p-3 text-sm text-danger">
          文档未能完成判题，请检查文件后重新提交。
        </p>
      )}
    </Card>
  );
}
