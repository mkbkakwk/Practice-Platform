import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Plus, Pencil, Loader2, Power, PowerOff, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api, type ProblemListItem, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";

const STOP_WARNING = "停用后学生无法继续查看或提交，但历史记录和成绩会保留。";
const DELETE_WARNING = "永久删除后，相关学生提交、答案、代码、文档、评分和统计将被清理，无法恢复。";
const TEACHER_BLOCKED = "该内容已有学生提交，只能停用，不能彻底删除。";
const DIFFICULTY_LABEL: Record<string, string> = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };

export default function AdminProblemList() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [problems, setProblems] = useState<ProblemListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    try {
      const response = await api.listManageProblems({ page: 1, pageSize: 50 });
      setProblems(response.problems);
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  async function toggle(problem: ProblemListItem) {
    const nextVisible = !problem.visible;
    if (!nextVisible && !window.confirm(STOP_WARNING)) return;
    setBusy(`toggle-${problem.id}`);
    setError("");
    try {
      await api.setProblemVisibility(problem.slug, nextVisible);
      await load();
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "操作失败");
    } finally {
      setBusy(null);
    }
  }

  async function hardDelete(problem: ProblemListItem) {
    if (user?.role === "TEACHER" && problem.submissionCount > 0) {
      window.alert(TEACHER_BLOCKED);
      return;
    }
    const danger = user?.role === "ADMIN" && problem.submissionCount > 0 ? "危险操作：该题已有学生提交。\n\n" : "";
    if (!window.confirm(`${danger}${DELETE_WARNING}\n\n确认彻底删除“${problem.title}”吗？`)) return;
    setBusy(`delete-${problem.id}`);
    setError("");
    try {
      await api.deleteProblem(problem.slug);
      await load();
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "删除失败");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">算法题管理</h1>
          <div className="mt-2 flex gap-3 text-sm">
            <Link className="font-medium text-zinc-900" to="/admin/problems">算法题</Link>
            <Link className="text-zinc-500 hover:text-zinc-900" to="/admin/office">Office 选择题</Link>
            <Link className="text-zinc-500 hover:text-zinc-900" to="/admin/office-doc">Office 排版练习</Link>
          </div>
        </div>
        <Button onClick={() => navigate("/admin/problems/new")} className="gap-1.5">
          <Plus className="h-4 w-4" /> 新建题目
        </Button>
      </div>

      {error && <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">{error}</div>}

      <Card>
        <CardHeader><CardTitle className="text-base">可管理题目（{problems.length}）</CardTitle></CardHeader>
        <CardContent className="p-0">
          {loading ? (
            <div className="flex h-32 items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-zinc-400" /></div>
          ) : problems.length === 0 ? (
            <div className="p-8 text-center text-sm text-zinc-500">暂无可管理题目</div>
          ) : (
            <div className="divide-y divide-zinc-100">
              {problems.map((problem) => (
                <div key={problem.id} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-zinc-50">
                  <span className="w-10 text-xs text-zinc-400">#{problem.id}</span>
                  <div className="min-w-64 flex-1">
                    <div className="font-medium text-zinc-900">{problem.title}</div>
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-zinc-500">
                      <code>{problem.slug}</code>
                      <span>创建者：{creatorLabel(problem)}</span>
                      <span>提交：{problem.submissionCount}</span>
                      <span>{formatCreatedAt(problem.createdAt)}</span>
                    </div>
                  </div>
                  <Badge variant="secondary">{DIFFICULTY_LABEL[problem.difficulty] ?? problem.difficulty}</Badge>
                  <Badge className={problem.visible ? "bg-green-100 text-green-700" : "bg-zinc-100 text-zinc-600"}>
                    {problem.visible ? "已启用" : "已停用"}
                  </Badge>
                  <Button variant="outline" size="sm" onClick={() => navigate(`/admin/problems/${problem.slug}/edit`)}>
                    <Pencil className="mr-1 h-3.5 w-3.5" /> 编辑
                  </Button>
                  <Button variant="outline" size="sm" disabled={busy !== null} onClick={() => void toggle(problem)}>
                    {problem.visible ? <PowerOff className="mr-1 h-3.5 w-3.5" /> : <Power className="mr-1 h-3.5 w-3.5" />}
                    {problem.visible ? "停用" : "启用"}
                  </Button>
                  <Button variant="destructive" size="sm" disabled={busy !== null} onClick={() => void hardDelete(problem)}>
                    <Trash2 className="mr-1 h-3.5 w-3.5" /> 彻底删除
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

function creatorLabel(problem: ProblemListItem) {
  return problem.createdBy == null ? "系统预置" : (problem.creatorUsername ?? `用户 #${problem.createdBy}`);
}

function formatCreatedAt(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}
