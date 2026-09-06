import { useEffect, useState, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { api, type DocExerciseDetail, type StudentDocSubmission } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Markdown } from "@/components/Markdown";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, Upload, Download, ArrowLeft } from "lucide-react";
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
  PENDING: "border-info/25 bg-info/10 text-info",
  JUDGING: "border-info/25 bg-info/10 text-info",
  COMPLETED: "border-success/25 bg-success/10 text-success",
  FAILED: "border-danger/25 bg-danger/10 text-danger",
  AUTO_CHECKED: "border-success/25 bg-success/10 text-success",
  NEEDS_REVIEW: "border-warning/25 bg-warning/10 text-warning",
  REVIEWED: "border-info/25 bg-info/10 text-info",
};

export default function OfficeDocExerciseDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const exerciseId = Number(id);
  const fileRef = useRef<HTMLInputElement>(null);
  const starterDownloadRef = useRef(false);

  const [exercise, setExercise] = useState<DocExerciseDetail | null>(null);
  const [submission, setSubmission] = useState<StudentDocSubmission | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [downloadingStarter, setDownloadingStarter] = useState(false);
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

  async function handleStarterDownload() {
    if (!exercise?.starterDocName || starterDownloadRef.current) return;
    starterDownloadRef.current = true;
    setDownloadingStarter(true);
    setError(null);
    try {
      await api.downloadStarterDoc(exerciseId, exercise.starterDocName);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "下载起始文档失败");
    } finally {
      starterDownloadRef.current = false;
      setDownloadingStarter(false);
    }
  }

  if (loading) {
    return <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div>;
  }
  if (!exercise) {
    return <div className="px-4 py-6"><Card className="p-8 text-center text-muted-foreground">{error || "练习不存在"}</Card></div>;
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6 lg:px-8">
      <Link to="/office/docs" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 返回列表
      </Link>

      <h1 className="mb-2 text-2xl font-semibold tracking-tight">{exercise.title}</h1>
      <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
        <span>{exercise.teacherDocName ? "参考文档已上传" : "暂无参考文档"}</span>
        <span>创建者：{exercise.createdBy == null ? "系统预置" : (exercise.creatorUsername ?? "未知")}</span>
        <span>创建于 {new Date(exercise.createdAt).toLocaleString("zh-CN", { hour12: false })}</span>
      </div>

      {/* Requirements */}
      <Card className="mb-4 gap-3 p-5">
        <h2 className="mb-3 text-sm font-semibold text-subtle">排版要求</h2>
        <div className="min-w-0">
          <Markdown>{exercise.description}</Markdown>
        </div>
        {exercise.starterDocName && (
          <div className="mt-4 border-t pt-3">
            <h3 className="mb-2 text-sm font-semibold text-subtle">① 下载待修改文件</h3>
            <Button variant="outline" size="sm" disabled={downloadingStarter} onClick={() => void handleStarterDownload()}>
              {downloadingStarter ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Download className="mr-1 h-4 w-4" />}
              {downloadingStarter ? "下载中..." : `下载 ${exercise.starterDocName}`}
            </Button>
            <p className="mt-3 text-sm text-subtle">② 在本地 Word / WPS 中按上述要求修改文件</p>
          </div>
        )}
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
      <Card className="mb-4 gap-3 bg-surface p-5">
        <h2 className="mb-3 text-sm font-semibold text-subtle">③ 上传修改后的 DOCX</h2>
        <p className="mb-3 text-xs text-muted-foreground">请上传 .docx 格式的 Word 文档（最大 10MB）</p>
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
        <Button className="max-w-full self-start whitespace-normal" onClick={() => fileRef.current?.click()} disabled={uploading || !exercise.teacherDocName || !exercise.starterDocName}>
          {uploading ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Upload className="mr-1 h-4 w-4" />}
          {uploading ? "上传中..." : "选择 .docx 文件上传"}
        </Button>
        {(!exercise.teacherDocName || !exercise.starterDocName) && (
          <p className="mt-2 text-xs text-danger">练习尚未同时准备起始文档和参考文档，暂无法提交</p>
        )}
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
      </Card>

      {/* Results */}
      {submission && (
        <Card className="gap-3 p-5">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
            <h2 className="text-sm font-semibold text-subtle">比对结果</h2>
            <div className="flex flex-wrap items-center gap-3">
              <span className={cn("rounded-md border px-2 py-1 text-xs font-medium whitespace-nowrap", STATUS_CLASS[submission.status])}>
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
            <div className="mb-4 rounded-md border border-info/25 bg-info/10 p-4">
              <div className="flex items-center gap-4">
                <span className="pilot-numeric text-2xl font-semibold text-foreground">{submission.score}</span>
                <span className="text-sm text-subtle">分</span>
              </div>
              {submission.status === "REVIEWED" && submission.teacherComment && (
                <p className="mt-2 text-sm text-subtle"><span className="font-medium">老师评语：</span>{submission.teacherComment}</p>
              )}
            </div>
          )}

          {submission.status === "FAILED" && (
            <div className="mb-4 rounded-md border border-danger/25 bg-danger/10 p-4 text-sm text-danger">
              文档未能完成判题：{failureLabel(submission.errorCategory)}。请检查文件后重新上传。
            </div>
          )}

          {submission.resultDetail && (
            <div className="mb-4 rounded-md border border-border p-4">
              <div className="mb-3 flex flex-wrap items-baseline gap-3">
                <h3 className="text-sm font-medium text-subtle">安全判题反馈</h3>
                <span className="text-sm text-subtle">
                  {submission.resultDetail.earnedScore} / {submission.resultDetail.totalScore}
                </span>
                <span className="text-xs text-muted-foreground">
                  {submission.resultDetail.totalErrorCount} 项差异
                </span>
              </div>
              <ul className="space-y-2 text-sm text-subtle">
                {submission.resultDetail.items.slice(0, 20).map((item) => (
                  <li key={item.ruleId} className="rounded bg-surface p-3">
                    <p className="font-medium text-subtle">{item.target}：{item.message}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      你的结果：{formatVal(item.actual)} · 要求：{formatVal(item.expected)}
                      <span className="ml-2">得分 {item.earned}/{item.score}</span>
                    </p>
                  </li>
                ))}
              </ul>
              {submission.resultDetail.truncated && (
                <p className="mt-2 text-xs text-muted-foreground">
                  仅显示部分错误，共 {submission.resultDetail.totalErrorCount} 项。
                </p>
              )}
            </div>
          )}

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
