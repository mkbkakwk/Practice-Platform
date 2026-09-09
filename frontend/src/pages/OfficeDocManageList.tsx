import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type DocExerciseListItem, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { AdminTable } from "@/components/admin/AdminTable";
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
    <div className="admin-page">
      <header className="management-hero"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="management-kicker">Document workflow</p><h1 className="management-title">Office 排版练习</h1><p className="management-summary">围绕文档资源、学生提交和复核组织现有练习；不改变 DOCX 工作流。</p></div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild><Link to="/admin/office-doc/review-list"><ClipboardCheck className="mr-1 h-4 w-4" />复核提交</Link></Button>
          <Button size="sm" asChild><Link to="/admin/office-doc/new"><Plus className="mr-1 h-4 w-4" />新建练习</Link></Button>
        </div>
      </div><div className="management-deck"><div className="management-deck-item"><p className="management-deck-label">当前列表</p><p className="management-deck-value">{loading ? "加载中" : `${items.length} 项`}</p></div><div className="management-deck-item"><p className="management-deck-label">参考文档就绪</p><p className="management-deck-value">{loading ? "—" : items.filter((item) => item.hasTeacherDoc).length}</p></div></div><nav className="management-section-nav" aria-label="Office 管理类型"><Link to="/admin/problems">算法题</Link><Link to="/admin/office">Office 选择题</Link><Link className="is-active" to="/admin/office-doc">Office 排版练习</Link></nav></header>
      {error && <div className="mb-4 rounded border border-danger/25 bg-danger/5 p-3 text-sm text-danger">{error}</div>}
      <section className="management-panel mt-6"><div className="management-toolbar"><div><h2 className="management-panel-title">练习与资源目录</h2><p className="management-panel-description">可快速确认练习是否已配置教师参考文档。</p></div></div><AdminTable label="Office 排版练习管理">
        <table className="w-full min-w-[960px] text-sm">
          <thead className="bg-surface text-left text-xs text-muted-foreground"><tr><th className="px-4 py-3">名称</th><th className="px-4 py-3">创建者</th><th className="px-4 py-3">状态</th><th className="px-4 py-3">提交</th><th className="px-4 py-3">创建时间</th><th className="px-4 py-3">操作</th></tr></thead>
          <tbody className="divide-y">
            {loading ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-muted-foreground" /></td></tr>
            ) : items.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">暂无可管理练习</td></tr>
            ) : items.map((exercise) => (
              <tr key={exercise.id} className="hover:bg-surface">
                <td className="px-4 py-3"><Link className="font-medium hover:underline" to={`/office/docs/${exercise.id}`}>{exercise.title}</Link><div className="mt-1 text-xs text-muted-foreground">{exercise.hasTeacherDoc ? "已上传参考文档" : "未上传参考文档"}</div></td>
                <td className="px-4 py-3">{exercise.createdBy == null ? "系统预置" : (exercise.creatorUsername ?? `用户 #${exercise.createdBy}`)}</td>
                <td className="px-4 py-3">{exercise.visible ? "已启用" : "已停用"}</td>
                <td className="px-4 py-3">{exercise.submissionCount}</td>
                <td className="px-4 py-3 text-xs text-muted-foreground">{new Date(exercise.createdAt).toLocaleString("zh-CN", { hour12: false })}</td>
                <td className="px-4 py-3"><div className="flex gap-1">
                  <Button variant="outline" size="sm" asChild><Link to={`/admin/office-doc/${exercise.id}/edit`}><Pencil className="mr-1 h-3.5 w-3.5" />编辑</Link></Button>
                  <Button variant="outline" size="sm" disabled={busy !== null} onClick={() => void toggle(exercise)}>{exercise.visible ? <PowerOff className="mr-1 h-3.5 w-3.5" /> : <Power className="mr-1 h-3.5 w-3.5" />}{exercise.visible ? "停用" : "启用"}</Button>
                  <Button variant="destructive" size="sm" disabled={busy !== null} onClick={() => void hardDelete(exercise)}><Trash2 className="mr-1 h-3.5 w-3.5" />彻底删除</Button>
                </div></td>
              </tr>
            ))}
          </tbody>
        </table>
      </AdminTable></section>
    </div>
  );
}
