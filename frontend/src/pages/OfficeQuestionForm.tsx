import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, type OfficeQuestionUpsert, type OfficeAppType, type OfficeQuestionType } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Plus, Trash2, ArrowLeft } from "lucide-react";
import { cn } from "@/lib/utils";

const APP_TYPES: OfficeAppType[] = ["WORD", "EXCEL", "PPT"];
const Q_TYPES: OfficeQuestionType[] = ["SINGLE_CHOICE", "MULTI_CHOICE", "TRUE_FALSE"];
const DIFFS = ["EASY", "MEDIUM", "HARD"] as const;

const APP_LABEL: Record<string, string> = { WORD: "Word", EXCEL: "Excel", PPT: "PPT" };
const QTYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: "单选题",
  MULTI_CHOICE: "多选题",
  TRUE_FALSE: "判断题",
};
const DIFF_LABEL: Record<string, string> = { EASY: "简单", MEDIUM: "中等", HARD: "困难" };

interface FormState {
  appType: OfficeAppType;
  category: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  questionType: OfficeQuestionType;
  content: string;
  options: string[];
  answer: string;
  explanation: string;
  visible: boolean;
  contentVisibility: "PUBLIC" | "CONTEST_ONLY";
}

function emptyForm(): FormState {
  return {
    appType: "WORD",
    category: "",
    difficulty: "EASY",
    questionType: "SINGLE_CHOICE",
    content: "",
    options: ["", ""],
    answer: "",
    explanation: "",
    visible: true,
    contentVisibility: "PUBLIC",
  };
}

