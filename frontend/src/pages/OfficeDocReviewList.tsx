import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, type DocSubmissionListItem } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { AdminTable } from "@/components/admin/AdminTable";
import { Loader2, ClipboardCheck } from "lucide-react";
import { cn } from "@/lib/utils";
import { ADMIN_DOC_STATUS_CLASS } from "@/components/admin/presentation";

const STATUS_LABEL: Record<string, string> = { PENDING: "等待判题", JUDGING: "判题中", COMPLETED: "自动判题完成", FAILED: "判题失败", AUTO_CHECKED: "自动通过", NEEDS_REVIEW: "待复核", REVIEWED: "已复核" };

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
    <div className="admin-page">
      <div className="mb-4 flex items-center gap-2">
        <ClipboardCheck className="h-6 w-6 text-secondary-foreground" />
        <h1 className="text-2xl font-semibold tracking-tight">文档提交复核</h1>
      </div>

      <AdminTable label="文档提交复核">
        <table className="w-full min-w-[760px] text-sm">
          <thead className="bg-surface text-left text-xs uppercase text-muted-foreground">
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
              <tr><td colSpan={6} className="px-4 py-12 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-muted-foreground" /></td></tr>
            ) : items.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-12 text-center text-muted-foreground">暂无提交记录</td></tr>
            ) : (
              items.map((s) => (
                <tr key={s.id} className="hover:bg-surface">
                  <td className="px-4 py-3 text-muted-foreground">{s.id}</td>
                  <td className="px-4 py-3"><span className="line-clamp-1 max-w-xs">{s.studentDocName}</span><span className="text-xs text-muted-foreground"> · 练习#{s.exerciseId}</span></td>
                  <td className="px-4 py-3 text-secondary-foreground">{s.userId}</td>
                  <td className="px-4 py-3"><span className={cn("rounded border px-2 py-0.5 text-xs font-medium whitespace-nowrap", ADMIN_DOC_STATUS_CLASS[s.status])}>{STATUS_LABEL[s.status]}</span></td>
                  <td className="px-4 py-3 text-secondary-foreground">{s.score ?? "—"}</td>
                  <td className="px-4 py-3"><Button variant="ghost" size="sm" asChild><Link to={`/admin/office-doc/review/${s.id}`}>复核</Link></Button></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </AdminTable>
    </div>
  );
}
