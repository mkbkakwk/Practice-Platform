import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Check, LockKeyhole, Table2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { ContestBackLink, ContestError, ContestLoading, ContestPhaseBadge } from "@/components/contest/ContestVisuals";
import { api, getApiErrorMessage, type ContestStanding } from "@/lib/api";
import { Card } from "@/components/ui/card";

export default function ContestStandings() {
  const { id } = useParams<{ id: string }>();
  const contestId = Number(id);
  const { user } = useAuth();
  const [standing, setStanding] = useState<ContestStanding | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api.getContestStandings(contestId).then((response) => {
      if (active) { setStanding(response.standings); setError(""); }
    }).catch((reason) => active && setError(getApiErrorMessage(reason, "排名加载失败")))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [contestId]);

  if (loading) return <ContestLoading label="加载比赛排名" />;
  if (!standing) return <main className="pilot-page space-y-5"><ContestBackLink to={`/contests/${contestId}`} /><ContestError message={error || "排名不可用"} /></main>;

  return <main className="pilot-page">
    <ContestBackLink to={`/contests/${contestId}`} />
    <header className="my-5 flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
      <div className="min-w-0">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <ContestPhaseBadge phase={standing.phase} />
          <Badge variant="neutral">{standing.scoringMode}</Badge>
          <span className="pilot-numeric ml-1 text-xs text-muted-foreground">Contest #{standing.contestId}</span>
        </div>
        <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">比赛排名</h1>
        <p className="mt-3 text-sm text-subtle">{standing.scoringMode === "ICPC" ? "ICPC：解题数优先，罚时次之" : "得分：每题取有效提交中的最高分"}</p>
      </div>
      <div className="text-xs text-muted-foreground sm:text-right">
        <p className="mb-1.5">榜单生成时间</p>
        <time className="pilot-numeric text-subtle" dateTime={standing.generatedAt}>{new Date(standing.generatedAt).toLocaleString()}</time>
      </div>
    </header>
    {standing.frozen && <p className="pilot-notice mb-4 border-warning/25 bg-warning/10 text-warning"><LockKeyhole aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" /><span>比赛已封榜；当前排名仅展示封榜前提交。</span></p>}
    {standing.managerView && standing.phase === "RUNNING" && standing.freezeAt && <p className="pilot-notice mb-4 border-info/25 bg-info/10 text-info">管理员实时榜单：学生视图会在封榜后隐藏封榜后的提交。</p>}
    <Card className="gap-0 overflow-hidden p-0">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-4 py-3 sm:px-5">
        <h2 className="flex items-center gap-2 text-sm font-medium"><Table2 aria-hidden="true" className="h-4 w-4 text-muted-foreground" />排名明细 <span className="pilot-numeric text-xs text-muted-foreground">{standing.entries.length} 人</span></h2>
        <span id="standings-scroll-hint" className="text-xs text-muted-foreground">更多题目列可横向滚动查看</span>
      </div>
      <div className="overflow-x-auto" role="region" aria-label="比赛排名表格" aria-describedby="standings-scroll-hint" tabIndex={0}>
        <table className="w-full min-w-[640px] text-left text-sm">
          <caption className="sr-only">比赛排名；数值与顺序来自服务端{standing.frozen ? "封榜快照" : "榜单"}。</caption>
          <thead className="whitespace-nowrap bg-surface text-xs text-muted-foreground"><tr>
            <th scope="col" className="w-20 px-4 py-3 font-medium">排名</th>
            <th scope="col" className="min-w-40 px-4 py-3 font-medium">用户</th>
            {standing.scoringMode === "ICPC" ? <><th scope="col" className="px-4 py-3 text-right font-medium">解题</th><th scope="col" className="px-4 py-3 text-right font-medium">罚时</th></> : <th scope="col" className="px-4 py-3 text-right font-medium">总分</th>}
            {standing.entries[0]?.problems.map((problem) => <th scope="col" key={problem.contestProblemId} className="min-w-32 px-4 py-3 text-center font-medium">{problem.label}</th>)}
          </tr></thead>
          <tbody>{standing.entries.map((entry) => {
            const isCurrentUser = user?.id === entry.userId;
            return <tr key={entry.userId} className={cn("pilot-row", isCurrentUser && "pilot-row-current")} data-current-user={isCurrentUser || undefined}>
              <td className="px-4 py-4"><span className={cn("pilot-numeric inline-flex h-7 min-w-7 items-center justify-center rounded-md border border-transparent px-1 text-xs font-semibold",
                entry.rank === 1 ? "border-rank-gold/25 bg-rank-gold/10 text-rank-gold" : entry.rank === 2 ? "border-rank-silver/25 bg-rank-silver/10 text-rank-silver" : entry.rank === 3 ? "border-rank-bronze/25 bg-rank-bronze/10 text-rank-bronze" : "text-muted-foreground")}>{entry.rank}</span></td>
              <th scope="row" className="px-4 py-4 font-medium"><div className="flex items-center gap-2"><span className="max-w-48 truncate" title={entry.username}>{entry.username}</span>{isCurrentUser && <Badge variant="outline" className="border-brand/25 bg-brand/5 text-brand">我</Badge>}</div></th>
              {standing.scoringMode === "ICPC" ? <><td className="pilot-numeric px-4 py-4 text-right font-semibold">{entry.solved}</td><td className="pilot-numeric px-4 py-4 text-right text-subtle">{entry.penaltyMinutes}</td></> : <td className="pilot-numeric px-4 py-4 text-right font-semibold">{entry.totalScore}</td>}
              {entry.problems.map((problem) => <td key={problem.contestProblemId} className="px-4 py-3 text-center">
                {standing.scoringMode === "ICPC" ? <span className={cn("pilot-numeric inline-flex min-h-8 items-center justify-center gap-1.5 whitespace-nowrap rounded-md border px-2.5 text-xs",
                  problem.solved ? "border-success/25 bg-success/10 text-success" : problem.attempts ? "border-warning/25 bg-warning/10 text-warning" : "border-transparent text-muted-foreground")}>
                  {problem.solved && <Check aria-hidden="true" className="h-3 w-3" />}
                  {problem.solved ? `AC · ${problem.penaltyMinutes}分` : problem.attempts ? `${problem.attempts} 次` : "—"}
                </span> : <span className="pilot-numeric text-subtle">{problem.score ?? 0}</span>}
              </td>)}
            </tr>;
          })}</tbody>
        </table>
      </div>
      {standing.entries.length === 0 && <div role="status" className="border-t px-6 py-12 text-center"><Table2 aria-hidden="true" className="mx-auto mb-3 h-5 w-5 text-muted-foreground" /><p className="text-sm text-subtle">暂无参赛者。</p></div>}
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t px-4 py-3 text-xs text-muted-foreground sm:px-5">
        {standing.scoringMode === "ICPC" && <><span className="inline-flex items-center gap-1.5"><Check aria-hidden="true" className="h-3 w-3 text-success" />AC 已通过</span><span>次数：尚未通过</span><span>— 未尝试</span></>}
        <span className="sm:ml-auto">排名与计分由服务端计算</span>
      </div>
    </Card>
  </main>;
}
