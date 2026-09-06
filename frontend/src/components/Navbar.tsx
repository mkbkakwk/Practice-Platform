import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Code2, ListOrdered, Trophy, LogOut, UserCircle, FileCode2, Settings, Briefcase, ClipboardCheck, Users, CalendarDays, Menu, Activity } from "lucide-react";
import { cn } from "@/lib/utils";

const deployEnvironment = import.meta.env.VITE_DEPLOY_ENV as string | undefined;
const buildSha = import.meta.env.VITE_BUILD_SHA as string | undefined;
const showStagingBadge = deployEnvironment === "staging";

export function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const links = [
    { to: "/", label: "题库", icon: ListOrdered, match: (path: string) => path === "/" || path.startsWith("/problem") },
    { to: "/office", label: "Office", icon: Briefcase, match: (path: string) => path.startsWith("/office") },
    { to: "/contests", label: "比赛", icon: CalendarDays, match: (path: string) => path.startsWith("/contests") || path.startsWith("/admin/contests") },
    { to: "/submissions", label: "提交记录", icon: FileCode2, match: (path: string) => path.startsWith("/submissions") },
    { to: "/leaderboard", label: "排行榜", icon: Trophy, match: (path: string) => path.startsWith("/leaderboard") },
  ];

  if (user?.role === "TEACHER" || user?.role === "ADMIN") {
    links.push({ to: "/admin/problems", label: "内容管理", icon: Settings, match: (path: string) => path.startsWith("/admin/problems") || path.startsWith("/admin/office") });
    links.push({ to: "/admin/office-doc/review-list", label: "复核", icon: ClipboardCheck, match: (path: string) => path.startsWith("/admin/office-doc/review") });
  }
  if (user?.role === "ADMIN") {
    links.push({ to: "/admin/users", label: "用户", icon: Users, match: (path: string) => path.startsWith("/admin/users") });
    links.push({ to: "/admin/system-status", label: "系统状态", icon: Activity, match: (path: string) => path.startsWith("/admin/system-status") });
  }

  return (
    <header className="graphite-theme dark sticky top-0 z-40 w-full border-b bg-background/95 text-foreground">
      <div className="mx-auto flex h-14 max-w-[1440px] items-center gap-2 px-4 font-sans sm:px-6 lg:px-8">
        <Link to="/" aria-label="Algorithm OJ 首页" className="mr-2 flex shrink-0 items-center gap-2 rounded-sm text-sm font-semibold tracking-tight">
          <span className="flex h-7 w-7 items-center justify-center rounded-md border border-border bg-elevated text-foreground"><Code2 className="h-4 w-4" /></span>
          <span className="hidden sm:inline">Algorithm OJ</span>
        </Link>
        {showStagingBadge && (
          <span data-testid="staging-build" className="rounded border border-warning/25 bg-warning/10 px-2 py-1 font-mono text-xs text-warning">
            <span className="sm:hidden">STG · {(buildSha || "unknown").slice(0, 7)}</span>
            <span className="hidden sm:inline">STAGING · {buildSha || "unknown"}</span>
          </span>
        )}
        <nav aria-label="主导航" className="hidden items-center gap-1 xl:flex">
          {links.map((link) => {
            const Icon = link.icon;
            return <Link key={link.to} to={link.to} aria-current={link.match(location.pathname) ? "page" : undefined} className={cn("flex items-center gap-1.5 rounded-md border px-2 py-1.5 text-[13px] font-medium transition-colors duration-150", link.match(location.pathname) ? "border-border bg-elevated text-foreground" : "border-transparent text-muted-foreground hover:bg-surface hover:text-foreground")}><Icon className="h-4 w-4" />{link.label}</Link>;
          })}
        </nav>
        <div className="ml-auto hidden items-center gap-2 xl:flex">
          {user ? <>
            <span className="hidden items-center gap-1.5 max-w-48 text-sm text-subtle sm:flex"><UserCircle className="h-4 w-4" /><span className="max-w-28 truncate" title={user.username}>{user.username}</span>{user.role === "ADMIN" && <span className="rounded border border-border bg-elevated px-1.5 py-0.5 text-[10px] font-semibold text-subtle">管理员</span>}{user.role === "TEACHER" && <span className="rounded border border-border bg-elevated px-1.5 py-0.5 text-[10px] font-semibold text-subtle">老师</span>}</span>
            <Button variant="ghost" size="sm" onClick={() => { logout(); navigate("/"); }}><LogOut className="mr-1 h-4 w-4" />退出</Button>
          </> : <><Button variant="ghost" size="sm" onClick={() => navigate("/login")}>登录</Button><Button size="sm" onClick={() => navigate("/register")}>注册</Button></>}
        </div>
        <Sheet>
          <SheetTrigger asChild>
            <Button className="ml-auto xl:hidden" variant="ghost" size="icon" aria-label="打开导航菜单">
              <Menu className="h-5 w-5" />
            </Button>
          </SheetTrigger>
          <SheetContent className="graphite-theme dark w-[min(22rem,90vw)] border-border text-foreground" aria-describedby="mobile-navigation-description">
            <SheetHeader>
              <SheetTitle>导航</SheetTitle>
              <SheetDescription id="mobile-navigation-description">
                {user ? `${user.username} · ${user.role === "ADMIN" ? "管理员" : user.role === "TEACHER" ? "老师" : "学生"}` : "浏览题库、Office 与比赛"}
              </SheetDescription>
            </SheetHeader>
            <nav className="grid gap-1 px-4" aria-label="移动端导航">
              {links.map((link) => {
                const Icon = link.icon;
                return (
                  <SheetClose asChild key={link.to}>
                    <Link
                      to={link.to}
                      aria-current={link.match(location.pathname) ? "page" : undefined}
                      className={cn(
                        "flex items-center gap-3 rounded-md border border-transparent px-3 py-2.5 text-sm font-medium transition-colors duration-150",
                        link.match(location.pathname) ? "border-border bg-elevated text-foreground" : "text-muted-foreground hover:bg-surface hover:text-foreground",
                      )}
                    >
                      <Icon className="h-4 w-4" />
                      {link.label}
                    </Link>
                  </SheetClose>
                );
              })}
            </nav>
            <SheetFooter>
              {user ? (
                <SheetClose asChild>
                  <Button variant="outline" onClick={() => { logout(); navigate("/"); }}>
                    <LogOut className="mr-2 h-4 w-4" />退出
                  </Button>
                </SheetClose>
              ) : (
                <div className="grid grid-cols-2 gap-2">
                  <SheetClose asChild><Button variant="outline" onClick={() => navigate("/login")}>登录</Button></SheetClose>
                  <SheetClose asChild><Button onClick={() => navigate("/register")}>注册</Button></SheetClose>
                </div>
              )}
            </SheetFooter>
          </SheetContent>
        </Sheet>
      </div>
    </header>
  );
}
