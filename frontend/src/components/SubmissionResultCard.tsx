import { Clock, Loader2, MemoryStick } from "lucide-react";
import type { Submission } from "@/lib/api";
import { VerdictBadge } from "@/lib/verdict";
import { Card } from "@/components/ui/card";

export function SubmissionResultCard({
  submission,
  pendingMessage,
}: {
  submission: Submission;
  pendingMessage?: string;
}) {
  const pending = submission.verdict === "PENDING" || submission.verdict === "JUDGING";
  return (
    <Card className="mt-4 p-4" aria-live="polite">
      <div className="flex flex-wrap items-center gap-2">
        {pending && <Loader2 className="h-4 w-4 animate-spin text-blue-600" />}
        <span className="font-semibold">Submission #{submission.id}</span>
        <VerdictBadge verdict={submission.verdict} />
      </div>
      {pending ? (
        <p className="mt-2 text-sm text-zinc-600">
          {pendingMessage ?? (submission.verdict === "PENDING" ? "正在排队..." : "正在判题...")}
        </p>
      ) : (
        <div className="mt-3 flex flex-wrap gap-4 text-sm text-zinc-600">
          <span>测试点：<strong className="text-zinc-900">{submission.passed} / {submission.total}</strong></span>
          <span className="inline-flex items-center gap-1"><Clock className="h-3.5 w-3.5" />{submission.timeMs} ms</span>
          <span className="inline-flex items-center gap-1"><MemoryStick className="h-3.5 w-3.5" />{formatMemory(submission.memoryKb)}</span>
        </div>
      )}
      {submission.message && !pending && (
        <pre className="mt-3 max-h-40 overflow-auto whitespace-pre-wrap rounded bg-zinc-50 p-3 text-xs text-zinc-700">{submission.message}</pre>
      )}
    </Card>
  );
}

function formatMemory(memoryKb: number) {
  if (!Number.isFinite(memoryKb) || memoryKb <= 0) return "—";
  return `${(memoryKb / 1024).toFixed(1)} MB`;
}
