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
    <div className="mx-auto max-w-6xl px-4 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">题库</h1>
        <span className="text-sm text-zinc-500">共 {total} 题</span>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-sm text-zinc-500">难度：</span>
        {diffs.map((d) => (
          <Button
            key={d.key}
            size="sm"
            variant={difficulty === d.key ? "default" : "outline"}
            onClick={() => {
              setDifficulty(d.key);
              setPage(1);
            }}
          >
            {d.label}
          </Button>
        ))}
      </div>

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-left text-xs uppercase text-zinc-500">
            <tr>
              <th className="w-16 px-4 py-3 font-medium">#</th>
              <th className="px-4 py-3 font-medium">题目</th>
              <th className="w-24 px-4 py-3 font-medium">难度</th>
              <th className="hidden w-40 px-4 py-3 font-medium sm:table-cell">标签</th>
              <th className="hidden w-32 px-4 py-3 font-medium md:table-cell">时间/内存</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-zinc-400">
                  <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                </td>
              </tr>
            ) : problems.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-zinc-400">
                  暂无题目
                </td>
              </tr>
            ) : (
              problems.map((p) => (
                <tr key={p.id} className="transition-colors hover:bg-zinc-50">
                  <td className="px-4 py-3 text-zinc-400">{p.id}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/problem/${p.slug}`}
                      className="font-medium text-zinc-900 hover:underline"
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
                  <td className="hidden px-4 py-3 sm:table-cell">
                    <div className="flex flex-wrap gap-1">
                      {p.tags.slice(0, 3).map((t) => (
                        <span
                          key={t}
                          className="rounded bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-600"
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="hidden px-4 py-3 text-xs text-zinc-500 md:table-cell">
                    {p.timeLimit}ms / {p.memoryLimit}MB
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
          <span className="text-sm text-zinc-600">
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
