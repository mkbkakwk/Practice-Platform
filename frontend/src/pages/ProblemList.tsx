import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type ProblemListItem } from "@/lib/api";
import { DIFFICULTY_CLASS, DIFFICULTY_LABEL } from "@/lib/verdict";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

export default function ProblemList() {
  const [problems, setProblems] = useState<ProblemListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [difficulty, setDifficulty] = useState<string>("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api
      .listProblems({ page, pageSize, difficulty: difficulty || undefined })
      .then((data) => {
        if (!active) return;
        setProblems(data.problems);
        setTotal(data.total);
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [page, pageSize, difficulty]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const diffs = [
    { key: "", label: "全部" },
    { key: "EASY", label: "简单" },
    { key: "MEDIUM", label: "中等" },
    { key: "HARD", label: "困难" },
  ];

  return (
    <div className="pilot-page">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight">题库</h1>
        <span className="text-sm text-muted-foreground">共 {total} 题</span>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-sm text-muted-foreground">难度：</span>
        {diffs.map((d) => (
          <Button
            key={d.key}
            size="sm"
            variant={difficulty === d.key ? "default" : "outline"}
            aria-pressed={difficulty === d.key}
            onClick={() => {
              setDifficulty(d.key);
              setPage(1);
            }}
          >
            {d.label}
          </Button>
        ))}
      </div>

      <Card className="gap-0 overflow-hidden py-0">
        <div className="overflow-x-auto" role="region" aria-label="训练题库表格" tabIndex={0}>
        <table className="w-full min-w-[480px] text-sm">
          <caption className="sr-only">训练题库</caption>
          <thead className="bg-surface text-left text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="w-16 px-4 py-3 font-medium">#</th>
              <th scope="col" className="px-4 py-3 font-medium">题目</th>
              <th scope="col" className="w-24 px-4 py-3 font-medium">难度</th>
              <th scope="col" className="hidden w-28 px-4 py-3 font-medium lg:table-cell">创建者</th>
              <th scope="col" className="hidden w-40 px-4 py-3 font-medium sm:table-cell">标签</th>
              <th scope="col" className="hidden w-32 px-4 py-3 font-medium md:table-cell whitespace-nowrap">时间/内存</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                  <span role="status" aria-label="加载训练题库"><Loader2 aria-hidden="true" className="mx-auto h-5 w-5 animate-spin" /><span className="sr-only">加载中</span></span>
                </td>
              </tr>
            ) : problems.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                  暂无题目
                </td>
              </tr>
            ) : (
              problems.map((p) => (
                <tr key={p.id} className="transition-colors duration-150 hover:bg-elevated">
                  <td className="pilot-numeric px-4 py-3 text-muted-foreground">{p.id}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/problem/${p.slug}`}
                      className="font-medium text-foreground hover:underline"
                    >
                      {p.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={cn(
                        "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium",
                        DIFFICULTY_CLASS[p.difficulty],
                      )}
                    >
                      {DIFFICULTY_LABEL[p.difficulty]}
                    </span>
                  </td>
                  <td className="hidden pilot-numeric px-4 py-3 text-xs text-subtle lg:table-cell">
                    {p.createdBy == null ? "系统预置" : (p.creatorUsername ?? "未知")}
                  </td>
                  <td className="hidden px-4 py-3 sm:table-cell">
                    <div className="flex flex-wrap gap-1">
                      {(p.tags || []).slice(0, 3).map((t) => (
                        <span
                          key={t}
                          className="rounded bg-elevated px-1.5 py-0.5 text-xs text-subtle"
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="hidden pilot-numeric whitespace-nowrap px-4 py-3 text-xs text-muted-foreground md:table-cell">
                    {p.timeLimit}ms / {p.memoryLimit}MB
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
          <span className="text-sm text-subtle">
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
