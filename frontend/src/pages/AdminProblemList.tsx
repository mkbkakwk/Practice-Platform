import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Plus, Pencil, Loader2, Power, PowerOff, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { AdminTable } from "@/components/admin/AdminTable";
import { DIFFICULTY_CLASS } from "@/lib/verdict";
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
    <div className="admin-page">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">算法题管理</h1>
          <div className="mt-2 flex flex-wrap gap-3 text-sm">
            <Link className="font-medium text-foreground" to="/admin/problems">算法题</Link>
            <Link className="text-muted-foreground hover:text-foreground" to="/admin/office">Office 选择题</Link>
            <Link className="text-muted-foreground hover:text-foreground" to="/admin/office-doc">Office 排版练习</Link>
          </div>
        </div>
        <Button onClick={() => navigate("/admin/problems/new")} className="gap-1.5">
          <Plus className="h-4 w-4" /> 新建题目
        </Button>
      </div>

      {error && <div className="mb-4 rounded-md border border-danger/25 bg-danger/5 px-4 py-2 text-sm text-danger">{error}</div>}

      <h2 className="mb-3 text-sm font-medium text-secondary-foreground">可管理题目（{problems.length}）</h2>
      <AdminTable label="算法题管理">
        <table className="min-w-[1060px]">
          <thead><tr><th className="px-4 py-3">ID</th><th className="px-4 py-3">题目 / 创建信息</th><th className="px-4 py-3">难度</th><th className="px-4 py-3">状态</th><th className="px-4 py-3">操作</th></tr></thead>
          <tbody>
          {loading ? (
            <tr><td colSpan={5} className="p-12 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-muted-foreground" /></td></tr>
          ) : problems.length === 0 ? (
            <tr><td colSpan={5} className="p-8 text-center text-sm text-muted-foreground">暂无可管理题目</td></tr>
          ) : (
            <>
              {problems.map((problem) => (
                <tr key={problem.id}>
                  <td className="px-4 py-3 font-mono text-xs text-muted-foreground">#{problem.id}</td>
                  <td className="min-w-64 max-w-lg px-4 py-3">
                    <div className="font-medium text-foreground">{problem.title}</div>
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                      <code>{problem.slug}</code>
                      <span>创建者：{creatorLabel(problem)}</span>
                      <span>提交：{problem.submissionCount}</span>
                      <span>{formatCreatedAt(problem.createdAt)}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3"><Badge variant="outline" className={DIFFICULTY_CLASS[problem.difficulty]}>{DIFFICULTY_LABEL[problem.difficulty] ?? problem.difficulty}</Badge></td>
                  <td className="px-4 py-3"><Badge variant={problem.visible ? "success" : "neutral"}>
                    {problem.visible ? "已启用" : "已停用"}
                  </Badge></td>
                  <td className="px-4 py-3"><div className="flex gap-1">
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
                  </div></td>
                </tr>
              ))}
            </>
          )}
          </tbody>
        </table>
      </AdminTable>
    </div>
  );
}

function creatorLabel(problem: ProblemListItem) {
  return problem.createdBy == null ? "系统预置" : (problem.creatorUsername ?? `用户 #${problem.createdBy}`);
}

function formatCreatedAt(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}
