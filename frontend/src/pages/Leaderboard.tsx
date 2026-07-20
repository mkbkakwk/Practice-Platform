import { useEffect, useState } from "react";
import { api, type PublicUser } from "@/lib/api";
import { Card } from "@/components/ui/card";
import { Loader2, Trophy, Medal } from "lucide-react";
import { cn } from "@/lib/utils";

interface Row extends PublicUser {
  rank: number;
  createdAt: string;
}

export default function Leaderboard() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api
      .leaderboard(50)
      .then((d) => active && setRows(d.leaderboard as Row[]))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">
      <div className="mb-4 flex items-center gap-2">
        <Trophy className="h-6 w-6 text-yellow-500" />
        <h1 className="text-2xl font-bold">排行榜</h1>
        <span className="ml-auto text-sm text-zinc-500">按通过题数排名</span>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-left text-xs uppercase text-zinc-500">
            <tr>
              <th className="w-16 px-4 py-3 font-medium">排名</th>
              <th className="px-4 py-3 font-medium">用户</th>
              <th className="w-32 px-4 py-3 text-right font-medium">通过题数</th>
              <th className="hidden w-40 px-4 py-3 text-right font-medium sm:table-cell">注册时间</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-zinc-400">
                  <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                </td>
              </tr>
            ) : rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-zinc-400">
                  暂无数据
                </td>
              </tr>
            ) : (
              rows.map((r) => {
                const medal =
                  r.rank === 1 ? "text-yellow-500" : r.rank === 2 ? "text-zinc-400" : r.rank === 3 ? "text-amber-700" : "";
                return (
                  <tr key={r.id} className="transition-colors hover:bg-zinc-50">
                    <td className="px-4 py-3">
                      <span className="flex items-center gap-1.5">
                        {r.rank <= 3 ? (
                          <Medal className={cn("h-4 w-4", medal)} />
                        ) : null}
                        <span className={cn("font-semibold", r.rank <= 3 && medal)}>{r.rank}</span>
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className="font-medium text-zinc-900">{r.username}</span>
                      {r.role === "ADMIN" && (
                        <span className="ml-2 rounded bg-zinc-900 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                          管理员
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right font-semibold text-zinc-900">{r.solvedCount ?? 0}</td>
                    <td className="hidden px-4 py-3 text-right text-xs text-zinc-400 sm:table-cell">
                      {new Date(r.createdAt).toLocaleDateString("zh-CN")}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
