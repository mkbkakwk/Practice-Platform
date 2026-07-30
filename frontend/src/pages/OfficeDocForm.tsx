import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Upload, ArrowLeft, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";

const DIFFS = [{ key: "EASY", label: "简单" }, { key: "MEDIUM", label: "中等" }, { key: "HARD", label: "困难" }];
const DEFAULT_DESCRIPTION = "# 排版要求\n\n1. 标题：黑体、三号、居中\n2. 正文：宋体、小四、首行缩进2字符、1.5倍行距\n3. 落款：楷体、小四、右对齐";

export default function OfficeDocForm({ mode }: { mode: "create" | "edit" }) {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const fileRef = useRef<HTMLInputElement>(null);
  const [currentId, setCurrentId] = useState<number | null>(mode === "edit" ? Number(id) : null);
  const [title, setTitle] = useState("");
  const [difficulty, setDifficulty] = useState("EASY");
  const [description, setDescription] = useState(DEFAULT_DESCRIPTION);
  const [visible, setVisible] = useState(true);
  const [teacherDocName, setTeacherDocName] = useState<string | null>(null);
  const [teacherFile, setTeacherFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(mode === "edit");
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (mode !== "edit" || !id) return;
    let active = true;
    api.getDocExercise(Number(id)).then(({ exercise }) => {
      if (!active) return;
      setCurrentId(exercise.id);
      setTitle(exercise.title);
      setDifficulty(exercise.difficulty);
      setDescription(exercise.description);
      setVisible(exercise.visible);
      setTeacherDocName(exercise.teacherDocName);
    }).catch((exception) => active && setError(exception instanceof ApiError ? exception.message : "加载失败"))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [id, mode]);

  async function handleSave(event: React.FormEvent) {
    event.preventDefault();
    if (!title.trim() || !description.trim()) { setError("请填写标题和要求"); return; }
    setSaving(true); setSaved(false); setError(null);
    try {
      const payload = { title: title.trim(), difficulty, description: description.trim(), visible };
      if (currentId) {
        await api.updateDocExercise(currentId, payload);
      } else {
        const { exercise } = await api.createDocExercise(payload);
        setCurrentId(exercise.id);
      }
      setSaved(true);
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleUploadTeacher() {
    if (!teacherFile || !currentId) return;
    setUploading(true); setError(null);
    try {
      await api.uploadTeacherDoc(currentId, teacherFile);
      setTeacherDocName(teacherFile.name);
      setTeacherFile(null);
      if (fileRef.current) fileRef.current.value = "";
    } catch (exception) {
      setError(exception instanceof ApiError ? exception.message : "上传参考文档失败");
    } finally {
      setUploading(false);
    }
  }

  if (loading) return <div className="flex justify-center py-20"><Loader2 className="h-6 w-6 animate-spin text-zinc-400" /></div>;

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6 lg:px-8">
      <Button variant="ghost" size="sm" className="mb-4" onClick={() => navigate("/admin/office-doc")}><ArrowLeft className="mr-1 h-4 w-4" />返回管理</Button>
      <h1 className="mb-4 text-2xl font-bold">{mode === "create" && !currentId ? "新建排版练习" : "编辑排版练习"}</h1>
      <form onSubmit={handleSave} className="space-y-5">
        <Card className="space-y-4 p-5">
          <div><Label className="mb-1.5 block text-xs">标题</Label><Input value={title} onChange={(event) => setTitle(event.target.value)} /></div>
          <div><Label className="mb-1.5 block text-xs">难度</Label><div className="flex gap-2">{DIFFS.map((item) => <Button key={item.key} type="button" size="sm" variant={difficulty === item.key ? "default" : "outline"} onClick={() => setDifficulty(item.key)}>{item.label}</Button>)}</div></div>
          <div><Label className="mb-1.5 block text-xs">排版要求（Markdown）</Label><textarea className="min-h-32 w-full rounded-md border border-zinc-200 p-3 text-sm" value={description} onChange={(event) => setDescription(event.target.value)} /></div>
          <div className="flex items-center justify-between"><div><Label className="text-sm font-semibold">启用状态</Label><p className="text-xs text-zinc-400">停用后学生不能查看或提交，历史数据保留</p></div><button type="button" onClick={() => setVisible(!visible)} className={cn("relative h-6 w-11 rounded-full", visible ? "bg-zinc-900" : "bg-zinc-300")}><span className={cn("absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform", visible ? "translate-x-5" : "translate-x-0.5")} /></button></div>
        </Card>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex items-center gap-3"><Button type="submit" disabled={saving}>{saving && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}{currentId ? "保存信息" : "创建练习"}</Button>{saved && <span className="text-sm text-green-600">✓ 已保存</span>}</div>
      </form>

      {currentId && <Card className="mt-5 p-5">
        <h2 className="mb-2 text-sm font-semibold">参考文档</h2>
        {teacherDocName && <p className="mb-3 flex items-center gap-1 text-sm text-green-700"><CheckCircle2 className="h-4 w-4" />当前文件：{teacherDocName}</p>}
        <p className="mb-3 text-xs text-zinc-500">上传新文件会替换当前参考文档；旧文件在确认未被其他记录使用后清理。</p>
        <input ref={fileRef} type="file" accept=".docx" className="hidden" onChange={(event) => setTeacherFile(event.target.files?.[0] ?? null)} />
        <div className="flex items-center gap-3"><Button variant="outline" size="sm" onClick={() => fileRef.current?.click()}><Upload className="mr-1 h-4 w-4" />选择 .docx</Button>{teacherFile && <span className="text-sm text-zinc-600">{teacherFile.name}</span>}</div>
        {teacherFile && <Button className="mt-3" size="sm" onClick={() => void handleUploadTeacher()} disabled={uploading}>{uploading && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}{uploading ? "上传中..." : "上传参考文档"}</Button>}
      </Card>}
      {currentId && <div className="mt-5 flex justify-end gap-2"><Button variant="outline" onClick={() => navigate("/admin/office-doc")}>完成</Button><Button asChild><a href={`#/office/docs/${currentId}`}>查看练习</a></Button></div>}
    </div>
  );
}
