import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { api, type DocSubmission, type DocCompareRow, type DocExerciseDetail } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Download, ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";

const STATUS_LABEL: Record<string, string> = {
  AUTO_CHECKED: "自动检查通过",
  NEEDS_REVIEW: "待复核",
  REVIEWED: "已复核",
};

export default function OfficeDocReview() {
  const { id } = useParams<{ id: string }>();
  const submissionId = Number(id);

  const [submission, setSubmission] = useState<DocSubmission | null>(null);
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
    api.getDocSubmission(submissionId).then(({ submission: sub }) => {
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

  if (loading) return <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-zinc-400" /></div>;
  if (!submission) return <div className="px-4 py-6"><Card className="p-8 text-center text-zinc-500">提交记录不存在</Card></div>;

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 lg:px-8">
      <Link to="/office/docs" className="mb-4 inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-800">
        <ArrowLeft className="h-4 w-4" /> 返回
      </Link>

      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">文档复核</h1>
        <span className={cn("rounded-md px-2 py-1 text-xs font-medium", submission.status === "REVIEWED" ? "bg-blue-100 text-blue-700" : "bg-yellow-100 text-yellow-700")}>
          {STATUS_LABEL[submission.status]}
        </span>
      </div>

      {/* Info */}
      <Card className="mb-4 p-4">
        <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
          <div><span className="text-zinc-400">练习：</span>{exercise?.title ?? `#${submission.exerciseId}`}</div>
          <div><span className="text-zinc-400">用户ID：</span>{submission.userId}</div>
          <div><span className="text-zinc-400">文件：</span>{submission.studentDocName}</div>
          <div><span className="text-zinc-400">提交时间：</span>{submission.createdAt}</div>
        </div>
        <div className="mt-3 flex items-center gap-3">
          <span className="text-sm text-zinc-500">自动匹配率：</span>
          <span className={cn("font-bold", matchPercent === 100 ? "text-green-600" : "text-orange-600")}>{matchPercent}%</span>
          <a href={api.studentDocUrl(submission.id)} target="_blank" rel="noreferrer">
            <Button variant="outline" size="sm"><Download className="mr-1 h-4 w-4" /> 下载学生文档</Button>
          </a>
          {exercise && exercise.teacherDocName && (
            <a href={api.teacherDocUrl(exercise.id)} target="_blank" rel="noreferrer">
              <Button variant="outline" size="sm"><Download className="mr-1 h-4 w-4" /> 下载参考文档</Button>
            </a>
          )}
        </div>
      </Card>

      {/* Comparison detail */}
      <Card className="mb-4 p-4">
        <h2 className="mb-3 text-sm font-semibold text-zinc-700">格式比对详情</h2>
        <div className="space-y-2">
          {compareRows.map((row) => (
            <div key={row.index} className={cn("rounded-lg border p-3 text-sm", row.match ? "border-green-200 bg-green-50/50" : "border-red-200 bg-red-50/50")}>
              <div className="mb-1 flex items-center gap-2">
                {row.match ? <CheckCircle2 className="h-4 w-4 text-green-600" /> : <XCircle className="h-4 w-4 text-red-600" />}
                <span className="text-xs text-zinc-500">第 {row.index + 1} 段</span>
                <span className="ml-auto text-xs text-zinc-400">{row.match ? "全部匹配" : `${row.diffs.filter((d) => !d.match).length} 项不符`}</span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <span className="text-zinc-500">学生：{row.studentText?.slice(0, 40) || "(空)"}</span>
                <span className="text-zinc-500">老师：{row.teacherText?.slice(0, 40) || "(空)"}</span>
              </div>
              {!row.match && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {row.diffs.filter((d) => !d.match).map((d, i) => (
                    <span key={i} className="rounded bg-white px-1.5 py-0.5 text-xs text-red-700">
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
        <h2 className="mb-3 text-sm font-semibold text-zinc-700">人工复核打分</h2>
        <div className="mb-4">
          <Label className="mb-1.5 block text-xs">分数（0-100）</Label>
          <Input type="number" min={0} max={100} value={score} onChange={(e) => setScore(Number(e.target.value))} className="w-32" />
        </div>
        <div className="mb-4">
          <Label className="mb-1.5 block text-xs">评语</Label>
          <textarea
            className="min-h-20 w-full rounded-md border border-zinc-200 p-3 text-sm"
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
          {saved && <span className="text-sm text-green-600">✓ 已保存</span>}
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
