import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type OfficeQuestionListItem } from "@/lib/api";
import { DIFFICULTY_CLASS, DIFFICULTY_LABEL } from "@/lib/verdict";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, Plus, Pencil } from "lucide-react";
import { cn } from "@/lib/utils";

const APP_LABEL: Record<string, string> = { WORD: "Word", EXCEL: "Excel", PPT: "PPT" };
const APP_CLASS: Record<string, string> = {
  WORD: "bg-blue-100 text-blue-700 border-blue-200",
  EXCEL: "bg-green-100 text-green-700 border-green-200",
  PPT: "bg-orange-100 text-orange-700 border-orange-200",
};
const QTYPE_LABEL: Record<string, string> = {
  SINGLE_CHOICE: "单选",
  MULTI_CHOICE: "多选",
  TRUE_FALSE: "判断",
};

export default function OfficeAdminList() {
  const [questions, setQuestions] = useState<OfficeQuestionListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api
      .listOfficeQuestions({ page, pageSize })
      .then((data) => {
        if (!active) return;
        setQuestions(data.questions);
        setTotal(data.total);
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [page, pageSize]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Office 题库管理</h1>
        <Button size="sm" asChild>
          <Link to="/admin/office/new">
            <Plus className="mr-1 h-4 w-4" /> 新建题目
          </Link>
        </Button>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-left text-xs uppercase text-zinc-500">
            <tr>
              <th className="w-16 px-4 py-3 font-medium">#</th>
              <th className="px-4 py-3 font-medium">题目</th>
              <th className="w-20 px-4 py-3 font-medium">应用</th>
              <th className="hidden w-20 px-4 py-3 font-medium sm:table-cell">类型</th>
              <th className="w-20 px-4 py-3 font-medium">难度</th>
              <th className="w-16 px-4 py-3 font-medium">可见</th>
              <th className="w-20 px-4 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-zinc-400">
                  <Loader2 className="mx-auto h-5 w-5 animate-spin" />
                </td>
              </tr>
            ) : questions.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-zinc-400">
                  暂无题目，点击右上角“新建题目”添加
                </td>
              </tr>
            ) : (
              questions.map((q) => (
                <tr key={q.id} className="transition-colors hover:bg-zinc-50">
                  <td className="px-4 py-3 text-zinc-400">{q.id}</td>
                  <td className="px-4 py-3">
                    <Link
                      to={`/office/${q.id}`}
                      className="line-clamp-1 max-w-md font-medium text-zinc-900 hover:underline"
                    >
                      {q.content}
                    </Link>
                    <span className="ml-2 text-xs text-zinc-400">{q.category}</span>
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
                  <td className="hidden px-4 py-3 text-zinc-600 sm:table-cell">{QTYPE_LABEL[q.questionType]}</td>
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
                  <td className="px-4 py-3">
                    <span
                      className={cn(
                        "inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium whitespace-nowrap",
                        q.visible
                          ? "bg-green-100 text-green-700"
                          : "bg-zinc-100 text-zinc-500",
                      )}
                    >
                      {q.visible ? "可见" : "隐藏"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <Button variant="ghost" size="sm" asChild>
                      <Link to={`/admin/office/${q.id}/edit`}>
                        <Pencil className="mr-1 h-3.5 w-3.5" /> 编辑
                      </Link>
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </Card>

      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((p) => Math.max(1, p - 1))}>
            上一页
          </Button>
          <span className="text-sm text-zinc-600">
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
    </div>
  );
}
