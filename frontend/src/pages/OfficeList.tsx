import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type OfficeQuestionListItem, type OfficeAppType } from "@/lib/api";
import { DIFFICULTY_CLASS, DIFFICULTY_LABEL } from "@/lib/verdict";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, FileText, Sheet, Presentation } from "lucide-react";
import { cn } from "@/lib/utils";

const APP_TABS: { key: string; label: string; icon: typeof FileText }[] = [
  { key: "", label: "全部", icon: FileText },
  { key: "WORD", label: "Word", icon: FileText },
  { key: "EXCEL", label: "Excel", icon: Sheet },
  { key: "PPT", label: "PPT", icon: Presentation },
];

const APP_LABEL: Record<OfficeAppType, string> = { WORD: "Word", EXCEL: "Excel", PPT: "PPT" };
const APP_CLASS: Record<OfficeAppType, string> = {
  WORD: "bg-info/10 text-info border-info/25",
  EXCEL: "bg-success/10 text-success border-success/25",
  PPT: "bg-warning/10 text-warning border-warning/25",
};

const QTYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: "单选",
  MULTI_CHOICE: "多选",
  TRUE_FALSE: "判断",
};

export default function OfficeList() {
  const { user } = useAuth();
  const [questions, setQuestions] = useState<OfficeQuestionListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [appType, setAppType] = useState<string>("");
  const [difficulty, setDifficulty] = useState<string>("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api
      .listOfficeQuestions({ page, pageSize, appType: appType || undefined, difficulty: difficulty || undefined })
      .then((data) => {
        if (!active) return;
        setQuestions(data.questions);
        setTotal(data.total);
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [page, pageSize, appType, difficulty]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const diffs = [
    { key: "", label: "全部" },
    { key: "EASY", label: "简单" },
    { key: "MEDIUM", label: "中等" },
    { key: "HARD", label: "困难" },
  ];

  return (
    <div className="pilot-page">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Office 操作练习</h1>
          <p className="mt-1 text-sm text-muted-foreground">Word / Excel / PowerPoint 操作题，答题即时判分</p>
        </div>
        <span className="text-sm text-muted-foreground">共 {total} 题</span>
      </div>

      <div className="mb-4 flex justify-end">
        <Button variant="outline" size="sm" asChild>
          <Link to="/office/docs">排版练习（文档上传）→</Link>
        </Button>
      </div>

      {/* App type tabs */}
      <div className="mb-3 flex flex-wrap items-center gap-2">
        {APP_TABS.map((t) => {
          const Icon = t.icon;
          return (
            <Button
              key={t.key}
              size="sm"
              variant={appType === t.key ? "default" : "outline"}
              onClick={() => {
                setAppType(t.key);
                setPage(1);
              }}
            >
              <Icon className="mr-1 h-4 w-4" />
              {t.label}
            </Button>
          );
        })}
      </div>

      {/* Difficulty filter */}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-sm text-muted-foreground">难度：</span>
        {diffs.map((d) => (
          <Button
            key={d.key}
            size="sm"
            variant={difficulty === d.key ? "default" : "outline"}
            aria-pressed={difficulty === d.key}
            onClick={() => {
              setDifficulty(d.key);
              setPage(1);
            }}
          >
            {d.label}
          </Button>
        ))}
      </div>

      <Card className="gap-0 overflow-hidden py-0">
        <div className="overflow-x-auto" role="region" aria-label="Office 题库表格" tabIndex={0}>
        <table className="w-full min-w-[480px] text-sm">
          <caption className="sr-only">Office 题库</caption>
          <thead className="bg-surface text-left text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="w-16 px-4 py-3 font-medium">#</th>
              <th scope="col" className="px-4 py-3 font-medium">题目</th>
              <th scope="col" className="w-20 px-4 py-3 font-medium">应用</th>
              <th scope="col" className="hidden w-24 px-4 py-3 font-medium sm:table-cell">类型</th>
              <th scope="col" className="w-20 px-4 py-3 font-medium">难度</th>
              <th scope="col" className="hidden w-24 px-4 py-3 font-medium sm:table-cell">分类</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                  <span role="status" aria-label="加载Office 题库"><Loader2 aria-hidden="true" className="mx-auto h-5 w-5 animate-spin" /><span className="sr-only">加载中</span></span>
                </td>
              </tr>
            ) : questions.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">
                  暂无题目
                </td>
              </tr>
            ) : (
              questions.map((q) => (
                <tr key={q.id} className="transition-colors duration-150 hover:bg-elevated">
                  <td className="pilot-numeric px-4 py-3 text-muted-foreground">{q.id}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/office/${q.id}`}
                      className="line-clamp-1 font-medium text-foreground hover:underline"
                    >
                      {q.content}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={cn(
                        "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
                        APP_CLASS[q.appType],
                      )}
                    >
                      {APP_LABEL[q.appType]}
                    </span>
                  </td>
                  <td className="hidden px-4 py-3 text-subtle sm:table-cell">{QTYPE_LABEL[q.questionType]}</td>
                  <td className="px-4 py-3">
                    <span
                      className={cn(
                        "inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
                        DIFFICULTY_CLASS[q.difficulty],
                      )}
                    >
                      {DIFFICULTY_LABEL[q.difficulty]}
                    </span>
                  </td>
                  <td className="hidden px-4 py-3 text-muted-foreground sm:table-cell">{q.category}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        </div>
      </Card>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
            上一页
          </Button>
          <span className="text-sm text-subtle">
            {page} / {totalPages}
          </span>
          <Button
            size="sm"
            variant="outline"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </Button>
        </div>
      )}

      {user?.role === "ADMIN" && (
        <div className="mt-6 flex justify-end">
          <Button variant="outline" size="sm" asChild>
            <Link to="/admin/office">管理 Office 题库</Link>
          </Button>
        </div>
      )}
    </div>
  );
}
