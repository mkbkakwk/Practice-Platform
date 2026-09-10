import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Download, FileUp, Loader2, Send } from "lucide-react";
import { toast } from "sonner";
import {
  api,
  getApiErrorMessage,
  isAbortError,
  type ContestDetail as ContestDetailModel,
  type ContestProblemItem,
  type ContestChoiceSubmission,
  type StudentDocSubmission,
  type LanguageDef,
  type Submission,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { CodeEditor } from "@/components/CodeEditor";
import { OfficeJudgeResult } from "@/components/OfficeJudgeResult";
import { SubmissionResultCard } from "@/components/SubmissionResultCard";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Markdown } from "@/components/Markdown";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PHASE_LABEL, formatTime, phaseClass } from "./ContestList";
import { cn } from "@/lib/utils";

export const CONTEST_REFRESH_MS = 15_000;
const DOCX_MAX_BYTES = 10 * 1024 * 1024;

interface ProblemDraft {
  language: string;
  codeByLanguage: Record<string, string>;
}

interface ProblemRunState {
  busy: "submitting" | "polling" | "downloading" | null;
  pollCount: number;
  submission?: Submission;
  officeSubmission?: StudentDocSubmission;
  choiceSubmission?: ContestChoiceSubmission;
  notice?: string;
  error?: string;
}

