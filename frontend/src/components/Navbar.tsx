import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Code2, ListOrdered, Trophy, LogOut, UserCircle, FileCode2, Settings, Briefcase, ClipboardCheck, Users } from "lucide-react";
import { cn } from "@/lib/utils";

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const links = [
    { to: "/", label: "题库", icon: ListOrdered, match: (p: string) => p === "/" || p.startsWith("/problem") },
    { to: "/office", label: "Office", icon: Briefcase, match: (p: string) => p.startsWith("/office") },
    { to: "/submissions", label: "提交记录", icon: FileCode2, match: (p: string) => p.startsWith("/submissions") },
    { to: "/leaderboard", label: "排行榜", icon: Trophy, match: (p: string) => p.startsWith("/leaderboard") },
  ];

  // Teachers see their review queue entry.
  if (user?.role === "TEACHER" || user?.role === "ADMIN") {
    links.push({ to: "/admin/office-doc/review-list", label: "复核", icon: ClipboardCheck, match: (p: string) => p.startsWith("/admin/office-doc/review") });
  }
  // Admin-only: problem management + user management.
  if (user?.role === "ADMIN") {
    links.push({ to: "/admin/problems", label: "管理", icon: Settings, match: (p: string) => p.startsWith("/admin/problems") || p.startsWith("/admin/office") });
    links.push({ to: "/admin/users", label: "用户", icon: Users, match: (p: string) => p.startsWith("/admin/users") });
  }

  return (
    <header className="sticky top-0 z-40 w-full border-b bg-white/80 backdrop-blur">
      <div className="flex h-14 items-center gap-2 px-4 sm:px-6 lg:px-8">
        <Link to="/" className="mr-2 flex items-center gap-2 font-bold">
          <span className="flex h-7 w-7 items-center justify-center rounded-md bg-zinc-900 text-white">
            <Code2 className="h-4 w-4" />
          </span>
          <span className="hidden sm:inline">Algorithm OJ</span>
        </Link>

        <nav className="flex items-center gap-1">
          {links.map((l) => {
            const active = l.match(location.pathname);
            const Icon = l.icon;
            return (
              <Link
                key={l.to}
                to={l.to}
                className={cn(
                  "flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors",
                  active ? "bg-zinc-900 text-white" : "text-zinc-600 hover:bg-zinc-100",
                )}
              >
                <Icon className="h-4 w-4" />
                {l.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-2">
          {user ? (
            <>
              <span className="hidden items-center gap-1.5 text-sm text-zinc-600 sm:flex">
                <UserCircle className="h-4 w-4" />
                {user.username}
                {user.role === "ADMIN" && (
                  <span className="rounded bg-zinc-900 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                    管理员
                  </span>
                )}
                {user.role === "TEACHER" && (
                  <span className="rounded bg-blue-600 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                    老师
                  </span>
                )}
              </span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  logout();
                  navigate("/");
                }}
              >
                <LogOut className="mr-1 h-4 w-4" /> 退出
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" onClick={() => navigate("/login")}>
                登录
              </Button>
              <Button size="sm" onClick={() => navigate("/register")}>
                注册
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
