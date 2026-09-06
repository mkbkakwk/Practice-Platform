import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type DocExerciseListItem } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { DIFFICULTY_CLASS, DIFFICULTY_LABEL } from "@/lib/verdict";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, FileText, Upload, Plus } from "lucide-react";
import { cn } from "@/lib/utils";

export default function OfficeDocList() {
  const { user } = useAuth();
  const [exercises, setExercises] = useState<DocExerciseListItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api.listDocExercises({ pageSize: 50 }).then((data) => {
      if (active) setExercises(data.exercises);
    }).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  return (
    <div className="pilot-page">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">排版练习（文档上传）</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            按要求排版 Word 文档后上传，系统自动解析格式并与老师参考文档比对
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link to="/office">← 选择题练习</Link>
          </Button>
          {(user?.role === "TEACHER" || user?.role === "ADMIN") && (
            <>
              <Button variant="outline" size="sm" asChild>
                <Link to="/admin/office-doc/review-list">复核列表</Link>
              </Button>
              <Button size="sm" asChild>
                <Link to="/admin/office-doc/new">
                  <Plus className="mr-1 h-4 w-4" /> 新建练习
                </Link>
              </Button>
            </>
          )}
        </div>
      </div>

      <div className="mb-4 flex items-center gap-2 rounded-md border border-info/25 bg-info/10 p-3 text-sm text-info">
        <Upload className="h-4 w-4 shrink-0" />
        <span>流程：下载要求 → 在 Word 中排版 → 上传 .docx → 系统自动比对 → 老师复核</span>
      </div>

      {loading ? (
        <div className="flex justify-center py-20">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : exercises.length === 0 ? (
        <Card className="p-12 text-center text-muted-foreground">暂无排版练习</Card>
      ) : (
        <div className="grid gap-3">
          {exercises.map((ex) => (
            <Card key={ex.id} className="flex flex-col items-start gap-4 p-5 transition-colors duration-150 hover:bg-elevated sm:flex-row sm:items-center">
              <FileText className="h-8 w-8 shrink-0 text-info" />
              <div className="min-w-0 flex-1">
                <Link to={`/office/docs/${ex.id}`} className="font-medium text-foreground hover:underline">
                  {ex.title}
                </Link>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <span className={cn("inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap", DIFFICULTY_CLASS[ex.difficulty])}>
                    {DIFFICULTY_LABEL[ex.difficulty]}
                  </span>
                  {ex.hasTeacherDoc ? (
                    <span className="text-xs text-success">✓ 已上传参考文档</span>
                  ) : (
                    <span className="text-xs text-muted-foreground">未上传参考文档</span>
                  )}
                  {!ex.visible && <span className="text-xs text-muted-foreground">（隐藏）</span>}
                  <span className="text-xs text-muted-foreground">创建者：{ex.createdBy == null ? "系统预置" : (ex.creatorUsername ?? "未知")}</span>
                  <span className="text-xs text-muted-foreground">{new Date(ex.createdAt).toLocaleString("zh-CN", { hour12: false })}</span>
                </div>
              </div>
              <Button size="sm" asChild>
                <Link to={`/office/docs/${ex.id}`}>进入练习</Link>
              </Button>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
