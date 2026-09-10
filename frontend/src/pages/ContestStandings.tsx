import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { api, getApiErrorMessage, type ContestStanding } from "@/lib/api";
import { Card } from "@/components/ui/card";

export default function ContestStandings() {
  const { id } = useParams<{ id: string }>();
  const contestId = Number(id);
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

  if (loading) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>;
  if (!standing) return <div className="mx-auto max-w-7xl p-6"><p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error || "排名不可用"}</p></div>;

  return <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
    <Link className="text-sm text-zinc-500 hover:underline" to={`/contests/${contestId}`}>← 返回比赛</Link>
    <div className="mt-3 mb-5"><h1 className="text-2xl font-bold">比赛排名</h1><p className="mt-1 text-sm text-zinc-500">{standing.scoringMode === "ICPC" ? "ICPC：解题数优先，罚时次之" : "得分：每题取有效提交中的最高分"}</p></div>
    {standing.frozen && <p className="mb-4 rounded border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">比赛已封榜；当前排名仅展示封榜前提交。</p>}
    {standing.managerView && standing.phase === "RUNNING" && standing.freezeAt && <p className="mb-4 rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">管理员实时榜单：学生视图会在封榜后隐藏封榜后的提交。</p>}
    <Card className="overflow-x-auto p-0"><table className="min-w-full text-sm"><thead className="bg-zinc-50 text-left text-zinc-600"><tr><th className="px-4 py-3">排名</th><th className="px-4 py-3">用户</th>{standing.scoringMode === "ICPC" ? <><th className="px-4 py-3">解题</th><th className="px-4 py-3">罚时</th></> : <th className="px-4 py-3">总分</th>}{standing.entries[0]?.problems.map((problem) => <th key={problem.contestProblemId} className="px-4 py-3">{problem.label}</th>)}</tr></thead>
      <tbody>{standing.entries.map((entry) => <tr key={entry.userId} className="border-t"><td className="px-4 py-3 font-semibold">{entry.rank}</td><td className="px-4 py-3">{entry.username}</td>{standing.scoringMode === "ICPC" ? <><td className="px-4 py-3">{entry.solved}</td><td className="px-4 py-3">{entry.penaltyMinutes}</td></> : <td className="px-4 py-3">{entry.totalScore}</td>}{entry.problems.map((problem) => <td key={problem.contestProblemId} className="px-4 py-3">{standing.scoringMode === "ICPC" ? (problem.solved ? `AC · ${problem.penaltyMinutes}分` : problem.attempts ? `${problem.attempts} 次` : "—") : `${problem.score ?? 0}`}</td>)}</tr>)}</tbody>
    </table></Card>
    {standing.entries.length === 0 && <p className="mt-4 text-sm text-zinc-500">暂无参赛者。</p>}
  </div>;
}
