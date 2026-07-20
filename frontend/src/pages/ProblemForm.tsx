import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Plus, Trash2, Save, Eye } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Markdown } from "@/components/Markdown";
import { api, type ProblemDetail, type ProblemUpsert, type Sample, ApiError } from "@/lib/api";
import { log, TAGS } from "@/lib/logger";

interface Props {
  mode: "create" | "edit";
  /** When editing, the problem fetched from the API (includes testCases for admin). */
  initial?: ProblemDetail;
}

const EMPTY: ProblemUpsert = {
  slug: "",
  title: "",
  description: "# 新题目\n\n在这里编写题面，支持 **Markdown** 和 $LaTeX$ 公式。\n\n## 题目描述\n\n",
  inputFmt: "",
  outputFmt: "",
  difficulty: "EASY",
  timeLimit: 1000,
  memoryLimit: 256,
  tags: [],
  samples: [{ input: "", output: "" }],
  testCases: [{ input: "", output: "" }],
  visible: true,
};

export default function ProblemForm({ mode, initial }: Props) {
  const navigate = useNavigate();
  const [form, setForm] = useState<ProblemUpsert>(EMPTY);
  const [tagsStr, setTagsStr] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  // Prefill when editing.
  useEffect(() => {
    if (mode === "edit" && initial) {
      setForm({
        slug: initial.slug,
        title: initial.title,
        description: initial.description,
        inputFmt: initial.inputFmt ?? "",
        outputFmt: initial.outputFmt ?? "",
        difficulty: initial.difficulty,
        timeLimit: initial.timeLimit,
        memoryLimit: initial.memoryLimit,
        tags: initial.tags ?? [],
        samples: initial.samples?.length ? initial.samples : [{ input: "", output: "" }],
        testCases: initial.testCases?.length ? initial.testCases : [{ input: "", output: "" }],
        visible: true,
      });
      setTagsStr((initial.tags ?? []).join(", "));
    }
  }, [mode, initial]);

  const update = <K extends keyof ProblemUpsert>(key: K, value: ProblemUpsert[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  // Dynamic list helpers (samples & testCases share the same shape).
  const updateItem = (key: "samples" | "testCases", idx: number, field: "input" | "output", val: string) =>
    setForm((f) => {
      const next = [...f[key]];
      next[idx] = { ...next[idx], [field]: val };
      return { ...f, [key]: next };
    });
  const addItem = (key: "samples" | "testCases") =>
    setForm((f) => ({ ...f, [key]: [...f[key], { input: "", output: "" }] }));
  const removeItem = (key: "samples" | "testCases", idx: number) =>
    setForm((f) => ({ ...f, [key]: f[key].filter((_, i) => i !== idx) }));

  const submit = async () => {
    setError("");
    if (!form.slug.trim() || !form.title.trim() || !form.description.trim()) {
      setError("slug、标题、题面描述不能为空");
      return;
    }
    const tags = tagsStr.split(",").map((t) => t.trim()).filter(Boolean);
    const payload: ProblemUpsert = {
      ...form,
      tags,
      samples: form.samples.filter((s) => s.input.trim() || s.output.trim()),
      testCases: form.testCases.filter((s) => s.input.trim() || s.output.trim()),
    };
    if (payload.testCases.length === 0) {
      setError("至少需要 1 个测试点");
      return;
    }
    setSaving(true);
    try {
      if (mode === "create") {
        await api.createProblem(payload);
        log.info(TAGS.app, `题目创建成功: ${payload.slug}`);
      } else {
        await api.updateProblem(form.slug, payload);
        log.info(TAGS.app, `题目更新成功: ${payload.slug}`);
      }
      navigate("/admin/problems");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="px-4 py-6 sm:px-6 lg:px-8">
      <button
        onClick={() => navigate("/admin/problems")}
        className="mb-3 inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-900"
      >
        <ArrowLeft className="h-4 w-4" /> 返回管理
      </button>

      <h1 className="mb-4 text-xl font-bold">
        {mode === "create" ? "新建题目" : `编辑题目：${initial?.title ?? ""}`}
      </h1>

      {error && (
        <div className="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="space-y-4">
        {/* 基本信息 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">基本信息</CardTitle>
            <CardDescription>题目的标识与显示属性</CardDescription>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Slug（URL 标识）</Label>
              <Input
                value={form.slug}
                onChange={(e) => update("slug", e.target.value)}
                placeholder="a-plus-b"
                disabled={mode === "edit"}
              />
              <p className="text-xs text-zinc-500">小写字母、数字、连字符，编辑后不可改</p>
            </div>
            <div className="space-y-1.5">
              <Label>标题</Label>
              <Input value={form.title} onChange={(e) => update("title", e.target.value)} placeholder="A + B 问题" />
            </div>
            <div className="space-y-1.5">
              <Label>难度</Label>
              <select
                value={form.difficulty}
                onChange={(e) => update("difficulty", e.target.value as ProblemUpsert["difficulty"])}
                className="h-9 w-full rounded-md border border-zinc-200 bg-transparent px-3 text-sm"
              >
                <option value="EASY">简单</option>
                <option value="MEDIUM">中等</option>
                <option value="HARD">困难</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>标签（逗号分隔）</Label>
              <Input value={tagsStr} onChange={(e) => setTagsStr(e.target.value)} placeholder="入门, 数学" />
              <div className="flex flex-wrap gap-1">
                {tagsStr.split(",").map((t) => t.trim()).filter(Boolean).map((t, i) => (
                  <Badge key={i} variant="secondary" className="text-xs">{t}</Badge>
                ))}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>时间限制 (ms)</Label>
              <Input
                type="number"
                value={form.timeLimit}
                onChange={(e) => update("timeLimit", Number(e.target.value))}
                min={100}
                max={30000}
              />
            </div>
            <div className="space-y-1.5">
              <Label>内存限制 (MB)</Label>
              <Input
                type="number"
                value={form.memoryLimit}
                onChange={(e) => update("memoryLimit", Number(e.target.value))}
                min={32}
                max={1024}
              />
            </div>
            <div className="flex items-center gap-2 md:col-span-2">
              <Switch checked={form.visible} onCheckedChange={(v) => update("visible", v)} id="visible" />
              <Label htmlFor="visible" className="cursor-pointer">对普通用户可见</Label>
            </div>
          </CardContent>
        </Card>

        {/* 题面描述 + 实时预览 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">题面描述</CardTitle>
            <CardDescription>支持 Markdown 与 LaTeX 公式（$E=mc^2$ 行内，$$...$$ 块级）</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
              <div className="space-y-1.5">
                <Label className="flex items-center gap-1.5"><Eye className="h-3.5 w-3.5" /> 编辑</Label>
                <Textarea
                  value={form.description}
                  onChange={(e) => update("description", e.target.value)}
                  rows={20}
                  className="font-mono text-[13px]"
                  placeholder="# 题目标题&#10;&#10;## 题目描述&#10;&#10;..."
                />
              </div>
              <div className="space-y-1.5">
                <Label className="text-zinc-500">预览</Label>
                <div className="min-h-[480px] rounded-md border border-zinc-200 bg-white p-4 overflow-auto">
                  <Markdown>{form.description || "*预览区*"}</Markdown>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 输入输出格式 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">输入 / 输出格式说明</CardTitle>
            <CardDescription>向学生说明输入输出的格式（可选，支持 Markdown）</CardDescription>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-1.5">
              <Label>输入格式</Label>
              <Textarea
                value={form.inputFmt}
                onChange={(e) => update("inputFmt", e.target.value)}
                rows={4}
                placeholder="一行，两个整数 a 和 b，以空格分隔。"
              />
            </div>
            <div className="space-y-1.5">
              <Label>输出格式</Label>
              <Textarea
                value={form.outputFmt}
                onChange={(e) => update("outputFmt", e.target.value)}
                rows={4}
                placeholder="一个整数 a+b。"
              />
            </div>
          </CardContent>
        </Card>

        {/* 样例 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">样例（展示给学生）</CardTitle>
            <CardDescription>学生可见的输入输出示例</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {form.samples.map((s, i) => (
              <SampleRow key={i} idx={i} sample={s} onChange={(f, v) => updateItem("samples", i, f, v)} onRemove={() => removeItem("samples", i)} />
            ))}
            <Button variant="outline" size="sm" onClick={() => addItem("samples")} className="gap-1.5">
              <Plus className="h-4 w-4" /> 添加样例
            </Button>
          </CardContent>
        </Card>

        {/* 测试点 */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">测试点（隐藏，用于评测）</CardTitle>
            <CardDescription>学生不可见，提交代码后会逐个运行判定</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            {form.testCases.map((s, i) => (
              <SampleRow key={i} idx={i} sample={s} onChange={(f, v) => updateItem("testCases", i, f, v)} onRemove={() => removeItem("testCases", i)} />
            ))}
            <Button variant="outline" size="sm" onClick={() => addItem("testCases")} className="gap-1.5">
              <Plus className="h-4 w-4" /> 添加测试点
            </Button>
          </CardContent>
        </Card>

        {/* 操作按钮 */}
        <div className="flex items-center gap-2 pb-8">
          <Button onClick={submit} disabled={saving} className="gap-1.5">
            <Save className="h-4 w-4" /> {saving ? "保存中..." : "保存题目"}
          </Button>
          <Button variant="ghost" onClick={() => navigate("/admin/problems")}>
            取消
          </Button>
        </div>
      </div>
    </div>
  );
}

function SampleRow({
  idx,
  sample,
  onChange,
  onRemove,
}: {
  idx: number;
  sample: Sample;
  onChange: (field: "input" | "output", val: string) => void;
  onRemove: () => void;
}) {
  return (
    <div className="rounded-md border border-zinc-200 p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-xs font-medium text-zinc-500">#{idx + 1}</span>
        <Button variant="ghost" size="sm" onClick={onRemove} className="h-7 gap-1 text-red-600 hover:text-red-700">
          <Trash2 className="h-3.5 w-3.5" /> 删除
        </Button>
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <div className="space-y-1">
          <Label className="text-xs text-zinc-500">输入</Label>
          <Textarea
            value={sample.input}
            onChange={(e) => onChange("input", e.target.value)}
            rows={3}
            className="font-mono text-[13px]"
            placeholder="1 2"
          />
        </div>
        <div className="space-y-1">
          <Label className="text-xs text-zinc-500">期望输出</Label>
          <Textarea
            value={sample.output}
            onChange={(e) => onChange("output", e.target.value)}
            rows={3}
            className="font-mono text-[13px]"
            placeholder="3"
          />
        </div>
      </div>
    </div>
  );
}
