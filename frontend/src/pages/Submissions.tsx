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
  const [mine, setMine] = useState(true);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setMine(user?.role !== "ADMIN");
    setPage(1);
  }, [user?.role]);

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
    <div className="pilot-page">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight">提交记录</h1>
        <div className="flex gap-2">
          {user?.role === "ADMIN" && (
            <Button size="sm" variant={!mine ? "default" : "outline"} onClick={() => setMine(false)}>
              全站
            </Button>
          )}
          {user && (
            <Button size="sm" variant={mine ? "default" : "outline"} onClick={() => setMine(true)}>
              我的
            </Button>
          )}
        </div>
      </div>

      <Card className="gap-0 overflow-hidden py-0">
        <div className="overflow-x-auto" role="region" aria-label="提交记录表格" tabIndex={0}>
        <table className="w-full min-w-[480px] text-sm">
          <caption className="sr-only">提交记录</caption>
          <thead className="bg-surface text-left text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="w-16 px-4 py-3 font-medium">#</th>
              <th scope="col" className="px-4 py-3 font-medium">题目</th>
              <th scope="col" className="hidden px-4 py-3 font-medium sm:table-cell">用户</th>
              <th scope="col" className="w-24 px-4 py-3 font-medium">语言</th>
              <th scope="col" className="w-24 px-4 py-3 font-medium">结果</th>
              <th scope="col" className="hidden w-32 px-4 py-3 font-medium md:table-cell">耗时</th>
              <th scope="col" className="hidden w-40 px-4 py-3 font-medium lg:table-cell">时间</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-muted-foreground">
                  <span role="status" aria-label="加载提交记录"><Loader2 aria-hidden="true" className="mx-auto h-5 w-5 animate-spin" /><span className="sr-only">加载中</span></span>
                </td>
              </tr>
            ) : submissions.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-muted-foreground">
                  暂无提交记录
                </td>
              </tr>
            ) : (
              submissions.map((s) => (
                <tr key={s.id} className="transition-colors duration-150 hover:bg-elevated">
                  <td className="pilot-numeric px-4 py-3 text-muted-foreground">{s.id}</td>
                  <td className="px-4 py-3">
                    {s.problem ? (
                      <Link
                        to={`/problem/${s.problem.slug}`}
                        className="font-medium text-foreground hover:underline"
                      >
                        {s.problem.title}
                      </Link>
                    ) : (
                      <span className="text-muted-foreground">已删除</span>
                    )}
                    {s.problem && (
                      <span className="ml-2 text-xs text-muted-foreground">
                        {DIFFICULTY_LABEL[s.problem.difficulty] || s.problem.difficulty}
                      </span>
                    )}
                  </td>
                  <td className="hidden px-4 py-3 text-subtle sm:table-cell">
                    {s.user?.username || "-"}
                  </td>
                  <td className="px-4 py-3">
                    <span className="rounded bg-elevated px-1.5 py-0.5 text-xs text-subtle">
                      {LANG_LABEL[s.language] || s.language}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <VerdictBadge verdict={s.verdict} />
                  </td>
                  <td className="hidden pilot-numeric px-4 py-3 text-xs text-muted-foreground md:table-cell">
                    {s.verdict === "AC" || s.verdict === "WA" || s.verdict === "TLE" || s.verdict === "RE"
                      ? `${s.timeMs}ms`
                      : "-"}
                  </td>
                  <td className="hidden pilot-numeric px-4 py-3 text-xs text-muted-foreground lg:table-cell">
                    {new Date(s.createdAt).toLocaleString("zh-CN", { hour12: false })}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        </div>
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
          <span className={cn("text-sm text-subtle")}>
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
