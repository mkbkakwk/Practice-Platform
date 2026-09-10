import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import ProblemForm from "@/pages/ProblemForm";
import { api, type ProblemDetail, ApiError } from "@/lib/api";

/**
 * 编辑模式包装：从 URL 取 slug，拉取题目详情（admin 会拿到 testCases），
 * 再交给 ProblemForm 渲染表单。
 */
export default function ProblemEditPage() {
  const { slug } = useParams<{ slug: string }>();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!slug) return;
    api
      .getProblem(slug)
      .then((res) => setProblem(res.problem))
      .catch((e) => setError(e instanceof ApiError ? e.message : "加载失败"))
      .finally(() => setLoading(false));
  }, [slug]);

  if (loading) {
    return (
      <div className="flex h-[50vh] items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-zinc-400" />
      </div>
    );
  }

  if (error || !problem) {
    return (
      <div className="px-4 py-6">
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error || "题目不存在"}
        </div>
      </div>
    );
  }

  return <ProblemForm mode="edit" initial={problem} />;
}
