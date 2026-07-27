import { useEffect, useState } from "react";
import { api, type UserListItem, ApiError } from "@/lib/api";
import { Card } from "@/components/ui/card";
import { Loader2, ShieldCheck, GraduationCap, User as UserIcon } from "lucide-react";
import { cn } from "@/lib/utils";

const ROLES: { key: "USER" | "TEACHER" | "ADMIN"; label: string; icon: typeof UserIcon; class: string }[] = [
  { key: "USER", label: "学生", icon: UserIcon, class: "bg-zinc-100 text-zinc-700" },
  { key: "TEACHER", label: "老师", icon: GraduationCap, class: "bg-blue-100 text-blue-700" },
  { key: "ADMIN", label: "管理员", icon: ShieldCheck, class: "bg-zinc-900 text-white" },
];

const ROLE_BADGE: Record<string, { label: string; class: string }> = {
  USER: { label: "学生", class: "bg-zinc-100 text-zinc-700" },
  TEACHER: { label: "老师", class: "bg-blue-100 text-blue-700" },
  ADMIN: { label: "管理员", class: "bg-zinc-900 text-white" },
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
    <div className="px-4 py-6 sm:px-6 lg:px-8">
      <div className="mb-4 flex items-center gap-2">
        <ShieldCheck className="h-6 w-6 text-zinc-700" />
        <h1 className="text-2xl font-bold">用户管理</h1>
      </div>

      {error && <p className="mb-3 text-sm text-red-600">{error}</p>}

      <Card className="overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-zinc-50 text-left text-xs uppercase text-zinc-500">
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
              <tr><td colSpan={5} className="px-4 py-12 text-center"><Loader2 className="mx-auto h-5 w-5 animate-spin text-zinc-400" /></td></tr>
            ) : users.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-12 text-center text-zinc-400">暂无用户</td></tr>
            ) : (
              users.map((u) => (
                <tr key={u.id} className="hover:bg-zinc-50">
                  <td className="px-4 py-3 text-zinc-400">{u.id}</td>
                  <td className="px-4 py-3 font-medium text-zinc-900">{u.username}</td>
                  <td className="px-4 py-3 text-zinc-600">{u.solvedCount}</td>
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
                            type="button"
                            disabled={updatingId === u.id || active}
                            onClick={() => changeRole(u.id, r.key)}
                            className={cn(
                              "inline-flex items-center gap-1 rounded-md border px-2 py-1 text-xs font-medium transition-colors disabled:opacity-50",
                              active ? r.class + " border-transparent" : "border-zinc-200 text-zinc-600 hover:bg-zinc-50",
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
      </Card>
    </div>
  );
}
