import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type DocSubmissionListItem } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Loader2, ClipboardCheck } from "lucide-react";
import { cn } from "@/lib/utils";

const STATUS_LABEL: Record<string, string> = { AUTO_CHECKED: "自动通过", NEEDS_REVIEW: "待复核", REVIEWED: "已复核" };
const STATUS_CLASS: Record<string, string> = { AUTO_CHECKED: "bg-green-100 text-green-700", NEEDS_REVIEW: "bg-yellow-100 text-yellow-700", REVIEWED: "bg-blue-100 text-blue-700" };

export default function OfficeDocReviewList() {
  const [items, setItems] = useState<DocSubmissionListItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api.listDocSubmissions({ pageSize: 50 }).then((d) => active && setItems(d.submissions))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  return (
    <div className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mb-4 flex items-center gap-2">
        <ClipboardCheck className="h-6 w-6 text-zinc-700" />
        <h1 className="text-2xl font-bold">文档提交复核</h1>
      </div>

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-left text-xs uppercase text-zinc-500">
            <tr>
              <th className="w-16 px-4 py-3 font-medium">#</th>
              <th className="px-4 py-3 font-medium">文件</th>
              <th className="w-20 px-4 py-3 font-medium">用户ID</th>
              <th className="w-24 px-4 py-3 font-medium">状态</th>
              <th className="w-16 px-4 py-3 font-medium">分数</th>
              <th className="w-28 px-4 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-zinc-400" /></td></tr>
            ) : items.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center text-zinc-400">暂无提交记录</td></tr>
            ) : (
              items.map((s) => (
                <tr key={s.id} className="hover:bg-zinc-50">
                  <td className="px-4 py-3 text-zinc-400">{s.id}</td>
                  <td className="px-4 py-3"><span className="line-clamp-1 max-w-xs">{s.studentDocName}</span><span className="text-xs text-zinc-400"> · 练习#{s.exerciseId}</span></td>
                  <td className="px-4 py-3 text-zinc-600">{s.userId}</td>
                  <td className="px-4 py-3"><span className={cn("rounded px-1.5 py-0.5 text-xs font-medium whitespace-nowrap", STATUS_CLASS[s.status])}>{STATUS_LABEL[s.status]}</span></td>
                  <td className="px-4 py-3 text-zinc-600">{s.score ?? "—"}</td>
                  <td className="px-4 py-3"><Button variant="ghost" size="sm" asChild><Link to={`/admin/office-doc/review/${s.id}`}>复核</Link></Button></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
