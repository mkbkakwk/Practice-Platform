import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FileUp, Loader2, Send } from "lucide-react";
import { api, ApiError, type ContestDetail as ContestDetailModel, type ContestProblemItem, type LanguageDef, type Submission } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Markdown } from "@/components/Markdown";
import { VerdictBadge } from "@/lib/verdict";
import { PHASE_LABEL, formatTime } from "./ContestList";

export default function ContestDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const contestId = Number(id);
  const [detail, setDetail] = useState<ContestDetailModel | null>(null);
  const [languages, setLanguages] = useState<LanguageDef[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const reload = async () => {
    const response = await api.getContest(contestId);
    setDetail(response.detail);
  };
  useEffect(() => {
    let active = true;
    Promise.all([api.getContest(contestId), api.getLanguages()]).then(([contest, languageResult]) => {
      if (!active) return;
      setDetail(contest.detail);
      setLanguages(languageResult.languages);
    }).catch((exception) => active && setError(exception instanceof ApiError ? exception.message : "比赛加载失败"))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [contestId]);

  if (loading) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>;
  if (!detail) return <div className="p-8"><p role="alert" className="text-red-600">{error || "比赛不存在"}</p></div>;
  const contest = detail.contest;
  const canJoin = user?.role === "USER" && contest.status === "PUBLISHED" && contest.phase === "UPCOMING"
    && contest.accessType === "OPEN" && !contest.participant;
  return <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
    <div className="mb-5"><Link className="text-sm text-zinc-500 hover:underline" to="/contests">← 返回比赛</Link>
      <div className="mt-3 flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-2xl font-bold">{contest.title}</h1><p className="mt-1 text-sm text-zinc-500">{formatTime(contest.startAt)} — {formatTime(contest.endAt)}</p></div><span className="rounded bg-zinc-900 px-3 py-1 text-sm font-semibold text-white">{PHASE_LABEL[contest.phase]}</span></div>
    </div>
    {error && <p role="alert" className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <Card className="mb-4 p-5"><Markdown>{contest.description || "暂无比赛说明。"}</Markdown><div className="mt-3 flex flex-wrap gap-3 text-sm text-zinc-500"><span>{contest.accessType === "OPEN" ? "公开报名" : "邀请制"}</span><span>{contest.participant ? "已参赛" : "未参赛"}</span></div>
      {canJoin && <Button className="mt-4" onClick={async () => { try { await api.joinContest(contest.id); await reload(); } catch (exception) { setError(exception instanceof ApiError ? exception.message : "加入失败"); } }}>加入比赛</Button>}
      {contest.accessType === "INVITE_ONLY" && !contest.participant && user?.role === "USER" && <p className="mt-3 text-sm text-amber-700">邀请制比赛仅对受邀学生开放。</p>}
    </Card>
    {contest.phase === "UPCOMING" && <p className="mb-4 rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">比赛尚未开始；CONTEST_ONLY 题目正文将在服务端确认开赛且你是参赛者后开放。</p>}
    {contest.phase === "CANCELLED" && <p className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">比赛已取消，历史信息保留，但不能创建新提交。</p>}
    <h2 className="mb-3 text-lg font-semibold">比赛题目</h2>
    {detail.problems.length === 0 ? <Card className="p-8 text-center text-zinc-400">当前阶段没有可展示的题目</Card>
      : <div className="space-y-4">{detail.problems.map((problem) => <ContestProblem key={problem.contestProblemId} contestId={contest.id} phase={contest.phase} participant={contest.participant} problem={problem} languages={languages} onError={setError} />)}</div>}
  </div>;
}

function ContestProblem({ contestId, phase, participant, problem, languages, onError }: {
  contestId: number; phase: ContestDetailModel["contest"]["phase"]; participant: boolean;
  problem: ContestProblemItem; languages: LanguageDef[]; onError: (message: string) => void;
}) {
  const [language, setLanguage] = useState(languages[0]?.id ?? "python");
  const [code, setCode] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<Submission | null>(null);
  const [officeResult, setOfficeResult] = useState<string | null>(null);
  useEffect(() => { if (!language && languages[0]) setLanguage(languages[0].id); }, [language, languages]);
  const canSubmit = phase === "RUNNING" && participant;
  const content = problem.content as { description?: string; inputFmt?: string; outputFmt?: string; samples?: Array<{ input: string; output: string }> } | null;

  async function submitAlgorithm() {
    setSubmitting(true); onError("");
    try {
      const response = await api.submitContestAlgorithm(contestId, problem.contestProblemId, language, code);
      setResult(await api.pollSubmission(response.submissionId, { timeoutMs: 60000 }));
    } catch (exception) { onError(exception instanceof ApiError ? exception.message : "提交失败"); }
    finally { setSubmitting(false); }
  }
  async function submitOffice() {
    if (!file) return;
    setSubmitting(true); onError("");
    try {
      const response = await api.submitContestOffice(contestId, problem.contestProblemId, file);
      setOfficeResult(`${response.submission.status}${response.submission.score == null ? "" : ` · ${response.submission.score} 分`}`);
    } catch (exception) { onError(exception instanceof ApiError ? exception.message : "DOCX 提交失败"); }
    finally { setSubmitting(false); }
  }

  return <Card className="p-5" data-testid={`contest-problem-${problem.contestProblemId}`}>
    <div className="mb-3 flex items-center gap-2"><span className="rounded bg-zinc-900 px-2 py-1 text-xs font-bold text-white">{problem.label}</span><h3 className="font-semibold">{problem.title}</h3><span className="text-xs text-zinc-500">{problem.problemType === "ALGORITHM" ? "算法" : "DOCX"}</span></div>
    {content?.description && <Markdown>{content.description}</Markdown>}
    {content?.inputFmt && <div className="mt-3"><h4 className="text-sm font-semibold">输入</h4><Markdown>{content.inputFmt}</Markdown></div>}
    {content?.outputFmt && <div className="mt-3"><h4 className="text-sm font-semibold">输出</h4><Markdown>{content.outputFmt}</Markdown></div>}
    {canSubmit && problem.problemType === "ALGORITHM" && <div className="mt-4 space-y-3 border-t pt-4"><select aria-label={`${problem.label} 编程语言`} value={language} onChange={(event) => setLanguage(event.target.value)} className="h-9 rounded border px-3 text-sm">{languages.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select><textarea aria-label={`${problem.label} 源代码`} className="min-h-40 w-full rounded border p-3 font-mono text-sm" value={code} onChange={(event) => setCode(event.target.value)} /><Button disabled={submitting || !code.trim()} onClick={() => void submitAlgorithm()}><Send className="mr-1 h-4 w-4" />提交代码</Button>{result && <div className="flex items-center gap-2 text-sm"><span>提交 #{result.id}</span><VerdictBadge verdict={result.verdict} /></div>}</div>}
    {canSubmit && problem.problemType === "OFFICE" && <div className="mt-4 space-y-3 border-t pt-4"><input aria-label={`${problem.label} DOCX 文件`} type="file" accept=".docx" onChange={(event) => setFile(event.target.files?.[0] ?? null)} /><Button disabled={submitting || !file} onClick={() => void submitOffice()}><FileUp className="mr-1 h-4 w-4" />提交 DOCX</Button>{officeResult && <p className="text-sm text-green-700">判题结果：{officeResult}</p>}</div>}
    {!canSubmit && <p className="mt-3 text-xs text-zinc-500">{phase === "ENDED" ? "比赛已结束；历史题目仍可查看。" : "仅参赛者可在比赛进行中提交。"}</p>}
  </Card>;
}
