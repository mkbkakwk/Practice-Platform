import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { api, type OfficeQuestionDetail, type OfficeSubmitResult } from "@/lib/api";
import { DIFFICULTY_CLASS, DIFFICULTY_LABEL } from "@/lib/verdict";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, CheckCircle2, XCircle, ArrowLeft, ArrowRight, LogIn } from "lucide-react";
import { cn } from "@/lib/utils";

const APP_LABEL: Record<string, string> = { WORD: "Word", EXCEL: "Excel", PPT: "PPT" };
const APP_CLASS: Record<string, string> = {
  WORD: "bg-blue-100 text-blue-700 border-blue-200",
  EXCEL: "bg-green-100 text-green-700 border-green-200",
  PPT: "bg-orange-100 text-orange-700 border-orange-200",
};
const QTYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: "单选题",
  MULTI_CHOICE: "多选题",
  TRUE_FALSE: "判断题",
};

export default function OfficePractice() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const qid = Number(id);

  const [question, setQuestion] = useState<OfficeQuestionDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<OfficeSubmitResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isFinite(qid)) {
      setError("无效的题目 ID");
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    setSelected(new Set());
    setResult(null);
    setError(null);
    api
      .getOfficeQuestion(qid)
      .then((data) => {
        if (!active) return;
        setQuestion(data.question);
      })
      .catch((e: Error) => {
        if (!active) return;
        setError(e.message || "加载题目失败");
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [qid]);

  const isMulti = question?.questionType === "MULTI_CHOICE";
  const isTrueFalse = question?.questionType === "TRUE_FALSE";

  function toggleOption(idx: string) {
    if (result) return; // lock after submit
    setSelected((prev) => {
      const next = new Set(prev);
      if (isMulti) {
        if (next.has(idx)) next.delete(idx);
        else next.add(idx);
      } else {
        next.clear();
        next.add(idx);
      }
      return next;
    });
  }

  async function handleSubmit() {
    if (!question || selected.size === 0) return;
    if (!user) {
      setError("请先登录后再答题");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const { result: r } = await api.submitOfficeAnswer(question.id, Array.from(selected));
      setResult(r);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "提交失败");
    } finally {
      setSubmitting(false);
    }
  }

  function handleNext() {
    // Naive next: increment id. The list is ordered by id asc.
    navigate(`/office/${qid + 1}`);
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center px-4 py-20">
        <Loader2 className="h-6 w-6 animate-spin text-zinc-400" />
      </div>
    );
  }

  if (error && !question) {
    return (
      <div className="px-4 py-6 sm:px-6 lg:px-8">
        <Card className="p-8 text-center">
          <p className="text-zinc-500">{error}</p>
          <Button variant="outline" size="sm" className="mt-4" asChild>
            <Link to="/office">返回题库</Link>
          </Button>
        </Card>
      </div>
    );
  }

  if (!question) return null;

  return (
    <div className="mx-auto max-w-3xl px-4 py-6 sm:px-6 lg:px-8">
      <Link to="/office" className="mb-4 inline-flex items-center gap-1 text-sm text-zinc-500 hover:text-zinc-800">
        <ArrowLeft className="h-4 w-4" /> 返回题库
      </Link>

      {/* Question header */}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        {question.appType && (
          <span
            className={cn(
              "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
              APP_CLASS[question.appType],
            )}
          >
            {APP_LABEL[question.appType]}
          </span>
        )}
        <span className="rounded-md border border-zinc-200 bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-600 whitespace-nowrap">
          {QTYPE_LABEL[question.questionType]}
        </span>
        <span
          className={cn(
            "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
            DIFFICULTY_CLASS[question.difficulty],
          )}
        >
          {DIFFICULTY_LABEL[question.difficulty]}
        </span>
        <span className="text-xs text-zinc-400">#{question.id}</span>
        <span className="ml-auto text-xs text-zinc-400">{question.category}</span>
      </div>

      {/* Question content */}
      <Card className="mb-4 p-5">
        <p className="whitespace-pre-wrap text-base leading-relaxed text-zinc-900">{question.content}</p>
        {isMulti && (
          <p className="mt-3 text-xs text-zinc-500">提示：本题有多项正确答案，请选择全部正确选项。</p>
        )}
      </Card>

      {/* Options */}
      <div className="space-y-2">
        {question.options.map((opt, idx) => {
          const optKey = String(idx);
          const isSelected = selected.has(optKey);
          const isCorrectOpt = result && result.correctAnswer.split(",").includes(optKey);
          const isWrongPick = result && isSelected && !isCorrectOpt;
          return (
            <button
              key={idx}
              type="button"
              disabled={!!result}
              onClick={() => toggleOption(optKey)}
              className={cn(
                "flex w-full items-start gap-3 rounded-lg border p-3 text-left text-sm transition-colors",
                result
                  ? isCorrectOpt
                    ? "border-green-300 bg-green-50"
                    : isWrongPick
                      ? "border-red-300 bg-red-50"
                      : "border-zinc-200 bg-white"
                  : isSelected
                    ? "border-zinc-900 bg-zinc-50"
                    : "border-zinc-200 bg-white hover:border-zinc-400 hover:bg-zinc-50",
                !result && "cursor-pointer",
              )}
            >
              <span
                className={cn(
                  "mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-xs font-medium",
                  isSelected
                    ? "border-zinc-900 bg-zinc-900 text-white"
                    : "border-zinc-300 text-zinc-500",
                )}
              >
                {isTrueFalse ? (idx === 0 ? "T" : "F") : String.fromCharCode(65 + idx)}
              </span>
              <span className="flex-1 text-zinc-800">{opt}</span>
              {result && isCorrectOpt && <CheckCircle2 className="h-5 w-5 shrink-0 text-green-600" />}
              {result && isWrongPick && <XCircle className="h-5 w-5 shrink-0 text-red-600" />}
            </button>
          );
        })}
      </div>

      {/* Login prompt if not logged in */}
      {!user && (
        <div className="mt-4 flex items-center gap-2 rounded-md border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800">
          <LogIn className="h-4 w-4" />
          <span>答题需登录后提交，</span>
          <Link to="/login" className="font-medium underline">
            去登录
          </Link>
        </div>
      )}

      {/* Error */}
      {error && <p className="mt-4 text-sm text-red-600">{error}</p>}

      {/* Feedback */}
      {result && (
        <Card className="mt-4 p-4">
          <div
            className={cn(
              "mb-2 flex items-center gap-2 font-semibold",
              result.correct ? "text-green-700" : "text-red-700",
            )}
          >
            {result.correct ? <CheckCircle2 className="h-5 w-5" /> : <XCircle className="h-5 w-5" />}
            {result.correct ? "回答正确" : "回答错误"}
          </div>
          {!result.correct && (
            <p className="mb-2 text-sm text-zinc-700">
              正确答案：
              <span className="font-medium text-green-700">
                {result.correctAnswer
                  .split(",")
                  .map((a) => {
                    const i = Number(a);
                    if (isTrueFalse) return i === 0 ? "正确" : "错误";
                    return question.options[i] || String.fromCharCode(65 + i);
                  })
                  .join("、")}
              </span>
            </p>
          )}
          {result.explanation && (
            <div className="rounded-md bg-zinc-50 p-3 text-sm leading-relaxed text-zinc-700">
              <span className="font-medium">解析：</span>
              {result.explanation}
            </div>
          )}
        </Card>
      )}

      {/* Actions */}
      <div className="mt-5 flex items-center justify-between">
        <Button variant="outline" size="sm" asChild>
          <Link to="/office">
            <ArrowLeft className="mr-1 h-4 w-4" /> 题库
          </Link>
        </Button>
        <div className="flex gap-2">
          {!result ? (
            <Button onClick={handleSubmit} disabled={submitting || selected.size === 0 || !user}>
              {submitting ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : null}
              提交答案
            </Button>
          ) : (
            <Button onClick={handleNext} variant="default" size="sm">
              下一题 <ArrowRight className="ml-1 h-4 w-4" />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
