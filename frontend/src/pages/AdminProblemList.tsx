import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Pencil, Loader2, Eye, EyeOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { api, type ProblemListItem, ApiError } from "@/lib/api";

const DIFFICULTY_LABEL: Record<string, string> = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };
const DIFFICULTY_CLASS: Record<string, string> = {
  EASY: "bg-green-100 text-green-700",
  MEDIUM: "bg-amber-100 text-amber-700",
  HARD: "bg-red-100 text-red-700",
};

export default function AdminProblemList() {
  const navigate = useNavigate();
  const [problems, setProblems] = useState<ProblemListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .listProblems({ page: 1, pageSize: 50 })
      .then((res) => setProblems(res.problems))
      .catch((e) => setError(e instanceof ApiError ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold">题目管理</h1>
        <Button onClick={() => navigate("/admin/problems/new")} className="gap-1.5">
          <Plus className="h-4 w-4" /> 新建题目
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">全部题目（{problems.length}）</CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="flex h-32 items-center justify-center">
              <Loader2 className="h-6 w-6 animate-spin text-zinc-400" />
            </div>
          ) : problems.length === 0 ? (
            <div className="p-8 text-center text-sm text-zinc-500">暂无题目，点击右上角「新建题目」</div>
          ) : (
            <div className="divide-y divide-zinc-100">
              {problems.map((p) => (
                <div key={p.id} className="flex items-center gap-3 px-4 py-3 hover:bg-zinc-50">
                  <span className="w-10 text-xs text-zinc-400">#{p.id}</span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate font-medium text-zinc-900">{p.title}</span>
                    </div>
                    <div className="mt-0.5 flex items-center gap-2 text-xs text-zinc-500">
                      <code className="rounded bg-zinc-100 px-1 py-0.5">{p.slug}</code>
                      <span>{p.timeLimit}ms / {p.memoryLimit}MB</span>
                      {(p.tags ?? []).slice(0, 3).map((t) => (
                        <Badge key={t} variant="outline" className="text-[10px]">{t}</Badge>
                      ))}
                    </div>
                  </div>
                  <Badge className={DIFFICULTY_CLASS[p.difficulty]} variant="secondary">
                    {DIFFICULTY_LABEL[p.difficulty] ?? p.difficulty}
                  </Badge>
                  {p.visible === false && (
                    <span className="flex items-center gap-0.5 text-xs text-amber-600">
                      <EyeOff className="h-3 w-3" /> 隐藏
                    </span>
                  )}
                  {p.visible !== false && (
                    <span className="flex items-center gap-0.5 text-xs text-zinc-400">
                      <Eye className="h-3 w-3" /> 可见
                    </span>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate(`/admin/problems/${p.slug}/edit`)}
                    className="gap-1.5"
                  >
                    <Pencil className="h-3.5 w-3.5" /> 编辑
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
