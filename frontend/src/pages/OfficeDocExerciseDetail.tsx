import { useEffect, useState, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { api, type DocExerciseDetail, type DocSubmission, type DocCompareRow } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Markdown } from "@/components/Markdown";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, Upload, Download, CheckCircle2, XCircle, ArrowLeft, FileText } from "lucide-react";
import { cn } from "@/lib/utils";

const STATUS_LABEL: Record<string, string> = {
  PENDING: "等待判题",
  JUDGING: "判题中",
  COMPLETED: "自动判题完成",
  FAILED: "判题失败",
  AUTO_CHECKED: "自动检查通过",
  NEEDS_REVIEW: "待老师复核",
  REVIEWED: "已复核",
};
const STATUS_CLASS: Record<string, string> = {
  PENDING: "bg-zinc-100 text-zinc-700",
  JUDGING: "bg-blue-100 text-blue-700",
  COMPLETED: "bg-green-100 text-green-700",
  FAILED: "bg-red-100 text-red-700",
  AUTO_CHECKED: "bg-green-100 text-green-700",
  NEEDS_REVIEW: "bg-yellow-100 text-yellow-700",
  REVIEWED: "bg-blue-100 text-blue-700",
};

export default function OfficeDocExerciseDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const exerciseId = Number(id);
  const fileRef = useRef<HTMLInputElement>(null);

  const [exercise, setExercise] = useState<DocExerciseDetail | null>(null);
  const [submission, setSubmission] = useState<DocSubmission | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isFinite(exerciseId)) return;
    let active = true;
    setLoading(true);
    Promise.all([
      api.getDocExercise(exerciseId),
      api.listDocSubmissions({ exerciseId, pageSize: 1 }).then((d) =>
        d.submissions.length > 0 ? api.getDocSubmission(d.submissions[0].id) : null,
      ),
    ]).then(([ex, sub]) => {
      if (!active) return;
      setExercise(ex.exercise);
      setSubmission(sub?.submission ?? null);
    }).catch((e: Error) => active && setError(e.message))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [exerciseId]);

  async function handleUpload(file: File) {
    setUploading(true);
    setError(null);
    try {
      const { submission: sub } = await api.submitDocExercise(exerciseId, file);
      setSubmission(sub);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "上传失败");
    } finally {
      setUploading(false);
    }
  }

  const compareRows: DocCompareRow[] = submission?.compareResult ? safeParse(submission.compareResult, []) : [];
  const matchPercent = compareRows.length > 0
    ? Math.round(compareRows.filter((r) => r.match).length * 100 / compareRows.length)
    : 0;

  if (loading) {
    return <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-zinc-400" /></div>;
  }
  if (!exercise) {
    return <div className="px-4 py-6"><Card className="p-8 text-center text-zinc-500">{error || "练习不存在"}</Card></div>;
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 lg:px-8">
      <Link to="/office/docs" className="mb-4 inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-800">
        <ArrowLeft className="h-4 w-4" /> 返回列表
      </Link>

      <h1 className="mb-2 text-2xl font-bold">{exercise.title}</h1>
      <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-zinc-500">
        <span>{exercise.teacherDocName ? "参考文档已上传" : "暂无参考文档"}</span>
        <span>创建者：{exercise.createdBy == null ? "系统预置" : (exercise.creatorUsername ?? "未知")}</span>
        <span>创建于 {new Date(exercise.createdAt).toLocaleString("zh-CN", { hour12: false })}</span>
      </div>

      {/* Requirements */}
      <Card className="mb-4 p-5">
        <h2 className="mb-3 text-sm font-semibold text-zinc-700">排版要求</h2>
        <div className="prose prose-sm max-w-none">
          <Markdown>{exercise.description}</Markdown>
        </div>
        {exercise.teacherDocName && (user?.role === "TEACHER" || user?.role === "ADMIN") && (
          <div className="mt-4 border-t pt-3">
            <span onClick={() => void api.downloadTeacherDoc(exerciseId, exercise.teacherDocName!)}>
              <Button variant="outline" size="sm">
                <Download className="mr-1 h-4 w-4" /> 下载老师参考文档
              </Button>
            </span>
          </div>
        )}
      </Card>

      {/* Upload area */}
      <Card className="mb-4 p-5">
        <h2 className="mb-3 text-sm font-semibold text-zinc-700">上传你的文档</h2>
        <p className="mb-3 text-xs text-zinc-500">请上传 .docx 格式的 Word 文档（最大 10MB）</p>
        <input
          ref={fileRef}
          type="file"
          accept=".docx"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) handleUpload(f);
            e.target.value = "";
          }}
        />
        <Button onClick={() => fileRef.current?.click()} disabled={uploading || !exercise.teacherDocName}>
          {uploading ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Upload className="mr-1 h-4 w-4" />}
          {uploading ? "上传中..." : "选择 .docx 文件上传"}
        </Button>
        {!exercise.teacherDocName && (
          <p className="mt-2 text-xs text-red-500">老师尚未上传参考文档，暂无法提交</p>
        )}
        {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      </Card>

      {/* Results */}
      {submission && (
        <Card className="p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-zinc-700">比对结果</h2>
            <div className="flex items-center gap-3">
              <span className={cn("rounded-md px-2 py-1 text-xs font-medium whitespace-nowrap", STATUS_CLASS[submission.status])}>
                {STATUS_LABEL[submission.status]}
              </span>
              {(user?.role === "TEACHER" || user?.role === "ADMIN") && (
                <Button variant="outline" size="sm" asChild>
                  <Link to={`/admin/office-doc/review/${submission.id}`}>复核</Link>
                </Button>
              )}
            </div>
          </div>

          {/* Score */}
          {submission.score != null && (
            <div className="mb-4 rounded-md border border-blue-200 bg-blue-50 p-4">
              <div className="flex items-center gap-4">
                <span className="text-2xl font-bold text-blue-700">{submission.score}</span>
                <span className="text-sm text-zinc-600">分</span>
              </div>
              {submission.status === "REVIEWED" && submission.teacherComment && (
                <p className="mt-2 text-sm text-zinc-700"><span className="font-medium">老师评语：</span>{submission.teacherComment}</p>
              )}
            </div>
          )}

          {submission.status === "FAILED" && (
            <div className="mb-4 rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700">
              文档未能完成判题：{failureLabel(submission.errorCategory)}。请检查文件后重新上传。
            </div>
          )}

          {submission.resultDetail?.items?.length > 0 && (
            <div className="mb-4 rounded-md border border-zinc-200 p-4">
              <h3 className="mb-2 text-sm font-medium text-zinc-700">主要扣分项</h3>
              <ul className="space-y-1 text-sm text-zinc-600">
                {submission.resultDetail.items.slice(0, 20).map((item) => (
                  <li key={item.ruleId}>• {item.target}：{item.message}（{item.earned}/{item.score}）</li>
                ))}
              </ul>
              {submission.resultDetail.truncated && (
                <p className="mt-2 text-xs text-zinc-500">
                  仅显示部分错误，共 {submission.resultDetail.totalErrorCount} 项。
                </p>
              )}
            </div>
          )}

          {/* Auto match summary */}
          <div className="mb-4 flex items-center gap-2 rounded-md bg-zinc-50 p-3 text-sm">
            <FileText className="h-4 w-4 text-zinc-400" />
            <span>自动比对匹配率：</span>
            <span className={cn("font-bold", matchPercent === 100 ? "text-green-600" : "text-orange-600")}>{matchPercent}%</span>
            <span className="text-zinc-400">（{compareRows.filter((r) => r.match).length}/{compareRows.length} 段匹配）</span>
          </div>

          {/* Per-paragraph comparison */}
          <div className="space-y-3">
            {compareRows.map((row) => (
              <div key={row.index} className={cn("rounded-lg border p-3", row.match ? "border-green-200 bg-green-50/50" : "border-red-200 bg-red-50/50")}>
                <div className="mb-2 flex items-center gap-2">
                  {row.match ? <CheckCircle2 className="h-4 w-4 text-green-600" /> : <XCircle className="h-4 w-4 text-red-600" />}
                  <span className="text-xs font-medium text-zinc-500">第 {row.index + 1} 段</span>
                </div>
                <div className="mb-2 grid grid-cols-2 gap-2 text-sm">
                  <div className="rounded bg-white/60 p-2">
                    <span className="text-xs text-zinc-400">你的文档：</span>
                    <p className="line-clamp-2 text-zinc-700">{row.studentText || "(空)"}</p>
                  </div>
                  <div className="rounded bg-white/60 p-2">
                    <span className="text-xs text-zinc-400">老师文档：</span>
                    <p className="line-clamp-2 text-zinc-700">{row.teacherText || "(空)"}</p>
                  </div>
                </div>
                {!row.match && (
                  <div className="flex flex-wrap gap-1.5">
                    {row.diffs.filter((d) => !d.match).map((d, i) => (
                      <span key={i} className="rounded border border-red-200 bg-white px-2 py-0.5 text-xs text-red-700">
                        {d.label}：你={formatVal(d.student)} / 老师={formatVal(d.teacher)}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Download student doc */}
          <div className="mt-4 border-t pt-3">
            <span onClick={() => void api.downloadStudentDoc(submission.id, submission.studentDocName)}>
              <Button variant="outline" size="sm">
                <Download className="mr-1 h-4 w-4" /> 下载我上传的文档
              </Button>
            </span>
          </div>
        </Card>
      )}
    </div>
  );
}

function formatVal(v: unknown): string {
  if (v === null || v === undefined || v === "") return "未设置";
  if (v === true) return "是";
  if (v === false) return "否";
  if (typeof v === "number") return v === 0 ? "未设置" : String(v);
  return String(v);
}

function safeParse<T>(json: string, fallback: T): T {
  try { return JSON.parse(json) as T; } catch { return fallback; }
}

function failureLabel(category: string | null): string {
  const labels: Record<string, string> = {
    INVALID_FILE_TYPE: "文件类型不受支持",
    FILE_TOO_LARGE: "文件超过大小限制",
    INVALID_DOCUMENT: "文档格式无效或已损坏",
    UNSUPPORTED_DOCUMENT: "文档包含不支持的内容",
    PASSWORD_PROTECTED: "暂不支持密码保护文档",
    PARSING_FAILED: "文档无法解析",
  };
  return category ? (labels[category] ?? "文档判题失败") : "文档判题失败";
}