export default function OfficeQuestionForm({ mode }: { mode: "create" | "edit" }) {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [form, setForm] = useState<FormState>(emptyForm());
  const [loading, setLoading] = useState(mode === "edit");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (mode !== "edit" || !id) return;
    let active = true;
    setLoading(true);
    api
      .getOfficeQuestion(Number(id))
      .then(({ question }) => {
        if (!active) return;
        setForm({
          appType: question.appType,
          category: question.category,
          difficulty: question.difficulty,
          questionType: question.questionType,
          content: question.content,
          options: question.options && question.options.length > 0 ? question.options : ["", ""],
          answer: question.answer || "",
          explanation: question.explanation || "",
          visible: question.visible ?? true,
          contentVisibility: question.contentVisibility ?? "PUBLIC",
        });
      })
      .catch((e: Error) => active && setError(e.message))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [mode, id]);

  function patch(p: Partial<FormState>) {
    setForm((f) => ({ ...f, ...p }));
  }

  function changeOption(idx: number, val: string) {
    setForm((f) => {
      const opts = [...f.options];
      opts[idx] = val;
      return { ...f, options: opts };
    });
  }

  function addOption() {
    setForm((f) => ({ ...f, options: [...f.options, ""] }));
  }

  function removeOption(idx: number) {
    setForm((f) => {
      if (f.options.length <= 2) return f; // keep at least 2
      const opts = f.options.filter((_, i) => i !== idx);
      // adjust answer indices
      const ansIdxs = f.answer.split(",").map((s) => s.trim());
      const newAns = ansIdxs
        .map((a) => {
          const n = Number(a);
          if (n === idx) return null;
          if (n > idx) return String(n - 1);
          return a;
        })
        .filter((x): x is string => x !== null)
        .join(",");
      return { ...f, options: opts, answer: newAns };
    });
  }

  function setTrueFalseOptions() {
    patch({ options: ["正确", "错误"], answer: "", questionType: "TRUE_FALSE" });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    // validation
    if (!form.content.trim()) {
      setError("请输入题目内容");
      return;
    }
    if (form.questionType !== "TRUE_FALSE" && form.options.some((o) => !o.trim())) {
      setError("选项不能为空");
      return;
    }
    if (!form.answer.trim()) {
      setError("请输入正确答案");
      return;
    }
    if (!form.category.trim()) {
      setError("请输入分类");
      return;
    }

    const payload: OfficeQuestionUpsert = {
      ...form,
      options: form.questionType === "TRUE_FALSE" ? ["正确", "错误"] : form.options.map((o) => o.trim()),
      content: form.content.trim(),
      category: form.category.trim(),
      answer: form.answer.trim(),
      explanation: form.explanation.trim(),
    };

    setSaving(true);
    try {
      if (mode === "create") {
        await api.createOfficeQuestion(payload);
      } else {
        await api.updateOfficeQuestion(Number(id), payload);
      }
      navigate("/admin/office");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center px-4 py-20">
        <Loader2 className="h-6 w-6 animate-spin text-zinc-400" />
      </div>
    );
  }

  const isTrueFalse = form.questionType === "TRUE_FALSE";

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 sm:px-6 lg:px-8">
      <Button variant="ghost" size="sm" className="mb-4" onClick={() => navigate("/admin/office")}>
        <ArrowLeft className="mr-1 h-4 w-4" /> 返回列表
      </Button>
      <h1 className="mb-4 text-2xl font-bold">{mode === "create" ? "新建 Office 题目" : "编辑 Office 题目"}</h1>

      <form onSubmit={handleSubmit} className="space-y-5">
        {/* Basic info */}
        <Card className="space-y-4 p-5">
          <h2 className="text-sm font-semibold text-zinc-700">基本信息</h2>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
            <div>
              <Label className="mb-1.5 block text-xs">应用</Label>
              <select
                className="h-9 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm"
                value={form.appType}
                onChange={(e) => patch({ appType: e.target.value as OfficeAppType })}
              >
                {APP_TYPES.map((a) => (
                  <option key={a} value={a}>
                    {APP_LABEL[a]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label className="mb-1.5 block text-xs">题型</Label>
              <select
                className="h-9 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm"
                value={form.questionType}
                onChange={(e) => {
                  const qt = e.target.value as OfficeQuestionType;
                  if (qt === "TRUE_FALSE") {
                    setTrueFalseOptions();
                  } else {
                    patch({ questionType: qt });
                  }
                }}
              >
                {Q_TYPES.map((q) => (
                  <option key={q} value={q}>
                    {QTYPE_LABEL[q]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <Label className="mb-1.5 block text-xs">难度</Label>
              <select
                className="h-9 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm"
                value={form.difficulty}
                onChange={(e) => patch({ difficulty: e.target.value as "EASY" | "MEDIUM" | "HARD" })}
              >
                {DIFFS.map((d) => (
                  <option key={d} value={d}>
                    {DIFF_LABEL[d]}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <Label className="mb-1.5 block text-xs">分类（如：文字排版 / 公式函数 / 动画）</Label>
            <Input
              value={form.category}
              onChange={(e) => patch({ category: e.target.value })}
              placeholder="分类标签"
            />
          </div>
        </Card>

        {/* Question content */}
        <Card className="space-y-2 p-5">
          <Label className="text-sm font-semibold text-zinc-700">题目内容</Label>
          <textarea
            className="min-h-24 w-full rounded-md border border-zinc-200 p-3 text-sm leading-relaxed"
            value={form.content}
            onChange={(e) => patch({ content: e.target.value })}
            placeholder="输入题目内容..."
          />
        </Card>

        {/* Options */}
        <Card className="space-y-3 p-5">
          <div className="flex items-center justify-between">
            <Label className="text-sm font-semibold text-zinc-700">
              选项 {isTrueFalse && <span className="text-xs font-normal text-zinc-400">（判断题固定为 正确/错误）</span>}
            </Label>
            {!isTrueFalse && (
              <Button type="button" variant="outline" size="sm" onClick={addOption}>
                <Plus className="mr-1 h-4 w-4" /> 添加选项
              </Button>
            )}
          </div>
          {isTrueFalse ? (
            <div className="space-y-2">
              <div className="flex items-center gap-2 rounded-md border border-zinc-200 p-2 text-sm">
                <span className="flex h-5 w-5 items-center justify-center rounded-full border text-xs">T</span>
                正确
              </div>
              <div className="flex items-center gap-2 rounded-md border border-zinc-200 p-2 text-sm">
                <span className="flex h-5 w-5 items-center justify-center rounded-full border text-xs">F</span>
                错误
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              {form.options.map((opt, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs font-medium text-zinc-500">
                    {String.fromCharCode(65 + idx)}
                  </span>
                  <Input
                    value={opt}
                    onChange={(e) => changeOption(idx, e.target.value)}
                    placeholder={`选项 ${String.fromCharCode(65 + idx)}`}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => removeOption(idx)}
                    disabled={form.options.length <= 2}
                  >
                    <Trash2 className="h-4 w-4 text-zinc-400" />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Answer */}
        <Card className="space-y-2 p-5">
          <Label className="text-sm font-semibold text-zinc-700">正确答案</Label>
          <p className="text-xs text-zinc-500">
            {isTrueFalse
              ? "填 T（正确）或 F（错误）"
              : form.questionType === "MULTI_CHOICE"
                ? "多选用逗号分隔选项序号(0-based)，如 0,2 表示第 1、3 项"
                : "填选项序号(0-based)，如 0 表示第 1 项"}
          </p>
          <Input
            value={form.answer}
            onChange={(e) => patch({ answer: e.target.value })}
            placeholder={isTrueFalse ? "T" : form.questionType === "MULTI_CHOICE" ? "0,2" : "0"}
          />
        </Card>

        {/* Explanation */}
        <Card className="space-y-2 p-5">
          <Label className="text-sm font-semibold text-zinc-700">解析（可选）</Label>
          <textarea
            className="min-h-16 w-full rounded-md border border-zinc-200 p-3 text-sm leading-relaxed"
            value={form.explanation}
            onChange={(e) => patch({ explanation: e.target.value })}
            placeholder="答题后展示给用户的解析说明..."
          />
        </Card>

        {/* Visible */}
        <Card className="flex items-center justify-between p-5">
          <div>
            <Label className="text-sm font-semibold text-zinc-700">是否可见</Label>
            <p className="text-xs text-zinc-500">关闭后普通用户不可见，仅管理员可见</p>
          </div>
          <button
            type="button"
            onClick={() => patch({ visible: !form.visible })}
            className={cn(
              "relative h-6 w-11 rounded-full transition-colors",
              form.visible ? "bg-zinc-900" : "bg-zinc-300",
            )}
          >
            <span
              className={cn(
                "absolute top-0.5 h-5 w-5 rounded-full bg-white transition-transform",
                form.visible ? "translate-x-5" : "translate-x-0.5",
              )}
            />
          </button>
        </Card>

        <Card className="space-y-2 p-5">
          <Label htmlFor="office-question-visibility" className="text-sm font-semibold text-zinc-700">内容可见范围</Label>
          <select id="office-question-visibility" value={form.contentVisibility}
            onChange={(event) => patch({ contentVisibility: event.target.value as FormState["contentVisibility"] })}
            className="h-9 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm">
            <option value="PUBLIC">PUBLIC（练习区公开）</option>
            <option value="CONTEST_ONLY">CONTEST_ONLY（比赛开始后仅参赛者可见）</option>
          </select>
        </Card>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={() => navigate("/admin/office")}>
            取消
          </Button>
          <Button type="submit" disabled={saving}>
            {saving && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
            {mode === "create" ? "创建题目" : "保存修改"}
          </Button>
        </div>
      </form>
    </div>
  );
}
