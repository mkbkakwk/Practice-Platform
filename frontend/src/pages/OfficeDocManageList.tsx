import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type DocExerciseListItem, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, Plus, Pencil, Power, PowerOff, Trash2, ClipboardCheck } from "lucide-react";

const STOP_WARNING = "停用后学生无法继续查看或提交，但历史记录和成绩会保留。";
const DELETE_WARNING = "永久删除后，相关学生提交、答案、代码、文档、评分和统计将被清理，无法恢复。";
const TEACHER_BLOCKED = "该内容已有学生提交，只能停用，不能彻底删除。";

export default function OfficeDocManageList() {
  const { user } = useAuth();
  const [items, setItems] = useState<DocExerciseListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    try {
      const response = await api.listManageDocExercises({ page: 1, pageSize: 50 });
      setItems(response.exercises);
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  async function toggle(exercise: DocExerciseListItem) {
    const nextVisible = !exercise.visible;
    if (!nextVisible && !window.confirm(STOP_WARNING)) return;
    setBusy(exercise.id);
    try {
      await api.setDocExerciseVisibility(exercise.id, nextVisible);
      await load();
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "操作失败");
    } finally {
      setBusy(null);
    }
  }

  async function hardDelete(exercise: DocExerciseListItem) {
    if (user?.role === "TEACHER" && exercise.submissionCount > 0) {
      window.alert(TEACHER_BLOCKED);
      return;
    }
    const danger = user?.role === "ADMIN" && exercise.submissionCount > 0 ? "危险操作：该练习已有学生文档和评分。\n\n" : "";
    if (!window.confirm(`${danger}${DELETE_WARNING}\n\n确认彻底删除“${exercise.title}”吗？`)) return;
    setBusy(exercise.id);
    try {
      await api.deleteDocExercise(exercise.id);
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
          <h1 className="text-2xl font-bold">Office 排版练习管理</h1>
          <div className="mt-2 flex gap-3 text-sm">
            <Link className="text-zinc-500 hover:text-zinc-900" to="/admin/problems">算法题</Link>
            <Link className="text-zinc-500 hover:text-zinc-900" to="/admin/office">Office 选择题</Link>
            <Link className="font-medium text-zinc-900" to="/admin/office-doc">Office 排版练习</Link>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild><Link to="/admin/office-doc/review-list"><ClipboardCheck className="mr-1 h-4 w-4" />复核提交</Link></Button>
          <Button size="sm" asChild><Link to="/admin/office-doc/new"><Plus className="mr-1 h-4 w-4" />新建练习</Link></Button>
        </div>
      </div>
      {error && <div className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div>}
      <Card className="overflow-x-auto">
        <table className="w-full min-w-[960px] text-sm">
          <thead className="bg-zinc-50 text-left text-xs text-zinc-500"><tr><th className="px-4 py-3">名称</th><th className="px-4 py-3">创建者</th><th className="px-4 py-3">状态</th><th className="px-4 py-3">提交</th><th className="px-4 py-3">创建时间</th><th className="px-4 py-3">操作</th></tr></thead>
          <tbody className="divide-y">
            {loading ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-zinc-400" /></td></tr>
            ) : items.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center text-zinc-400">暂无可管理练习</td></tr>
            ) : items.map((exercise) => (
              <tr key={exercise.id} className="hover:bg-zinc-50">
                <td className="px-4 py-3"><Link className="font-medium hover:underline" to={`/office/docs/${exercise.id}`}>{exercise.title}</Link><div className="mt-1 text-xs text-zinc-400">{exercise.hasTeacherDoc ? "已上传参考文档" : "未上传参考文档"}</div></td>
                <td className="px-4 py-3">{exercise.createdBy == null ? "系统预置" : (exercise.creatorUsername ?? `用户 #${exercise.createdBy}`)}</td>
                <td className="px-4 py-3">{exercise.visible ? "已启用" : "已停用"}</td>
                <td className="px-4 py-3">{exercise.submissionCount}</td>
                <td className="px-4 py-3 text-xs text-zinc-500">{new Date(exercise.createdAt).toLocaleString("zh-CN", { hour12: false })}</td>
                <td className="px-4 py-3"><div className="flex gap-1">
                  <Button variant="outline" size="sm" asChild><Link to={`/admin/office-doc/${exercise.id}/edit`}><Pencil className="mr-1 h-3.5 w-3.5" />编辑</Link></Button>
                  <Button variant="outline" size="sm" disabled={busy !== null} onClick={() => void toggle(exercise)}>{exercise.visible ? <PowerOff className="mr-1 h-3.5 w-3.5" /> : <Power className="mr-1 h-3.5 w-3.5" />}{exercise.visible ? "停用" : "启用"}</Button>
                  <Button variant="destructive" size="sm" disabled={busy !== null} onClick={() => void hardDelete(exercise)}><Trash2 className="mr-1 h-3.5 w-3.5" />彻底删除</Button>
                </div></td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
