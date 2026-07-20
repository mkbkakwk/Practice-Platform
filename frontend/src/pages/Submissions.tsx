import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type Submission } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { VerdictBadge, DIFFICULTY_LABEL } from "@/lib/verdict";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

const LANG_LABEL: Record<string, string> = {
  python: "Python",
  javascript: "JS",
  cpp: "C++",
  c: "C",
  java: "Java",
};

export default function Submissions() {
  const { user } = useAuth();
  const [mine, setMine] = useState(false);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setPage(1);
  }, [mine]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    const fn = mine ? api.mySubmissions.bind(api) : api.listSubmissions.bind(api);
    fn({ page, pageSize })
      .then((data) => {
        if (!active) return;
        setSubmissions(data.submissions as Submission[]);
        setTotal(data.total);
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [page, pageSize, mine]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">提交记录</h1>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant={!mine ? "default" : "outline"}
            onClick={() => setMine(false)}
          >
            全站
          </Button>
          {user && (
            <Button
              size="sm"
              variant={mine ? "default" : "outline"}
              onClick={() => setMine(true)}
            >
              我的
            </Button>
          )}
        </div>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-left text-xs uppercase text-zinc-500">
            <tr>
              <th className="w-16 px-4 py-3 font-medium">#</th>
              <th className="px-4 py-3 font-medium">题目</th>
              <th className="hidden px-4 py-3 font-medium sm:table-cell">用户</th>
              <th className="w-24 px-4 py-3 font-medium">语言</th>
              <th className="w-24 px-4 py-3 font-medium">结果</th>
              <th className="hidden w-32 px-4 py-3 font-medium md:table-cell">耗时</th>
              <th className="hidden w-40 px-4 py-3 font-medium lg:table-cell">时间</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-zinc-400">
                  <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                </td>
              </tr>
            ) : submissions.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-zinc-400">
                  暂无提交记录
                </td>
              </tr>
            ) : (
              submissions.map((s) => (
                <tr key={s.id} className="transition-colors hover:bg-zinc-50">
                  <td className="px-4 py-3 text-zinc-400">{s.id}</td>
                  <td className="px-4 py-3">
                    {s.problem ? (
                      <Link
                        to={`/problem/${s.problem.slug}`}
                        className="font-medium text-zinc-900 hover:underline"
                      >
                        {s.problem.title}
                      </Link>
                    ) : (
                      <span className="text-zinc-400">已删除</span>
                    )}
                    {s.problem && (
                      <span className="ml-2 text-xs text-zinc-400">
                        {DIFFICULTY_LABEL[s.problem.difficulty] || s.problem.difficulty}
                      </span>
                    )}
                  </td>
                  <td className="hidden px-4 py-3 text-zinc-600 sm:table-cell">
                    {s.user?.username || "-"}
                  </td>
                  <td className="px-4 py-3">
                    <span className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-600">
                      {LANG_LABEL[s.language] || s.language}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <VerdictBadge verdict={s.verdict} />
                  </td>
                  <td className="hidden px-4 py-3 text-xs text-zinc-500 md:table-cell">
                    {s.verdict === "AC" || s.verdict === "WA" || s.verdict === "TLE" || s.verdict === "RE"
                      ? `${s.timeMs}ms`
                      : "-"}
                  </td>
                  <td className="hidden px-4 py-3 text-xs text-zinc-400 lg:table-cell">
                    {new Date(s.createdAt).toLocaleString("zh-CN", { hour12: false })}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </Card>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </Button>
          <span className={cn("text-sm text-zinc-600")}>
            {page} / {totalPages}
          </span>
          <Button
            size="sm"
            variant="outline"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </Button>
        </div>
      )}
    </div>
  );
}
