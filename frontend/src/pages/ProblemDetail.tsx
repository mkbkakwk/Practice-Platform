import { useEffect, useRef, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { CodeEditor } from "@/components/CodeEditor";
import { Markdown } from "@/components/Markdown";
import {
  api,
  type ProblemDetail,
  type LanguageDef,
  type Verdict,
  ApiError,
  getApiErrorMessage,
  isAbortError,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { VerdictBadge, DIFFICULTY_LABEL, DIFFICULTY_CLASS } from "@/lib/verdict";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Loader2,
  Send,
  Play,
  ArrowLeft,
  Clock,
  MemoryStick,
  CheckCircle2,
  XCircle,
} from "lucide-react";
import { cn } from "@/lib/utils";

interface SubmitResult {
  verdict: Verdict;
  message?: string;
  timeMs: number;
  memoryKb: number;
  passed: number;
  total: number;
  detail?: {
    failedCase?: number;
    input?: string;
    expected?: string;
    actual?: string;
  };
}

export default function ProblemDetail() {
  const { slug } = useParams<{ slug: string }>();
  const { user } = useAuth();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [languages, setLanguages] = useState<LanguageDef[]>([]);
  const [langId, setLangId] = useState("python");
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [polling, setPolling] = useState(false);
  const [pollCount, setPollCount] = useState(0);
  const [result, setResult] = useState<SubmitResult | null>(null);
  const [error, setError] = useState("");
  const codeByLang = useRef<Record<string, string>>({});
  const pollController = useRef<AbortController | null>(null);
  const submitAttempt = useRef(0);

  useEffect(() => {
    if (!slug) return;
    let active = true;
    setLoading(true);
    Promise.all([api.getProblem(slug), api.getLanguages()])
      .then(([pRes, lRes]) => {
        if (!active) return;
        setProblem(pRes.problem);
        setLanguages(lRes.languages);
        const first = lRes.languages[0];
        if (first) {
          setLangId(first.id);
          setCode(first.template);
          codeByLang.current[first.id] = first.template;
        } else {
          setLangId("");
          setCode("");
          setError("当前没有可用的编程语言，请联系管理员。");
        }
      })
      .catch((e) => { if (active) setError(e instanceof ApiError ? e.message : "加载失败"); })
      .finally(() => { if (active) setLoading(false); });
    return () => {
      active = false;
      pollController.current?.abort();
    };
  }, [slug]);

  // Persist code per-language, swap templates when switching.
  const onLangChange = (id: string) => {
    codeByLang.current[langId] = code;
    const next = codeByLang.current[id] ?? languages.find((l) => l.id === id)?.template ?? "";
    setLangId(id);
    setCode(next);
  };

  const submit = async () => {
    if (!problem || !user || !langId || submitting || polling) return;
    pollController.current?.abort();
    const controller = new AbortController();
    pollController.current = controller;
    const attempt = ++submitAttempt.current;
    setSubmitting(true);
    setPolling(false);
    setPollCount(0);
    setError("");
    setResult(null);
    try {
      // 1) enqueue
      const res = await api.submit(problem.id, langId, code);
      if (controller.signal.aborted || attempt !== submitAttempt.current) return;
      // 2) poll for the verdict
      setSubmitting(false);
      setPolling(true);
      const settled = await api.pollSubmission(res.submissionId, {
        intervalMs: 1200,
        timeoutMs: 60000,
        signal: controller.signal,
        onTick: (n) => { if (attempt === submitAttempt.current) setPollCount(n); },
      });
      if (controller.signal.aborted || attempt !== submitAttempt.current) return;
      setResult({
        verdict: settled.verdict,
        message: settled.message,
        timeMs: settled.timeMs,
        memoryKb: settled.memoryKb,
        passed: settled.passed,
        total: settled.total,
      });
      if (settled.verdict === "PENDING" || settled.verdict === "JUDGING") {
        setError("判题仍在进行，可前往提交记录查看最终结果。");
      }
    } catch (e) {
      if (!isAbortError(e) && attempt === submitAttempt.current) {
        setError(getApiErrorMessage(e, "提交失败"));
      }
    } finally {
      if (attempt === submitAttempt.current && !controller.signal.aborted) {
        setSubmitting(false);
        setPolling(false);
      }
    }
  };

  if (loading)
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );

  if (!problem)
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <p className="text-muted-foreground">{error || "题目不存在"}</p>
        <Link to="/" className="mt-4 inline-block text-sm text-foreground underline">
          返回题库
        </Link>
      </div>
    );

  const ac = result?.verdict === "AC";

  return (
    <div className="pilot-page">
      <Link to="/" className="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 返回题库
      </Link>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        {/* Left: description */}
        <div className="min-w-0 space-y-4">
          <Card>
            <CardHeader>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="break-words text-xl font-semibold tracking-tight">{problem.title}</h1>
                <span
                  className={cn(
                    "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium",
                    DIFFICULTY_CLASS[problem.difficulty],
                  )}
                >
                  {DIFFICULTY_LABEL[problem.difficulty]}
                </span>
                <span className="pilot-numeric flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1">
                    <Clock className="h-3.5 w-3.5" /> {problem.timeLimit}ms
                  </span>
                  <span className="flex items-center gap-1">
                    <MemoryStick className="h-3.5 w-3.5" /> {problem.memoryLimit}MB
                  </span>
                </span>
              </div>
              <div className="text-xs text-muted-foreground">
                创建者：{problem.createdBy == null ? "系统预置" : (problem.creatorUsername ?? "未知")}
                <span className="ml-3">创建于 {new Date(problem.createdAt).toLocaleString("zh-CN", { hour12: false })}</span>
              </div>
              {(problem.tags || []).length > 0 && (
                <div className="mt-1 flex flex-wrap gap-1">
                  {(problem.tags || []).map((t) => (
                    <span key={t} className="rounded bg-elevated px-1.5 py-0.5 text-xs text-subtle">
                      {t}
                    </span>
                  ))}
                </div>
              )}
            </CardHeader>
            <CardContent>
              <Markdown>{problem.description}</Markdown>
            </CardContent>
          </Card>

          {Array.isArray(problem.samples) && problem.samples.length > 0 && (
            <Card className="gap-3 border-0 bg-transparent shadow-none">
              <CardHeader>
                <CardTitle className="text-base">样例</CardTitle>
              </CardHeader>
              <CardContent className="min-w-0 space-y-4">
                {problem.samples.map((s, i) => (
                  <div key={i} className="grid gap-2 sm:grid-cols-2">
                    <div>
                      <p className="mb-1 text-xs font-medium text-muted-foreground">样例输入 {i + 1}</p>
                      <pre className="overflow-x-auto rounded-md bg-elevated p-3 font-mono text-sm tabular-nums text-foreground">
                        {s.input}
                      </pre>
                    </div>
                    <div>
                      <p className="mb-1 text-xs font-medium text-muted-foreground">样例输出 {i + 1}</p>
                      <pre className="overflow-x-auto rounded-md bg-elevated p-3 font-mono text-sm tabular-nums text-foreground">
                        {s.output}
                      </pre>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </div>

        {/* Right: editor + result */}
        <div className="min-w-0 space-y-4">
          <Card className="bg-surface">
            <CardHeader className="pb-3">
              <div className="flex flex-wrap items-center gap-3">
                <CardTitle className="text-base">代码编辑器</CardTitle>
                <div className="ml-auto w-44">
                  <Select value={langId || undefined} onValueChange={onLangChange} disabled={languages.length === 0}>
                    <SelectTrigger size="sm" aria-label="编程语言">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="graphite-theme dark">
                      {languages.map((l) => (
                        <SelectItem key={l.id} value={l.id}>
                          {l.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              <CodeEditor
                ariaLabel="源代码"
                appearance="graphite"
                value={code}
                language={langId}
                onChange={setCode}
              />

              {!user && (
                <p className="mt-3 text-sm text-warning">
                  请先{" "}
                  <Link to="/login" className="underline">
                    登录
                  </Link>{" "}
                  后再提交代码
                </p>
              )}

              <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button onClick={submit} disabled={submitting || polling || !user || !langId} className="gap-1.5">
                  {(submitting || polling) ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
                  {submitting ? "提交中..." : polling ? `评测中${pollCount > 0 ? ` (${pollCount})` : ""}` : "提交评测"}
                </Button>
                <Button
                  variant="outline"
                  onClick={() =>
                    setResult({
                      verdict: "PENDING",
                      message: "样例自测使用“提交评测”运行隐藏测试点。",
                      timeMs: 0,
                      memoryKb: 0,
                      passed: 0,
                      total: 0,
                    })
                  }
                  className="gap-1.5"
                  disabled
                  title="提示"
                >
                  <Play className="h-4 w-4" /> 运行样例
                </Button>
              </div>

              {error && <p className="mt-3 text-sm text-danger" role="alert">{error}</p>}
            </CardContent>
          </Card>

          {result && result.verdict !== "PENDING" && (
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  {ac ? (
                    <CheckCircle2 className="h-5 w-5 text-success" />
                  ) : (
                    <XCircle className="h-5 w-5 text-danger" />
                  )}
                  评测结果
                  <VerdictBadge verdict={result.verdict} className="ml-1" />
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex flex-wrap gap-4 text-sm">
                  <span className="text-subtle">
                    通过测试点：<span className="font-semibold text-foreground">{result.passed}/{result.total}</span>
                  </span>
                  <span className="flex items-center gap-1 text-subtle">
                    <Clock className="h-3.5 w-3.5" />
                    {result.timeMs}ms
                  </span>
                </div>
                {result.message && <p className="text-sm text-subtle">{result.message}</p>}

                {result.detail?.failedCase != null && (
                  <div className="space-y-2 rounded-md border bg-surface p-3">
                    <p className="text-xs font-medium text-muted-foreground">
                      第 {result.detail.failedCase} 个测试点
                    </p>
                    {result.detail.input != null && (
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">输入</p>
                        <pre className="mt-1 overflow-x-auto rounded bg-card p-2 text-xs">{result.detail.input}</pre>
                      </div>
                    )}
                    <div className="grid gap-2 sm:grid-cols-2">
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">期望输出</p>
                        <pre className="mt-1 overflow-x-auto rounded bg-card p-2 text-xs text-success">
                          {result.detail.expected}
                        </pre>
                      </div>
                      <div>
                        <p className="text-xs font-medium text-muted-foreground">你的输出</p>
                        <pre className="mt-1 overflow-x-auto rounded bg-card p-2 text-xs text-danger">
                          {result.detail.actual}
                        </pre>
                      </div>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
