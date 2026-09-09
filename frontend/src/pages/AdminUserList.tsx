import { useEffect, useState } from "react";
import { api, type UserListItem, ApiError } from "@/lib/api";
import { AdminTable } from "@/components/admin/AdminTable";
import { Loader2, ShieldCheck, GraduationCap, User as UserIcon } from "lucide-react";
import { cn } from "@/lib/utils";

const ROLES: { key: "USER" | "TEACHER" | "ADMIN"; label: string; icon: typeof UserIcon; class: string }[] = [
  { key: "USER", label: "学生", icon: UserIcon, class: "bg-elevated text-secondary-foreground" },
  { key: "TEACHER", label: "老师", icon: GraduationCap, class: "bg-elevated text-secondary-foreground" },
  { key: "ADMIN", label: "管理员", icon: ShieldCheck, class: "bg-secondary text-foreground" },
];

const ROLE_BADGE: Record<string, { label: string; class: string }> = {
  USER: { label: "学生", class: "bg-elevated text-secondary-foreground" },
  TEACHER: { label: "老师", class: "bg-elevated text-secondary-foreground" },
  ADMIN: { label: "管理员", class: "bg-secondary text-foreground" },
};

export default function AdminUserList() {
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    api.listUsers({ pageSize: 100 }).then((d) => active && setUsers(d.users))
      .catch((e: Error) => active && setError(e.message))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, []);

  async function changeRole(id: number, role: "USER" | "TEACHER" | "ADMIN") {
    setUpdatingId(id);
    setError(null);
    try {
      const { user } = await api.updateUserRole(id, role);
      setUsers((prev) => prev.map((u) => (u.id === id ? user : u)));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "修改失败");
    } finally {
      setUpdatingId(null);
    }
  }

  return (
    <div className="admin-page">
      <header className="management-hero"><div className="flex items-start justify-between gap-3"><div><p className="management-kicker">Access control</p><h1 className="management-title">用户与角色</h1><p className="management-summary">在一个可扫描的权限工作区中查看账号与角色。角色变更仍使用既有授权 API。</p></div><ShieldCheck className="mt-1 h-6 w-6 text-muted-foreground" /></div><div className="management-deck"><div className="management-deck-item"><p className="management-deck-label">当前列表</p><p className="management-deck-value">{loading ? "加载中" : `${users.length} 个账号`}</p></div><div className="management-deck-item"><p className="management-deck-label">管理员</p><p className="management-deck-value">{loading ? "—" : users.filter((item) => item.role === "ADMIN").length}</p></div><div className="management-deck-item"><p className="management-deck-label">老师</p><p className="management-deck-value">{loading ? "—" : users.filter((item) => item.role === "TEACHER").length}</p></div></div></header>

      {error && <p className="mb-3 text-sm text-danger">{error}</p>}

      <section className="management-panel mt-6"><div className="management-toolbar"><div><h2 className="management-panel-title">账号目录</h2><p className="management-panel-description">选择一个目标角色即会触发现有的角色更新流程。</p></div></div><AdminTable label="用户与角色">
        <table className="w-full min-w-[720px] text-sm">
          <thead className="bg-surface text-left text-xs uppercase text-muted-foreground">
            <tr>
              <th className="w-16 px-4 py-3 font-medium">#</th>
              <th className="px-4 py-3 font-medium">用户名</th>
              <th className="w-24 px-4 py-3 font-medium">已解决</th>
              <th className="w-24 px-4 py-3 font-medium">当前角色</th>
              <th className="px-4 py-3 font-medium">设置角色</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {loading ? (
              <tr><td colSpan={5} className="px-4 py-12 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-muted-foreground" /></td></tr>
            ) : users.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-12 text-center text-muted-foreground">暂无用户</td></tr>
            ) : (
              users.map((u) => (
                <tr key={u.id} className="hover:bg-surface">
                  <td className="px-4 py-3 text-muted-foreground">{u.id}</td>
                  <td className="px-4 py-3 font-medium text-foreground">{u.username}</td>
                  <td className="px-4 py-3 text-secondary-foreground">{u.solvedCount}</td>
                  <td className="px-4 py-3">
                    <span className={cn("rounded px-1.5 py-0.5 text-xs font-medium whitespace-nowrap", ROLE_BADGE[u.role].class)}>
                      {ROLE_BADGE[u.role].label}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex gap-1">
                      {ROLES.map((r) => {
                        const Icon = r.icon;
                        const active = u.role === r.key;
                        return (
                          <button
                            key={r.key}
                            aria-label={`将 ${u.username} 设为${r.label}`}
                            aria-pressed={active}
                            type="button"
                            disabled={updatingId === u.id || active}
                            onClick={() => changeRole(u.id, r.key)}
                            className={cn(
                              "inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-medium transition-colors disabled:opacity-50",
                              active ? "bg-brand/10 text-foreground border-brand/30" : "border-border text-secondary-foreground hover:bg-surface",
                            )}
                          >
                            <Icon className="h-3 w-3" />
                            {r.label}
                          </button>
                        );
                      })}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </AdminTable></section>
    </div>
  );
}
