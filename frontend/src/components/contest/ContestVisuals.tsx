import { AlertCircle, ArrowLeft, Clock3 } from "lucide-react";
import { Link } from "react-router-dom";
import type { ContestPhase } from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { PHASE_LABEL } from "@/pages/ContestList";

export function ContestPhaseBadge({ phase }: { phase: ContestPhase }) {
  const variant = phase === "RUNNING" ? "success" : phase === "UPCOMING" ? "info" : phase === "CANCELLED" ? "danger" : "neutral";
  return <Badge variant={variant}>
    {phase === "RUNNING" && <span aria-hidden="true" className="pilot-running-dot" />}
    {PHASE_LABEL[phase]}
  </Badge>;
}

export function ContestBackLink({ to = "/contests" }: { to?: string }) {
  return <Link className="pilot-link" to={to}><ArrowLeft aria-hidden="true" className="h-3.5 w-3.5" />返回比赛</Link>;
}

export function ContestLoading({ label }: { label: string }) {
  return <main className="pilot-page" role="status" aria-label={label} aria-busy="true">
    <span className="sr-only">{label}</span>
    <div aria-hidden="true" className="space-y-5">
      <Skeleton className="h-4 w-24" />
      <Card className="gap-4 p-6"><Skeleton className="h-6 w-24" /><Skeleton className="h-8 w-3/4" /><Skeleton className="h-4 w-1/2" /></Card>
      <Card className="gap-4 p-6">{[0, 1, 2, 3].map((row) => <Skeleton key={row} className="h-12 w-full" />)}</Card>
    </div>
  </main>;
}

export function ContestError({ message }: { message: string }) {
  return <p role="alert" className="pilot-notice border-danger/25 bg-danger/10 text-danger">
    <AlertCircle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" /><span className="min-w-0 break-words">{message}</span>
  </p>;
}

export function ContestClock({ countdown }: { countdown: string }) {
  return <div className="min-w-0 border-l-2 border-border py-1 pl-4 sm:shrink-0">
    <p className="mb-1.5 flex items-center gap-2 text-xs text-subtle"><Clock3 aria-hidden="true" className="h-3.5 w-3.5" />比赛时间</p>
    <p className="pilot-numeric text-xl font-medium tracking-tight sm:text-2xl">{countdown}</p>
    <p className="mt-1.5 text-xs text-muted-foreground">状态与提交权限以服务端为准</p>
  </div>;
}
