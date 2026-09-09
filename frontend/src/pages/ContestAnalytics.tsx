import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { BarChart3, Loader2, Search } from "lucide-react";
import { api, getApiErrorMessage, type ContestAnalytics as Analytics, type ContestAnalyticsParticipant } from "@/lib/api";
import { AdminTable } from "@/components/admin/AdminTable";
import { adminPhaseClass } from "@/components/admin/presentation";
import { cn } from "@/lib/utils";

const pct = (value: number | null) => value == null ? "—" : `${(value * 100).toFixed(1)}%`;
const number = (value: number | null) => value == null ? "—" : Number.isInteger(value) ? String(value) : value.toFixed(1);

export default function ContestAnalytics() {
  const { id } = useParams<{ id: string }>();
  const contestId = Number(id);
  const [analyticsData, setData] = useState<Analytics | null>(null);
  const [rows, setRows] = useState<ContestAnalyticsParticipant[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState("");
  const [queryInput, setQueryInput] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const sequence = useRef(0);

  useEffect(() => { const timer = window.setTimeout(() => { setPage(1); setQuery(queryInput); }, 300); return () => window.clearTimeout(timer); }, [queryInput]);
  useEffect(() => {
    let live = true; const controller = new AbortController();
    const load = async () => {
      const token = ++sequence.current; setLoading(true);
      try {
        const [overview, participants] = await Promise.all([api.getContestAnalytics(contestId, controller.signal), api.getContestAnalyticsParticipants(contestId, { page, pageSize: 20, query }, controller.signal)]);
        if (live && token === sequence.current) { setData(overview.analytics); setRows(participants.participants.participants); setTotal(participants.participants.total); setError(""); }
      } catch (reason) { if (live && token === sequence.current && !(reason instanceof DOMException && reason.name === "AbortError")) setError(getApiErrorMessage(reason, "数据分析加载失败")); }
      finally { if (live && token === sequence.current) setLoading(false); }
    };
    void load(); return () => { live = false; controller.abort(); };
  }, [contestId, page, query, refresh]);
  useEffect(() => { if (analyticsData?.contestId !== contestId || analyticsData.phase !== "RUNNING") return; const timer = window.setInterval(() => setRefresh((value) => value + 1), 15_000); return () => window.clearInterval(timer); }, [contestId, analyticsData?.contestId, analyticsData?.phase]);

  const data = analyticsData?.contestId === contestId ? analyticsData : null;
  if (loading && !data) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-muted-foreground" /></div>;
  if (!data) return <div className="mx-auto max-w-7xl p-6"><p role="alert" className="rounded border border-danger/25 bg-danger/5 p-3 text-sm text-danger">{error || "数据分析不可用"}</p></div>;

  const o = data.overview;
  const pages = Math.max(1, Math.ceil(total / 20));
  const scoreMetrics = data.scoringMode === "SCORE"
    ? [["平均总分", number(o.averageTotalScore)], ["最高分", number(o.maxTotalScore)], ["满分人数", String(o.fullScoreParticipantCount ?? 0)]]
    : [["平均解题", number(o.averageSolved)], ["最高解题", number(o.maxSolved)], ["平均罚时", number(o.averagePenaltyAmongSolvedParticipants)]];
  const timelineMax = Math.max(1, ...data.timeline.map((item) => item.submissionCount));

  return <main className="admin-page">
    <header className="management-hero">
      <Link className="text-sm text-muted-foreground hover:underline" to={`/admin/contests/${contestId}`}>← 返回比赛管理</Link>
      <div className="mt-4 flex flex-wrap items-start justify-between gap-3"><div><p className="management-kicker">比赛数据分析</p><h1 className="management-title">{data.title}</h1><p className="management-summary">{data.phase === "RUNNING" ? "比赛进行中；统计每 15 秒更新。" : `数据更新于 ${new Date(data.generatedAt).toLocaleString()}`}</p></div><span className={cn("rounded px-3 py-1 text-sm font-semibold", adminPhaseClass(data.phase))}>{data.phase}</span></div>
      <div className="management-deck"><Metric label="参赛人数" value={o.participantCount} /><Metric label="实际参与" value={o.activeParticipantCount} /><Metric label="未提交" value={o.inactiveParticipantCount} /><Metric label="总提交" value={o.totalSubmissionCount} /></div>
      <nav className="management-section-nav" aria-label="比赛数据分区"><a className="is-active" href="#analytics-overview">概览</a><a href="#analytics-problems">题目</a><a href="#analytics-participants">参赛者</a></nav>
    </header>
    {error && <p role="alert" className="mt-4 rounded border border-danger/25 bg-danger/5 p-3 text-sm text-danger">{error}</p>}
    <section id="analytics-overview" className="management-layout"><div className="management-main space-y-5">
      <section className="management-panel"><div className="management-panel-header"><div><h2 className="management-panel-title">成绩与参与概览</h2><p className="management-panel-description">仅基于当前比赛数据；无额外遥测或推断指标。</p></div><BarChart3 className="h-5 w-5 text-muted-foreground" /></div><div className="management-metric-strip">{scoreMetrics.map(([label, value]) => <Metric key={label} label={label} value={value} />)}<Metric label="算法提交" value={o.algorithmSubmissionCount} /></div></section>
      <section className="management-split"><div className="management-chart"><h2 className="management-panel-title">提交趋势</h2><p className="management-panel-description">按服务端返回时间段聚合。</p><div className="mt-5 flex h-40 items-end gap-1.5" aria-label="提交趋势图">{data.timeline.map((bucket, index) => <div key={`${bucket.startAt}-${index}`} className="flex h-full min-w-0 flex-1 flex-col" title={`${bucket.submissionCount} 次提交`}><div className="flex min-h-0 flex-1 items-end"><div className="w-full rounded-t bg-brand/45" style={{ height: `${Math.max(3, Math.round(bucket.submissionCount / timelineMax * 100))}%` }} /></div><span className="mt-1 block h-4 shrink-0 truncate text-center font-mono text-[10px] text-muted-foreground">{index + 1}</span></div>)}</div>{data.timeline.length === 0 && <p className="mt-3 text-sm text-muted-foreground">暂无时间段数据。</p>}</div><div className="management-chart"><h2 className="management-panel-title">{data.scoringMode === "SCORE" ? "成绩分布" : "解题数分布"}</h2><p className="management-panel-description">参赛者在当前计分规则下的分布。</p><div className="mt-5 space-y-3">{data.distribution.map((bucket) => <div key={bucket.label} className="grid grid-cols-[4rem_minmax(0,1fr)_2rem] items-center gap-3"><span className="font-mono text-xs text-muted-foreground">{bucket.label}</span><div className="h-1.5 overflow-hidden rounded bg-elevated"><div className="h-full rounded bg-secondary-foreground/55" style={{ width: `${o.participantCount ? bucket.participantCount / o.participantCount * 100 : 0}%` }} /></div><span className="text-right font-mono text-xs tabular-nums">{bucket.participantCount}</span></div>)}</div></div></section>
    </div><aside className="management-rail"><section className="management-panel"><div className="management-panel-header"><div><h2 className="management-panel-title">提交构成</h2><p className="management-panel-description">按已有题型记录汇总。</p></div></div><dl className="divide-y divide-border"><MetricRow label="算法" value={o.algorithmSubmissionCount} /><MetricRow label="Office 选择" value={o.choiceSubmissionCount} /><MetricRow label="DOCX" value={o.docxSubmissionCount} /><MetricRow label="首次提交" value={o.firstSubmissionAt ? new Date(o.firstSubmissionAt).toLocaleString() : "—"} /><MetricRow label="最后提交" value={o.lastSubmissionAt ? new Date(o.lastSubmissionAt).toLocaleString() : "—"} /></dl></section><section className="management-panel"><div className="management-panel-header"><div><h2 className="management-panel-title">分析说明</h2><p className="management-panel-description">排名、得分和提交统计均由服务端现有规则计算。</p></div></div><div className="management-panel-body text-sm leading-6 text-muted-foreground">{data.scoringMode === "SCORE" ? "SCORE：显示各题最高分与总分统计。" : "ICPC：显示解题数与罚时统计。"}</div></section></aside></section>
    <section id="analytics-problems" className="management-panel mt-6"><div className="management-panel-header"><div><h2 className="management-panel-title">题目分析</h2><p className="management-panel-description">参与、完成和题型专属指标按每道比赛题聚合。</p></div></div><AdminTable label="题目分析"><table className="min-w-[820px]"><thead><tr><th className="px-4 py-3">题目</th><th className="px-4 py-3">类型</th><th className="px-4 py-3">提交 / 参与</th><th className="px-4 py-3">完成</th><th className="px-4 py-3">关键指标</th></tr></thead><tbody>{data.problems.map((problem) => <tr key={problem.contestProblemId}><td className="max-w-72 truncate px-4 py-3" title={problem.title}><span className="mr-2 font-mono text-xs text-muted-foreground">{problem.label}</span>{problem.title}</td><td className="px-4 py-3 text-muted-foreground">{problem.problemType}</td><td className="px-4 py-3 font-mono text-xs">{problem.submissionCount} / {problem.uniqueSubmitterCount} · {pct(problem.participationRate)}</td><td className="px-4 py-3 font-mono text-xs">{problem.successParticipantCount} · {pct(problem.successRate)}</td><td className="px-4 py-3 text-xs text-secondary-foreground">{problem.problemType === "ALGORITHM" ? `AC ${pct(problem.submissionAcceptanceRate)} · 平台失败 ${problem.infrastructureFailureCount}` : problem.problemType === "OFFICE_CHOICE" ? `正确率 ${pct(problem.correctSubmissionRate)}` : `最佳均分 ${number(problem.averageBestScore)} · 满分 ${problem.perfectScoreParticipantCount ?? 0}`}</td></tr>)}</tbody></table></AdminTable>{data.problems.length === 0 && <p className="management-empty">暂无题目数据。</p>}</section>
    <section id="analytics-participants" className="management-panel mt-6"><div className="management-toolbar"><div><h2 className="management-panel-title">参赛者分析（{total}）</h2><p className="management-panel-description">快速定位参赛者的提交和完成情况。</p></div><label className="relative block w-full sm:w-72"><span className="sr-only">搜索用户名</span><Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" /><input className="h-9 w-full rounded border border-border bg-card pl-9 pr-3 text-sm" value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder="搜索用户名" /></label></div><AdminTable label="参赛者分析"><table className="min-w-[780px]"><thead><tr><th className="px-4 py-3">排名</th><th className="px-4 py-3">学生</th><th className="px-4 py-3">提交</th><th className="px-4 py-3">已提交 / 完成</th><th className="px-4 py-3">最后活动</th><th className="px-4 py-3">{data.scoringMode === "SCORE" ? "总分" : "解题 / 罚时"}</th></tr></thead><tbody>{rows.map((row) => <tr key={row.userId}><td className="px-4 py-3 font-mono">{row.rank ?? "—"}</td><td className="max-w-48 truncate px-4 py-3 font-medium" title={row.username}>{row.username}</td><td className="px-4 py-3 font-mono">{row.totalSubmissionCount}</td><td className="px-4 py-3 font-mono">{row.submittedProblemCount} / {row.successfulProblemCount}</td><td className="px-4 py-3 font-mono text-xs text-muted-foreground">{row.lastSubmissionAt ? new Date(row.lastSubmissionAt).toLocaleString() : "—"}</td><td className="px-4 py-3 font-mono">{data.scoringMode === "SCORE" ? row.totalScore : `${row.solved} / ${row.penaltyMinutes}`}</td></tr>)}</tbody></table></AdminTable><div className="flex items-center justify-end gap-3 px-4 py-3 text-sm sm:px-5"><button className="rounded border px-3 py-1 disabled:opacity-50" disabled={page === 1} onClick={() => setPage(page - 1)}>上一页</button><span className="font-mono tabular-nums">{page} / {pages}</span><button className="rounded border px-3 py-1 disabled:opacity-50" disabled={page === pages} onClick={() => setPage(page + 1)}>下一页</button></div></section>
  </main>;
}

function Metric({ label, value }: { label: string; value: string | number }) { return <div className="management-metric"><p className="management-deck-label">{label}</p><strong>{value}</strong></div>; }
function MetricRow({ label, value }: { label: string; value: string | number }) { return <div className="flex items-center justify-between gap-4 px-4 py-3 text-sm"><dt className="text-muted-foreground">{label}</dt><dd className="max-w-[62%] truncate text-right font-mono text-xs tabular-nums">{value}</dd></div>; }
