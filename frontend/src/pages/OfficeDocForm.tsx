import { useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Upload, ArrowLeft, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/utils";

const DIFFS = [
  { key: "EASY", label: "简单" },
  { key: "MEDIUM", label: "中等" },
  { key: "HARD", label: "困难" },
];

export default function OfficeDocForm() {
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);

  const [title, setTitle] = useState("");
  const [difficulty, setDifficulty] = useState("EASY");
  const [description, setDescription] = useState("# 排版要求\n\n1. 标题：黑体、三号、居中\n2. 正文：宋体、小四、首行缩进2字符、1.5倍行距\n3. 落款：楷体、小四、右对齐");
  const [visible, setVisible] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<number | null>(null);

  // teacher doc upload
  const [teacherFile, setTeacherFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploaded, setUploaded] = useState(false);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!title.trim() || !description.trim()) {
      setError("请填写标题和要求");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const { exercise } = await api.createDocExercise({ title, difficulty, description, visible });
      setCreatedId(exercise.id);
    } catch (err: any) {
      setError(err?.message || "创建失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleUploadTeacher() {
    if (!teacherFile || !createdId) return;
    setUploading(true);
    setError(null);
    try {
      await api.uploadTeacherDoc(createdId, teacherFile);
      setUploaded(true);
    } catch (err: any) {
      setError(err?.message || "上传参考文档失败");
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-6 sm:px-6 lg:px-8">
      <Button variant="ghost" size="sm" className="mb-4" onClick={() => navigate("/office/docs")}>
        <ArrowLeft className="mr-1 h-4 w-4" /> 返回列表
      </Button>
      <h1 className="mb-4 text-2xl font-bold">新建排版练习</h1>

      {!createdId ? (
        <form onSubmit={handleCreate} className="space-y-5">
          <Card className="space-y-4 p-5">
            <div>
              <Label className="mb-1.5 block text-xs">标题</Label>
              <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="如：期末报告排版练习" />
            </div>
            <div>
              <Label className="mb-1.5 block text-xs">难度</Label>
              <div className="flex gap-2">
                {DIFFS.map((d) => (
                  <Button key={d.key} type="button" size="sm" variant={difficulty === d.key ? "default" : "outline"} onClick={() => setDifficulty(d.key)}>
                    {d.label}
                  </Button>
                ))}
              </div>
            </div>
            <div>
              <Label className="mb-1.5 block text-xs">排版要求（Markdown 格式）</Label>
              <textarea
                className="min-h-32 w-full rounded-md border border-zinc-200 p-3 text-sm leading-relaxed"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <p className="mt-1 text-xs text-zinc-400">学生会看到这些要求，请写清楚每个段落应使用的字体、字号、对齐方式等</p>
            </div>
            <div className="flex items-center justify-between">
              <Label className="text-sm font-semibold">是否可见</Label>
              <button type="button" onClick={() => setVisible(!visible)} className={cn("relative h-6 w-11 rounded-full transition-colors", visible ? "bg-zinc-900" : "bg-zinc-300")}>
                <span className={cn("absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform", visible ? "translate-x-5" : "translate-x-0.5")} />
              </button>
            </div>
          </Card>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end">
            <Button type="submit" disabled={saving}>
              {saving && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
              创建练习
            </Button>
          </div>
        </form>
      ) : (
        <div className="space-y-4">
          <Card className="border-green-200 bg-green-50 p-5">
            <div className="flex items-center gap-2 text-green-700">
              <CheckCircle2 className="h-5 w-5" />
              <span className="font-medium">练习已创建（#{createdId}）</span>
            </div>
            <p className="mt-2 text-sm text-zinc-600">接下来请上传老师的参考文档（.docx），系统会以此为标准自动比对学生的提交。</p>
          </Card>

          <Card className="p-5">
            <h2 className="mb-3 text-sm font-semibold text-zinc-700">上传老师参考文档</h2>
            <input
              ref={fileRef}
              type="file"
              accept=".docx"
              className="hidden"
              onChange={(e) => {
                setTeacherFile(e.target.files?.[0] ?? null);
                setUploaded(false);
              }}
            />
            <div className="flex items-center gap-3">
              <Button variant="outline" size="sm" onClick={() => fileRef.current?.click()}>
                <Upload className="mr-1 h-4 w-4" /> 选择 .docx
              </Button>
              {teacherFile && <span className="text-sm text-zinc-600">{teacherFile.name}</span>}
            </div>
            {teacherFile && !uploaded && (
              <Button className="mt-3" size="sm" onClick={handleUploadTeacher} disabled={uploading}>
                {uploading ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : null}
                {uploading ? "上传中..." : "上传参考文档"}
              </Button>
            )}
            {uploaded && (
              <p className="mt-3 text-sm text-green-600">✓ 参考文档已上传，学生现在可以提交了</p>
            )}
            {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
          </Card>

          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => navigate("/office/docs")}>完成</Button>
            <Button asChild>
              <a href={`#/office/docs/${createdId}`}>查看练习</a>
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
