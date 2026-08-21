import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowDown, ArrowUp, Loader2, Plus, Search, Trash2 } from "lucide-react";
import { toast } from "sonner";
import {
  api,
  getApiErrorMessage,
  type ContestDetail,
  type ContestParticipant,
  type ContestProblemItem,
  type ContestStudentOption,
  type ContestUpsert,
  type RejudgeBatchDetail,
  type RejudgeableSubmission,
  type DocExerciseListItem,
  type OfficeQuestionListItem,
  type ProblemListItem,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { cn } from "@/lib/utils";
import { phaseClass } from "./ContestList";

const initialForm: ContestUpsert = {
  title: "", description: "", startAt: "", endAt: "", accessType: "OPEN", scoringMode: "SCORE", freezeAt: null,
};
const PARTICIPANT_PAGE_SIZE = 20;
const CATALOG_PAGE_SIZE = 20;
const STUDENT_PAGE_SIZE = 10;

type ConfirmAction =
  | { kind: "publish" }
  | { kind: "cancel" }
  | { kind: "delete" }
  | { kind: "remove-problem"; problem: ContestProblemItem }
  | { kind: "remove-participant"; participant: ContestParticipant }
  | { kind: "rejudge"; contestProblemId?: number; submissionId?: number };

type FormErrors = Partial<Record<"title" | "startAt" | "endAt" | "freezeAt", string>>;

export default function ContestManage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const contestId = id && id !== "new" ? Number(id) : null;
  const [form, setForm] = useState<ContestUpsert>(initialForm);
  const [savedForm, setSavedForm] = useState<ContestUpsert>(initialForm);
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const [detail, setDetail] = useState<ContestDetail | null>(null);
  const [participants, setParticipants] = useState<ContestParticipant[]>([]);
  const [participantPage, setParticipantPage] = useState(1);
  const [participantTotal, setParticipantTotal] = useState(0);
  const [catalogType, setCatalogType] = useState<ContestProblemItem["problemType"]>("ALGORITHM");
  const [catalogPage, setCatalogPage] = useState(1);
  const [catalogTotal, setCatalogTotal] = useState(0);
  const [catalogProblems, setCatalogProblems] = useState<ProblemListItem[]>([]);
  const [catalogDocs, setCatalogDocs] = useState<DocExerciseListItem[]>([]);
  const [catalogQuestions, setCatalogQuestions] = useState<OfficeQuestionListItem[]>([]);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [selectedProblemIds, setSelectedProblemIds] = useState<Set<number>>(new Set());
  const [studentQuery, setStudentQuery] = useState("");
  const [studentPage, setStudentPage] = useState(1);
  const [studentTotal, setStudentTotal] = useState(0);
  const [studentOptions, setStudentOptions] = useState<ContestStudentOption[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [loading, setLoading] = useState(Boolean(contestId));
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [studentsLoading, setStudentsLoading] = useState(false);
  const [error, setError] = useState("");
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [rejudgeBatch, setRejudgeBatch] = useState<RejudgeBatchDetail | null>(null);
  const [rejudgeableSubmissions, setRejudgeableSubmissions] = useState<RejudgeableSubmission[]>([]);
  const [rejudgeSubmissionPage, setRejudgeSubmissionPage] = useState(1);
  const [rejudgeSubmissionTotal, setRejudgeSubmissionTotal] = useState(0);
  const busyRef = useRef(false);

  const mutable = !detail || detail.contest.phase === "DRAFT" || detail.contest.phase === "UPCOMING";
  const scoringMutable = !detail || detail.contest.phase === "DRAFT";
  const rejudgeAllowed = detail?.contest.phase === "RUNNING" || detail?.contest.phase === "ENDED";
  const dirty = JSON.stringify(form) !== JSON.stringify(savedForm);

  async function loadDetail(currentId: number) {
    const response = await api.getContest(currentId);
    applyLoadedDetail(response.detail);
  }

  function applyLoadedDetail(nextDetail: ContestDetail) {
    const nextForm = {
      title: nextDetail.contest.title,
      description: nextDetail.contest.description,
      startAt: toLocalInput(nextDetail.contest.startAt),
      endAt: toLocalInput(nextDetail.contest.endAt),
      accessType: nextDetail.contest.accessType,
      scoringMode: nextDetail.contest.scoringMode,
      freezeAt: nextDetail.contest.freezeAt ? toLocalInput(nextDetail.contest.freezeAt) : null,
    };
    setDetail(nextDetail);
    setForm(nextForm);
    setSavedForm(nextForm);
  }

  async function loadParticipants(currentId: number, page: number) {
    const roster = await api.listContestParticipants(currentId, { page, pageSize: PARTICIPANT_PAGE_SIZE });
    setParticipants(roster.participants);
    setParticipantTotal(roster.total);
  }

  useEffect(() => {
    if (!contestId) return;
    let active = true;
    setLoading(true);
    api.getContest(contestId)
      .then((response) => { if (active) applyLoadedDetail(response.detail); })
      .catch((reason) => { if (active) setError(getApiErrorMessage(reason)); })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [contestId]);

  useEffect(() => {
    if (!contestId) return;
    let active = true;
    api.listContestParticipants(contestId, { page: participantPage, pageSize: PARTICIPANT_PAGE_SIZE })
      .then((roster) => {
        if (!active) return;
        setParticipants(roster.participants);
        setParticipantTotal(roster.total);
      })
      .catch((reason) => active && setError(getApiErrorMessage(reason, "参赛者加载失败")));
    return () => { active = false; };
  }, [contestId, participantPage]);

  useEffect(() => {
    if (!contestId || loading || !mutable) return;
    let active = true;
    setCatalogLoading(true);
    const request = catalogType === "ALGORITHM"
      ? api.listManageProblems({ page: catalogPage, pageSize: CATALOG_PAGE_SIZE })
      : catalogType === "OFFICE_CHOICE"
        ? api.listManageOfficeQuestions({ page: catalogPage, pageSize: CATALOG_PAGE_SIZE })
        : api.listManageDocExercises({ page: catalogPage, pageSize: CATALOG_PAGE_SIZE });
    request.then((response) => {
      if (!active) return;
      if (catalogType === "ALGORITHM" && "problems" in response) {
        setCatalogProblems(response.problems);
        setCatalogTotal(response.total);
      } else if (catalogType === "OFFICE_CHOICE" && "questions" in response) {
        setCatalogQuestions(response.questions);
        setCatalogTotal(response.total);
      } else if (catalogType === "OFFICE_DOCX" && "exercises" in response) {
        setCatalogDocs(response.exercises);
        setCatalogTotal(response.total);
      }
      setSelectedProblemIds(new Set());
    }).catch((reason) => active && setError(getApiErrorMessage(reason, "可选题目加载失败")))
      .finally(() => active && setCatalogLoading(false));
    return () => { active = false; };
  }, [catalogPage, catalogType, contestId, loading, mutable]);

  useEffect(() => {
    if (!contestId || loading || !mutable) return;
    let active = true;
    const timeout = window.setTimeout(() => {
      setStudentsLoading(true);
      api.searchContestStudents({ query: studentQuery.trim(), page: studentPage, pageSize: STUDENT_PAGE_SIZE })
        .then((response) => {
          if (!active) return;
          setStudentOptions(response.students);
          setStudentTotal(response.total);
        })
        .catch((reason) => active && setError(getApiErrorMessage(reason, "学生列表加载失败")))
        .finally(() => active && setStudentsLoading(false));
    }, 250);
    return () => { active = false; window.clearTimeout(timeout); };
  }, [contestId, loading, mutable, studentPage, studentQuery]);

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busyRef.current) return;
    const validation = validateForm(form);
    setFormErrors(validation);
    if (Object.keys(validation).length > 0) return;
    busyRef.current = true;
    setBusy("saving");
    setError("");
    try {
      const payload = toApiForm(form);
      if (contestId) {
        await api.updateContest(contestId, payload);
        await loadDetail(contestId);
        toast.success("比赛设置已保存");
      } else {
        const created = await api.createContest(payload);
        toast.success("比赛草稿已创建");
        navigate(`/admin/contests/${created.detail.contest.id}`, { replace: true });
      }
    } catch (reason) {
      setError(getApiErrorMessage(reason, "保存失败"));
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }

  async function runAction(key: string, call: () => Promise<unknown>, success: string) {
    if (!contestId || busyRef.current) return false;
    busyRef.current = true;
    setBusy(key);
    setError("");
    try {
      await call();
      await Promise.all([loadDetail(contestId), loadParticipants(contestId, participantPage)]);
      toast.success(success);
      return true;
    } catch (reason) {
      setError(getApiErrorMessage(reason));
      return false;
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }

  async function move(problem: ContestProblemItem, offset: number) {
    if (!contestId || !detail || busyRef.current) return;
    const items = [...detail.problems];
    const from = items.findIndex((item) => item.contestProblemId === problem.contestProblemId);
    const to = from + offset;
    if (from < 0 || to < 0 || to >= items.length) return;
    [items[from], items[to]] = [items[to], items[from]];
    await runAction("reordering", () => api.reorderContestProblems(contestId, items.map((item) => item.contestProblemId)), "题目顺序已更新");
  }

  async function addSelectedProblems() {
    if (!contestId || busyRef.current || selectedProblemIds.size === 0) return;
    if (form.scoringMode === "ICPC" && catalogType !== "ALGORITHM") {
      setError("ICPC 比赛只允许添加算法题。");
      return;
    }
    busyRef.current = true;
    setBusy("adding-problems");
    setError("");
    let success = 0;
    const failures: string[] = [];
    for (const problemId of selectedProblemIds) {
      try {
        await api.addContestProblem(contestId, catalogType, problemId);
        success++;
      } catch (reason) {
        failures.push(`#${problemId}: ${getApiErrorMessage(reason)}`);
      }
    }
    try {
      await loadDetail(contestId);
      setSelectedProblemIds(new Set());
      if (success > 0) toast.success(`已添加 ${success} 道题`);
      if (failures.length > 0) setError(`成功 ${success}，失败 ${failures.length}：${failures.join("；")}`);
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }

  async function addStudent(student: ContestStudentOption) {
    if (!contestId) return;
    const added = await runAction(`adding-student-${student.id}`, () => api.addContestParticipant(contestId, student.id), `已添加 ${student.username}`);
    if (added) await loadParticipants(contestId, participantPage);
  }

  async function startRejudge(contestProblemId?: number, submissionId?: number) {
    if (!contestId || busyRef.current) return;
    busyRef.current = true;
    setBusy(submissionId ? `rejudge-submission-${submissionId}` : contestProblemId ? `rejudge-${contestProblemId}` : "rejudge-all");
    try {
      const response = submissionId
        ? await api.rejudgeContestSubmission(contestId, submissionId)
        : contestProblemId
          ? await api.rejudgeContestProblem(contestId, contestProblemId)
          : await api.rejudgeContest(contestId);
      setRejudgeBatch(response.batch);
      toast.success("重判批次已创建");
    } catch (reason) {
      setError(getApiErrorMessage(reason, "创建重判失败"));
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }

  useEffect(() => {
    if (!contestId || !rejudgeBatch || !["PENDING", "RUNNING"].includes(rejudgeBatch.batch.status)) return;
    const timer = window.setInterval(() => {
      api.getRejudgeBatch(contestId, rejudgeBatch.batch.id)
        .then((response) => setRejudgeBatch(response.batch))
        .catch((reason) => setError(getApiErrorMessage(reason, "重判进度刷新失败")));
    }, 2_000);
    return () => window.clearInterval(timer);
  }, [contestId, rejudgeBatch]);

  useEffect(() => {
    if (!contestId || !rejudgeAllowed) return;
    let active = true;
    api.listRejudgeableContestSubmissions(contestId, { page: rejudgeSubmissionPage, pageSize: 20 })
      .then((response) => {
        if (!active) return;
        setRejudgeableSubmissions(response.submissions);
        setRejudgeSubmissionTotal(response.total);
      })
      .catch((reason) => active && setError(getApiErrorMessage(reason, "重判提交加载失败")));
    return () => { active = false; };
  }, [contestId, rejudgeAllowed, rejudgeSubmissionPage, rejudgeBatch?.batch.id]);

  async function confirm() {
    if (!confirmAction || !contestId || busyRef.current || !detail) return;
    const action = confirmAction;
    setConfirmAction(null);
    if (action.kind === "publish") {
      if (dirty) { setError("请先保存比赛设置，再发布。"); return; }
      await runAction("publishing", () => api.publishContest(contestId), "比赛已发布");
    } else if (action.kind === "cancel") {
      await runAction("cancelling", () => api.cancelContest(contestId), "比赛已取消");
    } else if (action.kind === "delete") {
      busyRef.current = true;
      setBusy("deleting");
      try {
        await api.deleteContest(contestId);
        toast.success("草稿已删除");
        navigate("/contests");
      } catch (reason) {
        setError(getApiErrorMessage(reason, "删除失败"));
        busyRef.current = false;
        setBusy(null);
      }
    } else if (action.kind === "remove-problem") {
      await runAction(`removing-problem-${action.problem.contestProblemId}`, () => api.removeContestProblem(contestId, action.problem.contestProblemId), "题目已移除");
    } else if (action.kind === "remove-participant") {
      await runAction(`removing-participant-${action.participant.userId}`, () => api.removeContestParticipant(contestId, action.participant.userId), "参赛者已移除");
    } else if (action.kind === "rejudge") {
      await startRejudge(action.contestProblemId, action.submissionId);
    }
  }

  const participantPages = Math.max(1, Math.ceil(participantTotal / PARTICIPANT_PAGE_SIZE));
  const catalogPages = Math.max(1, Math.ceil(catalogTotal / CATALOG_PAGE_SIZE));
  const studentPages = Math.max(1, Math.ceil(studentTotal / STUDENT_PAGE_SIZE));
  const rejudgeSubmissionPages = Math.max(1, Math.ceil(rejudgeSubmissionTotal / 20));
  const existingIds = useMemo(() => new Set(detail?.problems.filter((item) => item.problemType === catalogType).map((item) => item.problemId) ?? []), [catalogType, detail]);
  const normalizedQuery = catalogQuery.trim().toLowerCase();
  const catalogItems = (catalogType === "ALGORITHM"
    ? catalogProblems.map((item) => ({ ...item, selectorTitle: item.title, selectorMeta: item.slug }))
    : catalogType === "OFFICE_CHOICE"
      ? catalogQuestions.map((item) => ({ ...item, selectorTitle: item.content, selectorMeta: `${item.appType} · ${item.questionType}` }))
      : catalogDocs.map((item) => ({ ...item, selectorTitle: item.title, selectorMeta: item.hasStarterDoc && item.hasTeacherDoc ? "Starter + Reference 已齐全" : "缺少 Starter 或 Reference" })))
    .filter((item) => {
    if (!normalizedQuery) return true;
    return item.selectorTitle.toLowerCase().includes(normalizedQuery)
      || item.selectorMeta.toLowerCase().includes(normalizedQuery);
  });

  if (loading) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>;
  return <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6">
    <div className="mb-5 flex items-center justify-between"><div><Link className="text-sm text-zinc-500 hover:underline" to="/contests">← 返回比赛</Link><h1 className="mt-2 text-2xl font-bold">{contestId ? "管理比赛" : "创建比赛"}</h1></div>{detail && <span className={cn("rounded px-3 py-1 text-sm font-semibold", phaseClass(detail.contest.phase))}>{detail.contest.phase}</span>}</div>
    {error && <p role="alert" className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <form onSubmit={save}><Card className="grid gap-4 p-5 md:grid-cols-2">
      <Field label="标题" htmlFor="contest-title" error={formErrors.title} className="md:col-span-2"><Input id="contest-title" value={form.title} disabled={!mutable || busy !== null} onChange={(event) => setForm({ ...form, title: event.target.value })} /></Field>
      <div className="md:col-span-2"><Label htmlFor="contest-description">说明</Label><Textarea id="contest-description" value={form.description} disabled={!mutable || busy !== null} onChange={(event) => setForm({ ...form, description: event.target.value })} /></div>
      <Field label="开始时间" htmlFor="contest-start" error={formErrors.startAt}><Input id="contest-start" type="datetime-local" value={form.startAt} disabled={!mutable || busy !== null} onChange={(event) => setForm({ ...form, startAt: event.target.value })} /><p className="mt-1 text-xs text-zinc-500">按你的本地时间显示，系统会自动统一处理时区。</p></Field>
      <Field label="结束时间" htmlFor="contest-end" error={formErrors.endAt}><Input id="contest-end" type="datetime-local" value={form.endAt} disabled={!mutable || busy !== null} onChange={(event) => setForm({ ...form, endAt: event.target.value })} /></Field>
      <div><Label htmlFor="contest-access">参赛模式</Label><select id="contest-access" disabled={!mutable || busy !== null} value={form.accessType} onChange={(event) => setForm({ ...form, accessType: event.target.value as ContestUpsert["accessType"] })} className="mt-1 h-9 w-full rounded border px-3 text-sm"><option value="OPEN">OPEN（公开报名）</option><option value="INVITE_ONLY">INVITE_ONLY（邀请制）</option></select><p className="mt-1 text-xs text-zinc-500">{form.accessType === "OPEN" ? "学生可以在开赛前自行加入。" : "仅由老师或管理员添加参赛者。"}</p></div>
      <div><Label htmlFor="contest-scoring">计分模式</Label><select id="contest-scoring" disabled={!scoringMutable || busy !== null} value={form.scoringMode} onChange={(event) => { const scoringMode = event.target.value as ContestUpsert["scoringMode"]; setForm({ ...form, scoringMode }); if (scoringMode === "ICPC") { setCatalogType("ALGORITHM"); setSelectedProblemIds(new Set()); } }} className="mt-1 h-9 w-full rounded border px-3 text-sm"><option value="SCORE">SCORE（每题最高分）</option><option value="ICPC">ICPC（算法题，解题数/罚时）</option></select><p className="mt-1 text-xs text-zinc-500">{form.scoringMode === "ICPC" ? "ICPC 模式仅允许算法题。" : "SCORE 支持算法、选择题和 DOCX。"}</p></div>
      <Field label="封榜时间（可选）" htmlFor="contest-freeze" error={formErrors.freezeAt}><Input id="contest-freeze" type="datetime-local" value={form.freezeAt ?? ""} disabled={!scoringMutable || busy !== null} onChange={(event) => setForm({ ...form, freezeAt: event.target.value || null })} /><p className="mt-1 text-xs text-zinc-500">仅草稿可设置；必须位于开始和结束时间之间。</p></Field>
      <div className="flex items-end"><Button disabled={busy !== null || !mutable}>{busy === "saving" ? <><Loader2 className="mr-1 h-4 w-4 animate-spin" />保存中...</> : contestId ? "保存设置" : "创建比赛"}</Button></div>
    </Card></form>
    {dirty && mutable && <p className="mt-2 text-xs text-amber-700">修改尚未保存。</p>}

    {detail && <>
      <div className="my-4 flex flex-wrap gap-2">{detail.contest.status === "DRAFT" && <Button disabled={busy !== null} onClick={() => setConfirmAction({ kind: "publish" })}>{busy === "publishing" ? "发布中..." : "发布比赛"}</Button>}{detail.contest.phase !== "ENDED" && detail.contest.phase !== "CANCELLED" && <Button variant="destructive" disabled={busy !== null} onClick={() => setConfirmAction({ kind: "cancel" })}>{busy === "cancelling" ? "取消中..." : "取消比赛"}</Button>}{detail.contest.status === "DRAFT" && <Button variant="outline" disabled={busy !== null} onClick={() => setConfirmAction({ kind: "delete" })}>删除草稿</Button>}</div>
      {rejudgeAllowed && <Card className="mb-4 p-5"><h2 className="font-semibold">算法题重判</h2><p className="mt-1 text-sm text-zinc-500">重判会创建新 generation；旧任务结果不会覆盖新结果。Office 题暂不支持重判。</p><div className="mt-3 flex flex-wrap gap-2"><Button variant="outline" disabled={busy !== null || !detail.problems.some((item) => item.problemType === "ALGORITHM")} onClick={() => setConfirmAction({ kind: "rejudge" })}>{busy === "rejudge-all" ? "创建中..." : "重判全部算法提交"}</Button>{detail.problems.filter((item) => item.problemType === "ALGORITHM").map((item) => <Button key={item.contestProblemId} variant="outline" size="sm" disabled={busy !== null} onClick={() => setConfirmAction({ kind: "rejudge", contestProblemId: item.contestProblemId })}>{busy === `rejudge-${item.contestProblemId}` ? "创建中..." : `重判 ${item.label}`}</Button>)}</div>{rejudgeableSubmissions.length > 0 && <div className="mt-4 rounded border"><p className="border-b px-3 py-2 text-sm font-medium">单个算法提交（{rejudgeSubmissionTotal}）</p>{rejudgeableSubmissions.map((submission) => <div key={submission.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm"><span>{submission.problemLabel} · {submission.username} · #{submission.id} · {submission.verdict}</span><Button size="sm" variant="outline" disabled={busy !== null} onClick={() => setConfirmAction({ kind: "rejudge", submissionId: submission.id })}>{busy === `rejudge-submission-${submission.id}` ? "创建中..." : "重判此提交"}</Button></div>)}<div className="border-t px-3 py-2"><PageControls page={rejudgeSubmissionPage} totalPages={rejudgeSubmissionPages} onPage={setRejudgeSubmissionPage} disabled={busy !== null} /></div></div>}{rejudgeBatch && <p className="mt-3 rounded bg-zinc-50 p-3 text-sm" role="status">批次 #{rejudgeBatch.batch.id} · {rejudgeBatch.batch.status} · 已完成 {rejudgeBatch.batch.completedCount} / {rejudgeBatch.batch.totalCount}{rejudgeBatch.batch.failedCount ? ` · 失败 ${rejudgeBatch.batch.failedCount}` : ""}</p>}</Card>}
      <Card className="mb-4 p-5"><h2 className="mb-3 font-semibold">比赛题目（{detail.problems.length}）</h2><p className="mb-3 text-xs text-amber-700">PUBLIC 题目仍可从练习区提前查看；赛前保密请使用 CONTEST_ONLY。</p>
        {detail.problems.length === 0 ? <p className="rounded bg-zinc-50 p-6 text-center text-sm text-zinc-500">暂无比赛题目，请从下方选择。</p> : detail.problems.map((item, index) => <div key={item.contestProblemId} className="flex items-center gap-2 border-t py-2 text-sm"><span className="w-8 font-bold">{item.label}</span><span className="flex-1">{item.title} · {contestTypeLabel(item.problemType)}</span><Button aria-label={`上移 ${item.title}`} size="sm" variant="ghost" disabled={!mutable || busy !== null || index === 0} onClick={() => void move(item, -1)}><ArrowUp className="h-4 w-4" /></Button><Button aria-label={`下移 ${item.title}`} size="sm" variant="ghost" disabled={!mutable || busy !== null || index === detail.problems.length - 1} onClick={() => void move(item, 1)}><ArrowDown className="h-4 w-4" /></Button><Button aria-label={`移除 ${item.title}`} size="sm" variant="ghost" disabled={!mutable || busy !== null} onClick={() => setConfirmAction({ kind: "remove-problem", problem: item })}><Trash2 className="h-4 w-4 text-red-600" /></Button></div>)}

        {mutable && <div className="mt-5 rounded border p-4"><div className="flex flex-wrap items-end gap-3"><div><Label htmlFor="catalog-type">添加题型</Label><select id="catalog-type" value={catalogType} disabled={busy !== null} onChange={(event) => { setCatalogType(event.target.value as typeof catalogType); setCatalogPage(1); setCatalogQuery(""); }} className="mt-1 block h-9 rounded border px-3 text-sm"><option value="ALGORITHM">算法题</option>{form.scoringMode === "SCORE" && <><option value="OFFICE_CHOICE">Office 选择题</option><option value="OFFICE_DOCX">DOCX 文件题</option></>}</select></div><div className="min-w-60 flex-1"><Label htmlFor="catalog-search">搜索当前页</Label><div className="relative mt-1"><Search className="absolute left-2.5 top-2.5 h-4 w-4 text-zinc-400" /><Input id="catalog-search" className="pl-8" value={catalogQuery} onChange={(event) => setCatalogQuery(event.target.value)} placeholder="标题或元数据" /></div></div></div>
          {catalogLoading ? <div className="py-8 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-zinc-400" /></div> : catalogItems.length === 0 ? <p className="py-8 text-center text-sm text-zinc-500">当前页没有可选题目</p> : <div className="mt-3 divide-y">{catalogItems.map((item) => {
            const unavailable = existingIds.has(item.id) || item.visible === false
              || ("hasTeacherDoc" in item && (!item.hasTeacherDoc || !item.hasStarterDoc));
            return <label key={item.id} className={cn("flex items-start gap-3 py-3", unavailable && "opacity-50")}><Checkbox checked={selectedProblemIds.has(item.id)} disabled={unavailable || busy !== null} onCheckedChange={(checked) => setSelectedProblemIds((current) => { const next = new Set(current); if (checked) next.add(item.id); else next.delete(item.id); return next; })} aria-label={`选择 ${item.selectorTitle}`} /><span className="flex-1"><span className="block font-medium">{item.selectorTitle}</span><span className="text-xs text-zinc-500">#{item.id} · {item.selectorMeta} · {item.difficulty} · {item.contentVisibility}{existingIds.has(item.id) ? " · 已在比赛中" : ""}</span></span></label>})}</div>}
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2"><PageControls page={catalogPage} totalPages={catalogPages} onPage={setCatalogPage} disabled={catalogLoading || busy !== null} /><Button disabled={selectedProblemIds.size === 0 || busy !== null} onClick={() => void addSelectedProblems()}><Plus className="mr-1 h-4 w-4" />{busy === "adding-problems" ? "添加中..." : `添加 ${selectedProblemIds.size} 道题`}</Button></div>
        </div>}
      </Card>

      <Card className="p-5"><h2 className="mb-1 font-semibold">参赛者（{participantTotal}）</h2><p className="mb-3 text-xs text-zinc-500">{detail.contest.accessType === "OPEN" ? "公开报名：学生可以在开赛前自行加入，也可由老师添加。" : "邀请制比赛：只有列表中的学生可以参赛。"}</p>
        {participants.length === 0 ? <p className="rounded bg-zinc-50 p-6 text-center text-sm text-zinc-500">当前页暂无参赛者</p> : participants.map((participant) => <div key={participant.id} className="flex items-center justify-between border-t py-2 text-sm"><span>{participant.username}（#{participant.userId}）</span><Button size="sm" variant="ghost" disabled={!mutable || busy !== null} onClick={() => setConfirmAction({ kind: "remove-participant", participant })}>移除</Button></div>)}
        <div className="mt-3"><PageControls page={participantPage} totalPages={participantPages} onPage={setParticipantPage} disabled={busy !== null} /></div>
        {mutable && <div className="mt-5 rounded border p-4"><Label htmlFor="student-search">搜索学生</Label><div className="relative mt-1"><Search className="absolute left-2.5 top-2.5 h-4 w-4 text-zinc-400" /><Input id="student-search" className="pl-8" value={studentQuery} onChange={(event) => { setStudentQuery(event.target.value); setStudentPage(1); }} placeholder="输入用户名" /></div>
          {studentsLoading ? <Loader2 className="mx-auto my-6 h-5 w-5 animate-spin text-zinc-400" /> : <div className="mt-2 divide-y">{studentOptions.map((student) => { const joined = participants.some((item) => item.userId === student.id); return <div key={student.id} className="flex items-center justify-between py-2 text-sm"><span>{student.username} <span className="text-zinc-400">#{student.id}</span></span><Button size="sm" variant="outline" disabled={joined || busy !== null} onClick={() => void addStudent(student)}>{joined ? "已加入" : busy === `adding-student-${student.id}` ? "添加中..." : "添加"}</Button></div>})}{studentOptions.length === 0 && <p className="py-4 text-center text-sm text-zinc-500">未找到学生</p>}</div>}
          <PageControls page={studentPage} totalPages={studentPages} onPage={setStudentPage} disabled={studentsLoading || busy !== null} />
        </div>}
      </Card>
    </>}
    <ConfirmDialog action={confirmAction} detail={detail} onOpenChange={(open) => !open && setConfirmAction(null)} onConfirm={() => void confirm()} busy={busy !== null} />
  </div>;
}

function Field({ label, htmlFor, error, className, children }: { label: string; htmlFor: string; error?: string; className?: string; children: React.ReactNode }) {
  return <div className={className}><Label htmlFor={htmlFor}>{label}</Label>{children}{error && <p role="alert" className="mt-1 text-xs text-red-600">{error}</p>}</div>;
}

function PageControls({ page, totalPages, onPage, disabled }: { page: number; totalPages: number; onPage: (page: number) => void; disabled: boolean }) {
  return <div className="flex items-center gap-2"><Button type="button" variant="outline" size="sm" disabled={disabled || page <= 1} onClick={() => onPage(page - 1)}>上一页</Button><span className="text-sm text-zinc-600">{page} / {totalPages}</span><Button type="button" variant="outline" size="sm" disabled={disabled || page >= totalPages} onClick={() => onPage(page + 1)}>下一页</Button></div>;
}

function ConfirmDialog({ action, detail, onOpenChange, onConfirm, busy }: {
  action: ConfirmAction | null;
  detail: ContestDetail | null;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  busy: boolean;
}) {
  const algorithmCount = detail?.problems.filter((item) => item.problemType === "ALGORITHM").length ?? 0;
  const choiceCount = detail?.problems.filter((item) => item.problemType === "OFFICE_CHOICE").length ?? 0;
  const docxCount = detail?.problems.filter((item) => item.problemType === "OFFICE_DOCX").length ?? 0;
  let title = "确认操作";
  let description: React.ReactNode = "此操作需要确认。";
  let confirmLabel = "确认";
  let destructive = false;
  if (action?.kind === "publish" && detail) {
    title = "发布比赛";
    confirmLabel = "确认发布比赛";
    description = <span className="space-y-1"><span className="block">比赛：{detail.contest.title}</span><span className="block">时间：{formatLocal(detail.contest.startAt)} → {formatLocal(detail.contest.endAt)}</span><span className="block">参赛方式：{detail.contest.accessType}</span><span className="block">题目：{detail.problems.length}（Algorithm {algorithmCount} / Office 选择 {choiceCount} / DOCX {docxCount}）</span></span>;
  } else if (action?.kind === "cancel") {
    title = "取消比赛？";
    confirmLabel = "确认取消比赛";
    destructive = true;
    description = "取消后不能继续提交，历史提交和比赛记录会保留；此操作不可恢复。";
  } else if (action?.kind === "delete") {
    title = "删除草稿？";
    confirmLabel = "确认删除草稿";
    destructive = true;
    description = "此操作会删除草稿比赛，且无法恢复。";
  } else if (action?.kind === "remove-problem") {
    title = "移除比赛题目？";
    confirmLabel = "确认移除";
    destructive = true;
    description = `将从比赛中移除 ${action.problem.label} ${action.problem.title}。`;
  } else if (action?.kind === "remove-participant") {
    title = "移除参赛者？";
    confirmLabel = "确认移除";
    destructive = true;
    description = `将 ${action.participant.username} 从比赛参赛者中移除。`;
  } else if (action?.kind === "rejudge") {
    title = "确认重判？";
    confirmLabel = "确认创建重判";
    description = action.submissionId
      ? `将重新判定 Submission #${action.submissionId}。已有成绩可能发生变化。`
      : action.contestProblemId
        ? "将重新判定该算法题的全部提交。已有成绩可能发生变化。"
        : "将重新判定本比赛全部算法提交。Office 题不会被重判，已有成绩可能发生变化。";
  }
  return <AlertDialog open={action !== null} onOpenChange={onOpenChange}><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>{title}</AlertDialogTitle><AlertDialogDescription asChild><div>{description}</div></AlertDialogDescription></AlertDialogHeader><AlertDialogFooter><AlertDialogCancel disabled={busy}>返回检查</AlertDialogCancel><AlertDialogAction disabled={busy} onClick={onConfirm} className={destructive ? "bg-red-600 text-white hover:bg-red-700" : ""}>{confirmLabel}</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog>;
}

function contestTypeLabel(type: ContestProblemItem["problemType"]) {
  if (type === "ALGORITHM") return "算法";
  if (type === "OFFICE_CHOICE") return "Office 选择题";
  return "DOCX";
}

function validateForm(form: ContestUpsert): FormErrors {
  const errors: FormErrors = {};
  if (!form.title.trim()) errors.title = "标题不能为空。";
  const start = Date.parse(form.startAt);
  const end = Date.parse(form.endAt);
  if (!form.startAt || !Number.isFinite(start)) errors.startAt = "请选择开始时间。";
  else if (start <= Date.now()) errors.startAt = "开始时间必须晚于当前时间。";
  if (!form.endAt || !Number.isFinite(end)) errors.endAt = "请选择结束时间。";
  else if (Number.isFinite(start) && end <= start) errors.endAt = "结束时间必须晚于开始时间。";
  if (form.freezeAt) {
    const freeze = Date.parse(form.freezeAt);
    if (!Number.isFinite(freeze)) errors.freezeAt = "请选择有效的封榜时间。";
    else if (Number.isFinite(start) && freeze <= start || Number.isFinite(end) && freeze >= end) {
      errors.freezeAt = "封榜时间必须位于开始和结束时间之间。";
    }
  }
  return errors;
}

function toLocalInput(value: string) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toApiForm(form: ContestUpsert): ContestUpsert {
  return { ...form, title: form.title.trim(), startAt: new Date(form.startAt).toISOString(),
    endAt: new Date(form.endAt).toISOString(), freezeAt: form.freezeAt ? new Date(form.freezeAt).toISOString() : null };
}

function formatLocal(value: string) {
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}
