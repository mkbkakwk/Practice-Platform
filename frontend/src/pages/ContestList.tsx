import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarDays, Loader2, Plus } from "lucide-react";
import { api, type ContestPhase, type ContestSummary } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

const PHASE_LABEL: Record<ContestPhase, string> = {
  DRAFT: "草稿",
  UPCOMING: "即将开始",
  RUNNING: "进行中",
  ENDED: "已结束",
  CANCELLED: "已取消",
};

const PHASE_CLASS: Record<ContestPhase, string> = {
  DRAFT: "bg-zinc-100 text-zinc-700",
  UPCOMING: "bg-blue-100 text-blue-700",
  RUNNING: "bg-green-100 text-green-700",
  ENDED: "bg-zinc-200 text-zinc-700",
  CANCELLED: "bg-red-100 text-red-700",
};

export default function ContestList() {
  const { user } = useAuth();
  const [contests, setContests] = useState<ContestSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const pageSize = 20;

  useEffect(() => {
    let active = true;
    setLoading(true);
    api.listContests({ page, pageSize }).then((response) => {
      if (!active) return;
      setContests(response.contests);
      setTotal(response.total);
      setError("");
    }).catch((exception) => active && setError(exception instanceof ApiError ? exception.message : "比赛列表加载失败"))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [page]);

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(interval);
  }, []);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  return <div className="px-4 py-6 sm:px-6 lg:px-8">
    <div className="mb-5 flex items-center justify-between">
      <div><h1 className="text-2xl font-bold">比赛</h1><p className="mt-1 text-sm text-zinc-500">阶段与提交权限以服务器时间为准</p></div>
      {(user?.role === "TEACHER" || user?.role === "ADMIN") && <Button asChild><Link to="/admin/contests/new"><Plus className="mr-1 h-4 w-4" />创建比赛</Link></Button>}
    </div>
    {error && <p role="alert" className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    {loading ? <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>
      : contests.length === 0 ? <Card className="p-12 text-center"><p className="text-zinc-500">{user?.role === "TEACHER" || user?.role === "ADMIN" ? "暂无比赛" : "暂无可参加的比赛"}</p>{(user?.role === "TEACHER" || user?.role === "ADMIN") && <Button className="mt-4" asChild><Link to="/admin/contests/new">创建第一场比赛</Link></Button>}</Card>
        : <div className="grid gap-3">{contests.map((contest) => <Card key={contest.id} className="p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div><Link className="text-lg font-semibold hover:underline" to={`/contests/${contest.id}`}>{contest.title}</Link>
              <p className="mt-1 line-clamp-2 text-sm text-zinc-500">{contest.description || "暂无说明"}</p></div>
            <span data-testid={`contest-phase-${contest.id}`} className={`rounded px-2 py-1 text-xs font-semibold ${phaseClass(contest.phase)}`}>{PHASE_LABEL[contest.phase]}</span>
          </div>
          <div className="mt-3 flex flex-wrap gap-3 text-xs text-zinc-500">
            <span><CalendarDays className="mr-1 inline h-3.5 w-3.5" />{formatTime(contest.startAt)} — {formatTime(contest.endAt)}</span>
            <span className="font-medium text-zinc-700">{phaseTiming(contest, now)}</span>
            <span>{contest.accessType === "OPEN" ? "公开报名" : "邀请制"}</span><span>创建者：{contest.ownerUsername ?? contest.ownerId}</span>
            {contest.participant && <span className="font-medium text-green-700">已参赛</span>}
          </div>
          <div className="mt-4 flex gap-2"><Button size="sm" asChild><Link to={`/contests/${contest.id}`}>查看比赛</Link></Button>
            {(user?.role === "TEACHER" || user?.role === "ADMIN") && <Button size="sm" variant="outline" asChild><Link to={`/admin/contests/${contest.id}`}>管理</Link></Button>}</div>
        </Card>)}</div>}
    {totalPages > 1 && <div className="mt-4 flex justify-center gap-2"><Button variant="outline" size="sm" disabled={page === 1} onClick={() => setPage(page - 1)}>上一页</Button><span className="py-1 text-sm">{page} / {totalPages}</span><Button variant="outline" size="sm" disabled={page === totalPages} onClick={() => setPage(page + 1)}>下一页</Button></div>}
  </div>;
}

export function formatTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

export function phaseClass(phase: ContestPhase) {
  return PHASE_CLASS[phase];
}

function phaseTiming(contest: ContestSummary, now: number) {
  if (contest.phase === "UPCOMING") return `${shortTime(contest.startAt)} 开始`;
  if (contest.phase === "RUNNING") return `进行中 · 剩余 ${shortDuration(Math.max(0, Date.parse(contest.endAt) - now))}`;
  if (contest.phase === "ENDED") return "已结束";
  if (contest.phase === "CANCELLED") return "已取消";
  return "尚未发布";
}

function shortTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hour12: false });
}

function shortDuration(milliseconds: number) {
  const minutes = Math.floor(milliseconds / 60_000);
  if (minutes >= 60) return `${Math.floor(minutes / 60)}小时${minutes % 60}分`;
  return `${minutes}分钟`;
}

export { PHASE_LABEL };
