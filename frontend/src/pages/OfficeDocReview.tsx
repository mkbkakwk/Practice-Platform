import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { api, type ReviewerDocSubmission, type DocCompareRow, type DocExerciseDetail } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Download, ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { ADMIN_DOC_STATUS_CLASS } from "@/components/admin/presentation";

const STATUS_LABEL: Record<string, string> = {
  PENDING: "等待判题",
  JUDGING: "判题中",
  COMPLETED: "自动判题完成",
  FAILED: "判题失败",
  AUTO_CHECKED: "自动检查通过",
  NEEDS_REVIEW: "待复核",
  REVIEWED: "已复核",
};

export default function OfficeDocReview() {
  const { id } = useParams<{ id: string }>();
  const submissionId = Number(id);

  const [submission, setSubmission] = useState<ReviewerDocSubmission | null>(null);
  const [exercise, setExercise] = useState<DocExerciseDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [score, setScore] = useState<number>(80);
  const [comment, setComment] = useState("");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!Number.isFinite(submissionId)) return;
    let active = true;
    setLoading(true);
    api.getDocSubmissionForReview(submissionId).then(({ submission: sub }) => {
      if (!active) return;
      setSubmission(sub);
      if (sub.score != null) setScore(sub.score);
      if (sub.teacherComment) setComment(sub.teacherComment);
      return api.getDocExercise(sub.exerciseId).then(({ exercise: ex }) => active && setExercise(ex));
    }).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [submissionId]);

  async function handleReview() {
    if (!submission) return;
    setSaving(true);
    setSaved(false);
    try {
      const { submission: updated } = await api.reviewDocSubmission(submission.id, score, comment);
      setSubmission(updated);
      setSaved(true);
    } finally {
      setSaving(false);
    }
  }

  const compareRows: DocCompareRow[] = submission?.compareResult ? safeParse(submission.compareResult, []) : [];
  const matchPercent = compareRows.length > 0
    ? Math.round(compareRows.filter((r) => r.match).length * 100 / compareRows.length)
    : 0;

  if (loading) return <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  if (!submission) return <div className="px-4 py-6"><Card className="p-8 text-center text-muted-foreground">提交记录不存在</Card></div>;

  return (
    <div className="admin-page max-w-5xl">
      <Link to="/office/docs" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 返回
      </Link>

      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">文档复核</h1>
        <span className={cn("rounded-md border px-2 py-1 text-xs font-medium", ADMIN_DOC_STATUS_CLASS[submission.status])}>
          {STATUS_LABEL[submission.status]}
        </span>
      </div>

      {/* Info */}
      <Card className="mb-4 p-4">
        <div className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div><span className="text-muted-foreground">练习：</span>{exercise?.title ?? `#${submission.exerciseId}`}</div>
          <div><span className="text-muted-foreground">用户ID：</span>{submission.userId}</div>
          <div><span className="text-muted-foreground">文件：</span>{submission.studentDocName}</div>
          <div><span className="text-muted-foreground">提交时间：</span>{submission.createdAt}</div>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span className="text-sm text-muted-foreground">自动匹配率：</span>
          <span className={cn("font-bold", matchPercent === 100 ? "text-success" : "text-warning")}>{matchPercent}%</span>
          <span onClick={() => void api.downloadStudentDoc(submission.id, submission.studentDocName)}>
            <Button variant="outline" size="sm"><Download className="mr-1 h-4 w-4" /> 下载学生文档</Button>
          </span>
          {exercise && exercise.teacherDocName && (
            <span onClick={() => void api.downloadTeacherDoc(exercise.id, exercise.teacherDocName!)}>
              <Button variant="outline" size="sm"><Download className="mr-1 h-4 w-4" /> 下载参考文档</Button>
            </span>
          )}
        </div>
      </Card>

      {/* Comparison detail */}
      <Card className="mb-4 p-4">
        <h2 className="mb-3 text-sm font-semibold text-secondary-foreground">格式比对详情</h2>
        <div className="space-y-2">
          {compareRows.map((row) => (
            <div key={row.index} className={cn("rounded-md border-l-2 p-3 text-sm", row.match ? "border-success/25 bg-surface" : "border-danger/25 bg-surface")}>
              <div className="mb-1 flex items-center gap-2">
                {row.match ? <CheckCircle2 className="h-4 w-4 text-success" /> : <XCircle className="h-4 w-4 text-danger" />}
                <span className="text-xs text-muted-foreground">第 {row.index + 1} 段</span>
                <span className="ml-auto text-xs text-muted-foreground">{row.match ? "全部匹配" : `${row.diffs.filter((d) => !d.match).length} 项不符`}</span>
              </div>
              <div className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
                <span className="text-muted-foreground">学生：{row.studentText?.slice(0, 40) || "(空)"}</span>
                <span className="text-muted-foreground">老师：{row.teacherText?.slice(0, 40) || "(空)"}</span>
              </div>
              {!row.match && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {row.diffs.filter((d) => !d.match).map((d, i) => (
                    <span key={i} className="rounded bg-surface px-1.5 py-0.5 text-xs text-danger">
                      {d.label}: {fmt(d.student)} → {fmt(d.teacher)}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </Card>

      {/* Review form */}
      <Card className="p-5">
        <h2 className="mb-3 text-sm font-semibold text-secondary-foreground">人工复核打分</h2>
        <div className="mb-4">
          <Label htmlFor="admin-score" className="mb-1.5 block text-xs">分数（0-100）</Label>
          <Input id="admin-score" type="number" min={0} max={100} value={score} onChange={(e) => setScore(Number(e.target.value))} className="w-32" />
        </div>
        <div className="mb-4">
          <Label htmlFor="admin-comment" className="mb-1.5 block text-xs">评语</Label>
          <textarea id="admin-comment"
            className="min-h-20 w-full rounded-md border border-border p-3 text-sm"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="给学生反馈..."
          />
        </div>
        <div className="flex items-center gap-3">
          <Button onClick={handleReview} disabled={saving}>
            {saving && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
            保存复核结果
          </Button>
          {saved && <span className="text-sm text-success">✓ 已保存</span>}
        </div>
      </Card>
    </div>
  );
}

function fmt(v: unknown): string {
  if (v === null || v === undefined || v === "") return "未设置";
  if (v === true) return "是";
  if (v === false) return "否";
  if (typeof v === "number") return v === 0 ? "未设置" : String(v);
  return String(v);
}

function safeParse<T>(json: string, fallback: T): T {
  try { return JSON.parse(json) as T; } catch { return fallback; }
}