export default function ContestDetail() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const contestId = Number(id);
  const [searchParams, setSearchParams] = useSearchParams();
  const [detail, setDetail] = useState<ContestDetailModel | null>(null);
  const [languages, setLanguages] = useState<LanguageDef[]>([]);
  const [languageError, setLanguageError] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [joining, setJoining] = useState(false);
  const [drafts, setDrafts] = useState<Record<number, ProblemDraft>>({});
  const [runs, setRuns] = useState<Record<number, ProblemRunState>>({});
  const [files, setFiles] = useState<Record<number, File | null>>({});
  const [choiceSelections, setChoiceSelections] = useState<Record<number, string[]>>({});
  const [now, setNow] = useState(() => Date.now());
  const joiningRef = useRef(false);
  const phaseRef = useRef<ContestDetailModel["contest"]["phase"] | null>(null);
  const boundaryRefreshRef = useRef<number | null>(null);
  const controllersRef = useRef<Record<number, AbortController>>({});
  const attemptRef = useRef<Record<number, number>>({});
  const busyProblemRef = useRef<Set<number>>(new Set());
  const mountedRef = useRef(true);

  const applyDetail = useCallback((next: ContestDetailModel, notify = false) => {
    if (!mountedRef.current) return;
    const previous = phaseRef.current;
    phaseRef.current = next.contest.phase;
    setDetail(next);
    if (!notify || !previous || previous === next.contest.phase) return;
    if (previous === "UPCOMING" && next.contest.phase === "RUNNING") toast.success("比赛已开始");
    if (previous === "RUNNING" && next.contest.phase === "ENDED") toast.info("比赛已结束");
  }, []);

  const reload = useCallback(async (notify = false) => {
    const response = await api.getContest(contestId);
    applyDetail(response.detail, notify);
  }, [applyDetail, contestId]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    Promise.allSettled([api.getContest(contestId), api.getLanguages()]).then(([contestResult, languageResult]) => {
      if (!active) return;
      if (contestResult.status === "fulfilled") applyDetail(contestResult.value.detail);
      else setError(getApiErrorMessage(contestResult.reason, "比赛加载失败"));
      if (languageResult.status === "fulfilled") {
        setLanguages(languageResult.value.languages);
        setLanguageError(languageResult.value.languages.length === 0 ? "当前没有可用编程语言，算法提交已禁用。" : "");
      } else {
        setLanguages([]);
        setLanguageError(getApiErrorMessage(languageResult.reason, "编程语言列表加载失败，算法提交已禁用。"));
      }
    }).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [applyDetail, contestId]);

  useEffect(() => {
    if (!detail || languages.length === 0) return;
    const first = languages[0];
    setDrafts((current) => {
      const next = { ...current };
      for (const problem of detail.problems) {
        if (problem.problemType !== "ALGORITHM" || next[problem.contestProblemId]) continue;
        next[problem.contestProblemId] = {
          language: first.id,
          codeByLanguage: { [first.id]: first.template },
        };
      }
      return next;
    });
  }, [detail, languages]);

  const currentPhase = detail?.contest.phase;
  useEffect(() => {
    if (!currentPhase || !["UPCOMING", "RUNNING"].includes(currentPhase)) return;
    const interval = window.setInterval(() => void reload(true).catch((reason) => setError(getApiErrorMessage(reason, "比赛状态刷新失败"))), CONTEST_REFRESH_MS);
    return () => window.clearInterval(interval);
  }, [currentPhase, reload]);

  useEffect(() => {
    const interval = window.setInterval(() => setNow(Date.now()), 1_000);
    return () => window.clearInterval(interval);
  }, []);

  const boundary = detail?.contest.phase === "UPCOMING"
    ? Date.parse(detail.contest.startAt)
    : detail?.contest.phase === "RUNNING" ? Date.parse(detail.contest.endAt) : null;
  useEffect(() => {
    if (boundary == null || now < boundary || boundaryRefreshRef.current === boundary) return;
    boundaryRefreshRef.current = boundary;
    void reload(true).catch((reason) => setError(getApiErrorMessage(reason, "比赛状态刷新失败")));
  }, [boundary, now, reload]);

  useEffect(() => {
    mountedRef.current = true;
    const controllers = controllersRef.current;
    const busyProblems = busyProblemRef.current;
    return () => {
      mountedRef.current = false;
      Object.values(controllers).forEach((controller) => controller.abort());
      busyProblems.clear();
    };
  }, []);

  const requestedProblemId = Number(searchParams.get("problem"));
  const activeProblem = useMemo(() => {
    if (!detail?.problems.length) return null;
    return detail.problems.find((problem) => problem.contestProblemId === requestedProblemId) ?? detail.problems[0];
  }, [detail, requestedProblemId]);

  useEffect(() => {
    if (!activeProblem || requestedProblemId === activeProblem.contestProblemId) return;
    const next = new URLSearchParams(searchParams);
    next.set("problem", String(activeProblem.contestProblemId));
    setSearchParams(next, { replace: true });
  }, [activeProblem, requestedProblemId, searchParams, setSearchParams]);

  function selectProblem(problemId: number) {
    const next = new URLSearchParams(searchParams);
    next.set("problem", String(problemId));
    setSearchParams(next);
  }

  function updateDraft(problemId: number, updater: (current: ProblemDraft) => ProblemDraft) {
    setDrafts((current) => {
      const existing = current[problemId];
      if (!existing) return current;
      return { ...current, [problemId]: updater(existing) };
    });
  }

  function switchLanguage(problemId: number, language: string) {
    updateDraft(problemId, (current) => ({
      language,
      codeByLanguage: {
        ...current.codeByLanguage,
        [language]: current.codeByLanguage[language] ?? languages.find((item) => item.id === language)?.template ?? "",
      },
    }));
  }

  function updateCode(problemId: number, code: string) {
    updateDraft(problemId, (current) => ({
      ...current,
      codeByLanguage: { ...current.codeByLanguage, [current.language]: code },
    }));
  }

  function updateRun(problemId: number, patch: Partial<ProblemRunState>) {
    if (!mountedRef.current) return;
    setRuns((current) => {
      const previous = current[problemId] ?? { busy: null, pollCount: 0 };
      return { ...current, [problemId]: { ...previous, ...patch } };
    });
  }

  async function submitAlgorithm(problem: ContestProblemItem) {
    const problemId = problem.contestProblemId;
    const draft = drafts[problemId];
    if (!draft || busyProblemRef.current.has(problemId)) return;
    const code = draft.codeByLanguage[draft.language] ?? "";
    if (!draft.language || !code.trim()) return;

    busyProblemRef.current.add(problemId);
    controllersRef.current[problemId]?.abort();
    const controller = new AbortController();
    controllersRef.current[problemId] = controller;
    const attempt = (attemptRef.current[problemId] ?? 0) + 1;
    attemptRef.current[problemId] = attempt;
    updateRun(problemId, { busy: "submitting", pollCount: 0, submission: undefined, notice: undefined, error: undefined });
    try {
      const response = await api.submitContestAlgorithm(contestId, problemId, draft.language, code);
      if (attemptRef.current[problemId] !== attempt || controller.signal.aborted) return;
      const queued: Submission = {
        id: response.submissionId,
        verdict: response.status,
        timeMs: 0,
        memoryKb: 0,
        passed: 0,
        total: 0,
        language: draft.language,
        code,
        createdAt: new Date().toISOString(),
        contestProblemId: problemId,
      };
      updateRun(problemId, { busy: "polling", submission: queued });
      const settled = await api.pollSubmission(response.submissionId, {
        timeoutMs: 60_000,
        signal: controller.signal,
        onTick: (pollCount, submission) => {
          if (attemptRef.current[problemId] !== attempt || controller.signal.aborted) return;
          updateRun(problemId, { pollCount, submission: submission ?? queued });
        },
      });
      if (attemptRef.current[problemId] !== attempt || controller.signal.aborted) return;
      const pending = settled.verdict === "PENDING" || settled.verdict === "JUDGING";
      updateRun(problemId, {
        busy: null,
        submission: settled,
        notice: pending ? "判题仍在进行，可前往提交记录查看最终结果。" : undefined,
      });
      if (!pending) toast.success(`Submission #${settled.id} 判题完成`);
    } catch (reason) {
      if (!isAbortError(reason) && attemptRef.current[problemId] === attempt) {
        updateRun(problemId, { busy: null, error: getApiErrorMessage(reason, "提交失败") });
      }
    } finally {
      if (controllersRef.current[problemId] === controller) delete controllersRef.current[problemId];
      busyProblemRef.current.delete(problemId);
    }
  }

  async function submitOffice(problem: ContestProblemItem) {
    const problemId = problem.contestProblemId;
    const file = files[problemId];
    if (!file || busyProblemRef.current.has(problemId)) return;
    busyProblemRef.current.add(problemId);
    updateRun(problemId, { busy: "submitting", officeSubmission: undefined, notice: undefined, error: undefined });
    try {
      const response = await api.submitContestOffice(contestId, problemId, file);
      updateRun(problemId, { busy: null, officeSubmission: response.submission });
      toast.success("DOCX 上传并判题完成");
    } catch (reason) {
      updateRun(problemId, { busy: null, error: getApiErrorMessage(reason, "DOCX 提交失败") });
    } finally {
      busyProblemRef.current.delete(problemId);
    }
  }

  async function submitChoice(problem: ContestProblemItem) {
    const problemId = problem.contestProblemId;
    if (busyProblemRef.current.has(problemId)) return;
    const selected = choiceSelections[problemId] ?? [];
    if (selected.length === 0) return;
    busyProblemRef.current.add(problemId);
    updateRun(problemId, { busy: "submitting", choiceSubmission: undefined, notice: undefined, error: undefined });
    try {
      const response = await api.submitContestChoice(contestId, problemId, selected);
      updateRun(problemId, { busy: null, choiceSubmission: response.submission });
      toast.success("作答已提交");
    } catch (reason) {
      updateRun(problemId, { busy: null, error: getApiErrorMessage(reason, "Office 选择题提交失败") });
    } finally {
      busyProblemRef.current.delete(problemId);
    }
  }

  async function downloadStarter(problem: ContestProblemItem) {
    const problemId = problem.contestProblemId;
    if (busyProblemRef.current.has(problemId)) return;
    busyProblemRef.current.add(problemId);
    updateRun(problemId, { busy: "downloading", notice: undefined, error: undefined });
    try {
      const filename = String((problem.content as { starterDocName?: string } | null)?.starterDocName ?? "starter.docx");
      await api.downloadContestStarter(contestId, problemId, filename);
      updateRun(problemId, { busy: null });
      toast.success("待修改文件已下载");
    } catch (reason) {
      updateRun(problemId, { busy: null, error: getApiErrorMessage(reason, "待修改文件下载失败") });
    } finally {
      busyProblemRef.current.delete(problemId);
    }
  }

  function changeChoice(problem: ContestProblemItem, value: string, checked: boolean, multi: boolean) {
    setChoiceSelections((current) => {
      const previous = current[problem.contestProblemId] ?? [];
      const next = multi
        ? checked ? [...new Set([...previous, value])] : previous.filter((item) => item !== value)
        : checked ? [value] : [];
      return { ...current, [problem.contestProblemId]: next };
    });
  }

  function chooseFile(problemId: number, file: File | null) {
    if (!file) {
      setFiles((current) => ({ ...current, [problemId]: null }));
      return;
    }
    if (!file.name.toLowerCase().endsWith(".docx")) {
      updateRun(problemId, { error: "仅支持 DOCX 文件。" });
      setFiles((current) => ({ ...current, [problemId]: null }));
      return;
    }
    if (file.size > DOCX_MAX_BYTES) {
      updateRun(problemId, { error: "文件超过 10 MiB。" });
      setFiles((current) => ({ ...current, [problemId]: null }));
      return;
    }
    updateRun(problemId, { error: undefined });
    setFiles((current) => ({ ...current, [problemId]: file }));
  }

  async function join() {
    if (joiningRef.current || !detail) return;
    joiningRef.current = true;
    setJoining(true);
    setError("");
    try {
      await api.joinContest(detail.contest.id);
      await reload();
      toast.success("已加入比赛");
    } catch (reason) {
      setError(getApiErrorMessage(reason, "加入失败"));
    } finally {
      joiningRef.current = false;
      setJoining(false);
    }
  }

  if (loading) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>;
  if (!detail) return <div className="p-8"><p role="alert" className="text-red-600">{error || "比赛不存在"}</p></div>;

  const contest = detail.contest;
  const canJoin = user?.role === "USER" && contest.status === "PUBLISHED" && contest.phase === "UPCOMING"
    && contest.accessType === "OPEN" && !contest.participant;
  const countdown = countdownLabel(contest.phase, contest.startAt, contest.endAt, now);

  return <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
    <div className="mb-5"><Link className="text-sm text-zinc-500 hover:underline" to="/contests">← 返回比赛</Link>
      <div className="mt-3 flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-2xl font-bold">{contest.title}</h1><p className="mt-1 text-sm text-zinc-500">{formatTime(contest.startAt)} — {formatTime(contest.endAt)}</p>{countdown && <p className="mt-1 text-sm font-medium text-blue-700">{countdown}</p>}</div><span className={cn("rounded px-3 py-1 text-sm font-semibold", phaseClass(contest.phase))}>{PHASE_LABEL[contest.phase]}</span></div>
    </div>
    {error && <p role="alert" className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <Card className="mb-4 p-5"><Markdown>{contest.description || "暂无比赛说明。"}</Markdown><div className="mt-3 flex flex-wrap gap-3 text-sm text-zinc-500"><span>{contest.accessType === "OPEN" ? "公开报名" : "邀请制"}</span><span>{contest.participant ? "已参赛" : "未参赛"}</span></div>
      {canJoin && <Button className="mt-4" disabled={joining} onClick={() => void join()}>{joining && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}{joining ? "加入中..." : "加入比赛"}</Button>}
      {contest.accessType === "INVITE_ONLY" && !contest.participant && user?.role === "USER" && <p className="mt-3 text-sm text-amber-700">邀请制比赛仅对受邀学生开放。</p>}
      {(contest.phase === "RUNNING" || contest.phase === "ENDED") && <Link className="mt-4 inline-block text-sm font-medium text-blue-700 hover:underline" to={`/contests/${contest.id}/standings`}>查看排名 →</Link>}
    </Card>
    {contest.phase === "UPCOMING" && <p className="mb-4 rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-800">比赛尚未开始；CONTEST_ONLY 题目正文将在服务端确认开赛且你是参赛者后开放。</p>}
    {contest.phase === "CANCELLED" && <p className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">比赛已取消，历史信息保留，但不能创建新提交。</p>}
    {detail.problems.length === 0 ? <Card className="p-8 text-center text-zinc-500">当前阶段没有可展示的题目</Card> : <>
      <div className="mb-3 flex gap-2 overflow-x-auto pb-2 md:hidden" aria-label="比赛题目导航">
        {detail.problems.map((problem) => <ProblemNavButton key={problem.contestProblemId} problem={problem} active={problem.contestProblemId === activeProblem?.contestProblemId} compact onClick={() => selectProblem(problem.contestProblemId)} />)}
      </div>
      <div className="grid gap-5 md:grid-cols-[260px_minmax(0,1fr)]">
        <Card className="hidden h-fit p-3 md:block"><h2 className="mb-2 px-2 text-sm font-semibold">题目导航</h2><div className="space-y-1">{detail.problems.map((problem) => <ProblemNavButton key={problem.contestProblemId} problem={problem} active={problem.contestProblemId === activeProblem?.contestProblemId} onClick={() => selectProblem(problem.contestProblemId)} />)}</div></Card>
        {activeProblem && <ContestProblemPanel
          problem={activeProblem}
          phase={contest.phase}
          participant={contest.participant}
          languages={languages}
          languageError={languageError}
          draft={drafts[activeProblem.contestProblemId]}
          run={runs[activeProblem.contestProblemId]}
          file={files[activeProblem.contestProblemId] ?? null}
          selectedChoices={choiceSelections[activeProblem.contestProblemId] ?? []}
          onLanguageChange={(language) => switchLanguage(activeProblem.contestProblemId, language)}
          onCodeChange={(code) => updateCode(activeProblem.contestProblemId, code)}
          onFileChange={(file) => chooseFile(activeProblem.contestProblemId, file)}
          onSubmitAlgorithm={() => void submitAlgorithm(activeProblem)}
          onSubmitOffice={() => void submitOffice(activeProblem)}
          onChoiceChange={(value, checked, multi) => changeChoice(activeProblem, value, checked, multi)}
          onSubmitChoice={() => void submitChoice(activeProblem)}
          onDownloadStarter={() => void downloadStarter(activeProblem)}
        />}
      </div>
    </>}
  </div>;
}

function ProblemNavButton({ problem, active, compact = false, onClick }: {
  problem: ContestProblemItem;
  active: boolean;
  compact?: boolean;
  onClick: () => void;
}) {
  return <button type="button" aria-current={active ? "page" : undefined} onClick={onClick} className={cn(
    "rounded-md border text-left transition-colors",
    compact ? "min-w-36 px-3 py-2" : "w-full px-3 py-2",
    active ? "border-zinc-900 bg-zinc-900 text-white" : "border-transparent text-zinc-700 hover:bg-zinc-100",
  )}><span className="mr-2 font-bold">{problem.label}</span><span className="text-sm">{problem.title}</span><span className={cn("ml-2 text-[10px]", active ? "text-zinc-300" : "text-zinc-500")}>{problemTypeLabel(problem.problemType)} · {problem.difficulty}</span></button>;
}

function ContestProblemPanel({
  problem,
  phase,
  participant,
  languages,
  languageError,
  draft,
  run,
  file,
  selectedChoices,
  onLanguageChange,
  onCodeChange,
  onFileChange,
  onSubmitAlgorithm,
  onSubmitOffice,
  onChoiceChange,
  onSubmitChoice,
  onDownloadStarter,
}: {
  problem: ContestProblemItem;
  phase: ContestDetailModel["contest"]["phase"];
  participant: boolean;
  languages: LanguageDef[];
  languageError: string;
  draft?: ProblemDraft;
  run?: ProblemRunState;
  file: File | null;
  selectedChoices: string[];
  onLanguageChange: (language: string) => void;
  onCodeChange: (code: string) => void;
  onFileChange: (file: File | null) => void;
  onSubmitAlgorithm: () => void;
  onSubmitOffice: () => void;
  onChoiceChange: (value: string, checked: boolean, multi: boolean) => void;
  onSubmitChoice: () => void;
  onDownloadStarter: () => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const canSubmit = phase === "RUNNING" && participant;
  const content = problem.content as {
    description?: string;
    inputFmt?: string;
    outputFmt?: string;
    samples?: Array<{ input: string; output: string }>;
    appType?: string;
    category?: string;
    questionType?: "SINGLE_CHOICE" | "MULTI_CHOICE" | "TRUE_FALSE";
    content?: string;
    options?: string[];
    hasStarter?: boolean;
    starterDocName?: string;
  } | null;
  const code = draft ? (draft.codeByLanguage[draft.language] ?? "") : "";
  const busy = run?.busy != null;

  return <Card className="min-w-0 p-5" data-testid={`contest-problem-${problem.contestProblemId}`}>
    <div className="mb-4 flex items-center gap-2"><span className="rounded bg-zinc-900 px-2 py-1 text-xs font-bold text-white">{problem.label}</span><h2 className="text-lg font-semibold">{problem.title}</h2><span className="text-xs text-zinc-500">{problemTypeLabel(problem.problemType)}</span></div>
    {content?.description && <Markdown>{content.description}</Markdown>}
    {content?.inputFmt && <section className="mt-4"><h3 className="text-sm font-semibold">输入</h3><Markdown>{content.inputFmt}</Markdown></section>}
    {content?.outputFmt && <section className="mt-4"><h3 className="text-sm font-semibold">输出</h3><Markdown>{content.outputFmt}</Markdown></section>}
    {content?.samples && content.samples.length > 0 && <section className="mt-5"><h3 className="mb-3 text-sm font-semibold">样例</h3><div className="space-y-4">{content.samples.map((sample, index) => <div key={index}><h4 className="mb-2 text-xs font-semibold text-zinc-500">样例 {index + 1}</h4><div className="grid gap-3 sm:grid-cols-2"><SampleBox label="输入" value={sample.input} /><SampleBox label="输出" value={sample.output} /></div></div>)}</div></section>}

    {canSubmit && problem.problemType === "ALGORITHM" && <section className="mt-5 space-y-3 border-t pt-5">
      {languageError && <p role="alert" className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{languageError}</p>}
      <div className="w-full sm:w-52"><Select value={draft?.language ?? ""} disabled={languages.length === 0 || busy} onValueChange={onLanguageChange}><SelectTrigger aria-label={`${problem.label} 编程语言`}><SelectValue placeholder="选择语言" /></SelectTrigger><SelectContent>{languages.map((item) => <SelectItem key={item.id} value={item.id}>{item.name}</SelectItem>)}</SelectContent></Select></div>
      <CodeEditor ariaLabel={`${problem.label} 源代码`} value={code} language={draft?.language ?? ""} onChange={onCodeChange} height="360px" />
      <Button disabled={busy || languages.length === 0 || !draft?.language || !code.trim()} onClick={onSubmitAlgorithm}><Send className="mr-1 h-4 w-4" />{run?.busy === "submitting" ? "提交中..." : run?.busy === "polling" ? `正在判题${run.pollCount ? ` (${run.pollCount})` : "..."}` : "提交代码"}</Button>
    </section>}

    {problem.problemType === "OFFICE_CHOICE" && <section className="mt-5 space-y-3 border-t pt-5">
      <p className="text-sm text-zinc-700">{content?.content}</p>
      <p className="text-xs text-zinc-500">{content?.appType} · {content?.questionType}</p>
      {canSubmit && <fieldset className="space-y-2" disabled={busy}>
        <legend className="sr-only">选择答案</legend>
        {(content?.options ?? []).map((option, index) => {
          const value = content?.questionType === "TRUE_FALSE" ? (index === 0 ? "T" : "F") : String(index);
          const multi = content?.questionType === "MULTI_CHOICE";
          return <label key={value} className="flex cursor-pointer items-start gap-2 rounded border p-3 text-sm">
            <input type={multi ? "checkbox" : "radio"} name={`choice-${problem.contestProblemId}`}
              value={value} checked={selectedChoices.includes(value)}
              onChange={(event) => onChoiceChange(value, event.target.checked, multi)} />
            <span>{option}</span>
          </label>;
        })}
        <Button disabled={busy || selectedChoices.length === 0} onClick={onSubmitChoice}>
          {busy && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}{busy ? "提交中..." : "提交答案"}
        </Button>
      </fieldset>}
    </section>}

    {problem.problemType === "OFFICE_DOCX" && content?.hasStarter && participant && (phase === "RUNNING" || phase === "ENDED") && <section className="mt-5 rounded border border-blue-200 bg-blue-50 p-4">
      <p className="mb-2 text-sm font-medium text-blue-900">① 下载待修改文件 → ② 用 Word / WPS 修改 → ③ 上传结果</p>
      <Button variant="outline" disabled={busy} onClick={onDownloadStarter}>
        {run?.busy === "downloading" ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Download className="mr-1 h-4 w-4" />}
        {run?.busy === "downloading" ? "下载中..." : `下载 ${content.starterDocName ?? "starter.docx"}`}
      </Button>
    </section>}

    {canSubmit && problem.problemType === "OFFICE_DOCX" && <section className="mt-5 space-y-3 border-t pt-5">
      <label className="block text-sm font-medium" htmlFor={`contest-docx-${problem.contestProblemId}`}>DOCX 文件</label>
      <input ref={fileInputRef} id={`contest-docx-${problem.contestProblemId}`} type="file" accept=".docx" className="sr-only" disabled={busy} onChange={(event) => onFileChange(event.target.files?.[0] ?? null)} />
      <Button type="button" variant="outline" disabled={busy} onClick={() => fileInputRef.current?.click()}><FileUp className="mr-1 h-4 w-4" />选择 DOCX 文件</Button>
      {file && <div className="min-w-0 max-w-full rounded border bg-zinc-50 px-3 py-2 text-sm text-zinc-600" aria-live="polite"><p className="truncate" title={file.name}>{file.name}</p><p className="text-xs text-zinc-500">{formatFileSize(file.size)}</p></div>}
      <Button disabled={busy || !file} onClick={onSubmitOffice}>{run?.busy && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}<FileUp className="mr-1 h-4 w-4" />{run?.busy ? "上传判题中..." : "提交 DOCX"}</Button>
    </section>}

    {!canSubmit && <p className="mt-4 rounded bg-zinc-50 p-3 text-sm text-zinc-600">{phase === "ENDED" ? "比赛已结束；历史题目仍可查看。" : phase === "UPCOMING" ? "比赛尚未开始。" : "仅参赛者可在比赛进行中提交。"}</p>}
    {run?.error && <p role="alert" className="mt-3 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{run.error}</p>}
    {run?.submission && <SubmissionResultCard submission={run.submission} pendingMessage={run.notice} />}
    {run?.officeSubmission && <OfficeJudgeResult submission={run.officeSubmission} />}
    {run?.choiceSubmission && <div className={cn("mt-4 rounded border p-4 text-sm font-medium",
      run.choiceSubmission.correct ? "border-green-200 bg-green-50 text-green-800" : "border-amber-200 bg-amber-50 text-amber-800")}
      role="status">{run.choiceSubmission.correct ? "回答正确" : "回答错误"} · Record #{run.choiceSubmission.recordId}</div>}
  </Card>;
}

function problemTypeLabel(type: ContestProblemItem["problemType"]) {
  if (type === "ALGORITHM") return "算法";
  if (type === "OFFICE_CHOICE") return "Office 选择题";
  return "DOCX";
}

function SampleBox({ label, value }: { label: string; value: string }) {
  return <div><div className="mb-1 text-xs font-medium text-zinc-500">{label}</div><pre className="overflow-x-auto rounded-md bg-zinc-900 p-3 text-xs text-zinc-100">{value}</pre></div>;
}

function countdownLabel(phase: ContestDetailModel["contest"]["phase"], startAt: string, endAt: string, now: number) {
  if (phase === "UPCOMING") return `距离开始 ${formatDuration(Math.max(0, Date.parse(startAt) - now))}`;
  if (phase === "RUNNING") return `剩余 ${formatDuration(Math.max(0, Date.parse(endAt) - now))}`;
  if (phase === "ENDED") return "比赛已结束";
  return "";
}

function formatDuration(milliseconds: number) {
  const seconds = Math.floor(milliseconds / 1_000);
  const hours = Math.floor(seconds / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const remainingSeconds = seconds % 60;
  return [hours, minutes, remainingSeconds].map((part) => String(part).padStart(2, "0")).join(":");
}

function formatFileSize(bytes: number) {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MiB` : `${Math.ceil(bytes / 1024)} KiB`;
}
