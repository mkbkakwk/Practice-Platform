import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowDown, ArrowUp, Loader2, Plus, Trash2 } from "lucide-react";
import { api, ApiError, type ContestDetail, type ContestParticipant, type ContestProblemItem, type ContestUpsert } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";

const initialForm: ContestUpsert = {
  title: "", description: "", startAt: "", endAt: "", accessType: "OPEN",
};

export default function ContestManage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const contestId = id && id !== "new" ? Number(id) : null;
  const [form, setForm] = useState<ContestUpsert>(initialForm);
  const [detail, setDetail] = useState<ContestDetail | null>(null);
  const [participants, setParticipants] = useState<ContestParticipant[]>([]);
  const [problemType, setProblemType] = useState<"ALGORITHM" | "OFFICE">("ALGORITHM");
  const [problemId, setProblemId] = useState("");
  const [label, setLabel] = useState("");
  const [participantUserId, setParticipantUserId] = useState("");
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(Boolean(contestId));
  const [error, setError] = useState("");

  async function load(currentId: number) {
    const response = await api.getContest(currentId);
    setDetail(response.detail);
    setForm({
      title: response.detail.contest.title,
      description: response.detail.contest.description,
      startAt: toLocalInput(response.detail.contest.startAt),
      endAt: toLocalInput(response.detail.contest.endAt),
      accessType: response.detail.contest.accessType,
    });
    const roster = await api.listContestParticipants(currentId, { pageSize: 50 });
    setParticipants(roster.participants);
  }

  useEffect(() => {
    if (!contestId) return;
    let active = true;
    load(contestId).catch((exception) => active && setError(messageOf(exception)))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [contestId]);

  const mutable = !detail || detail.contest.phase === "DRAFT" || detail.contest.phase === "UPCOMING";
  async function save(event: React.FormEvent) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const payload = toApiForm(form);
      if (contestId) { await api.updateContest(contestId, payload); await load(contestId); }
      else { const created = await api.createContest(payload); navigate(`/admin/contests/${created.detail.contest.id}`, { replace: true }); }
    } catch (exception) { setError(messageOf(exception)); } finally { setSaving(false); }
  }
  async function action(call: () => Promise<unknown>) {
    if (!contestId) return;
    setError("");
    try { await call(); await load(contestId); } catch (exception) { setError(messageOf(exception)); }
  }
  async function move(problem: ContestProblemItem, offset: number) {
    if (!contestId || !detail) return;
    const items = [...detail.problems];
    const from = items.findIndex((item) => item.contestProblemId === problem.contestProblemId);
    const to = from + offset;
    if (from < 0 || to < 0 || to >= items.length) return;
    [items[from], items[to]] = [items[to], items[from]];
    await action(() => api.reorderContestProblems(contestId, items.map((item) => item.contestProblemId)));
  }

  if (loading) return <div className="py-20 text-center"><Loader2 className="mx-auto h-6 w-6 animate-spin text-zinc-400" /></div>;
  return <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
    <div className="mb-5 flex items-center justify-between"><div><Link className="text-sm text-zinc-500 hover:underline" to="/contests">← 返回比赛</Link><h1 className="mt-2 text-2xl font-bold">{contestId ? "管理比赛" : "创建比赛"}</h1></div>{detail && <span className="rounded bg-zinc-900 px-3 py-1 text-sm font-semibold text-white">{detail.contest.phase}</span>}</div>
    {error && <p role="alert" className="mb-4 rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p>}
    <form onSubmit={save}><Card className="grid gap-4 p-5 md:grid-cols-2">
      <div className="md:col-span-2"><Label htmlFor="contest-title">标题</Label><Input id="contest-title" value={form.title} disabled={!mutable} onChange={(event) => setForm({ ...form, title: event.target.value })} /></div>
      <div className="md:col-span-2"><Label htmlFor="contest-description">说明</Label><Textarea id="contest-description" value={form.description} disabled={!mutable} onChange={(event) => setForm({ ...form, description: event.target.value })} /></div>
      <div><Label htmlFor="contest-start">开始时间（本地显示，提交为 UTC）</Label><Input id="contest-start" type="datetime-local" value={form.startAt} disabled={!mutable} onChange={(event) => setForm({ ...form, startAt: event.target.value })} /></div>
      <div><Label htmlFor="contest-end">结束时间（本地显示，提交为 UTC）</Label><Input id="contest-end" type="datetime-local" value={form.endAt} disabled={!mutable} onChange={(event) => setForm({ ...form, endAt: event.target.value })} /></div>
      <div><Label htmlFor="contest-access">参赛模式</Label><select id="contest-access" disabled={!mutable} value={form.accessType} onChange={(event) => setForm({ ...form, accessType: event.target.value as ContestUpsert["accessType"] })} className="mt-1 h-9 w-full rounded border px-3 text-sm"><option value="OPEN">OPEN（学生可在开始前自行加入）</option><option value="INVITE_ONLY">INVITE_ONLY（仅管理员添加）</option></select></div>
      <div className="flex items-end"><Button disabled={saving || !mutable}>{saving ? "保存中..." : contestId ? "保存设置" : "创建比赛"}</Button></div>
    </Card></form>
    {detail && <>
      <div className="my-4 flex flex-wrap gap-2">{detail.contest.status === "DRAFT" && <Button onClick={() => void action(() => api.publishContest(detail.contest.id))}>发布比赛</Button>}{detail.contest.phase !== "ENDED" && detail.contest.phase !== "CANCELLED" && <Button variant="destructive" onClick={() => void action(() => api.cancelContest(detail.contest.id))}>取消比赛</Button>}{detail.contest.status === "DRAFT" && <Button variant="outline" onClick={async () => { await action(() => api.deleteContest(detail.contest.id)); navigate("/contests"); }}>删除草稿</Button>}</div>
      <Card className="mb-4 p-5"><h2 className="mb-3 font-semibold">比赛题目</h2><p className="mb-3 text-xs text-amber-700">PUBLIC 题目加入比赛后仍可从练习区提前查看；赛前保密请使用 CONTEST_ONLY。</p>
        {detail.problems.map((item, index) => <div key={item.contestProblemId} className="flex items-center gap-2 border-t py-2 text-sm"><span className="w-8 font-bold">{item.label}</span><span className="flex-1">{item.title} · {item.problemType}</span><Button aria-label={`上移 ${item.title}`} size="sm" variant="ghost" disabled={!mutable || index === 0} onClick={() => void move(item, -1)}><ArrowUp className="h-4 w-4" /></Button><Button aria-label={`下移 ${item.title}`} size="sm" variant="ghost" disabled={!mutable || index === detail.problems.length - 1} onClick={() => void move(item, 1)}><ArrowDown className="h-4 w-4" /></Button><Button aria-label={`移除 ${item.title}`} size="sm" variant="ghost" disabled={!mutable} onClick={() => void action(() => api.removeContestProblem(detail.contest.id, item.contestProblemId))}><Trash2 className="h-4 w-4 text-red-600" /></Button></div>)}
        <div className="mt-3 flex flex-wrap gap-2"><select aria-label="题目类型" value={problemType} disabled={!mutable} onChange={(event) => setProblemType(event.target.value as typeof problemType)} className="h-9 rounded border px-2 text-sm"><option value="ALGORITHM">算法题</option><option value="OFFICE">DOCX 题</option></select><Input aria-label="题目 ID" className="w-32" type="number" value={problemId} disabled={!mutable} onChange={(event) => setProblemId(event.target.value)} placeholder="题目 ID" /><Input aria-label="显示标签" className="w-28" value={label} disabled={!mutable} onChange={(event) => setLabel(event.target.value)} placeholder="标签(可选)" /><Button disabled={!mutable || !problemId} onClick={() => void action(() => api.addContestProblem(detail.contest.id, problemType, Number(problemId), label || undefined))}><Plus className="mr-1 h-4 w-4" />添加</Button></div>
      </Card>
      <Card className="p-5"><h2 className="mb-3 font-semibold">参赛者</h2>{participants.map((participant) => <div key={participant.id} className="flex items-center justify-between border-t py-2 text-sm"><span>{participant.username}（#{participant.userId}）</span><Button size="sm" variant="ghost" disabled={!mutable} onClick={() => void action(() => api.removeContestParticipant(detail.contest.id, participant.userId))}>移除</Button></div>)}
        <div className="mt-3 flex gap-2"><Input aria-label="学生用户 ID" className="w-40" type="number" value={participantUserId} disabled={!mutable} onChange={(event) => setParticipantUserId(event.target.value)} placeholder="学生用户 ID" /><Button disabled={!mutable || !participantUserId} onClick={() => void action(() => api.addContestParticipant(detail.contest.id, Number(participantUserId)))}>添加参赛者</Button></div>
      </Card>
    </>}
  </div>;
}

function toLocalInput(value: string) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function toApiForm(form: ContestUpsert): ContestUpsert {
  return { ...form, startAt: new Date(form.startAt).toISOString(), endAt: new Date(form.endAt).toISOString() };
}

function messageOf(exception: unknown) {
  return exception instanceof ApiError ? exception.message : "操作失败";
}
